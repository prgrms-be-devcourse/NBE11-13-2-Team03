# Gudit 성능테스트 실행 순서

## 1. 전체 흐름

```text
경로 변수 준비
→ 성능테스트 컨테이너 실행
→ 테스트 데이터 초기화
→ Spring performance 프로필 실행
→ preflight
→ 동시성 테스트
→ 테스트 데이터 재초기화
→ Spring 재실행
→ preflight
→ 부하테스트
→ 결과 확인
```

구매 및 취소 테스트는 DB 상태를 변경하므로 동시성 테스트와 부하테스트 사이에 반드시 데이터를 다시 적재한다.

## 2. 프로젝트 루트로 이동

PowerShell을 열고 Spring 프로젝트 루트로 이동한다.

```powershell
Set-Location "C:\Users\a\Desktop\강좌\project\Goods Shop\Gudit"
```

확인:

```powershell
Get-Location
```

## 3. 성능테스트 경로 변수 준비

새 PowerShell 터미널을 열 때마다 한 번 실행한다.

권장 폴더 구조라면:

```powershell
. ".\performance-tests\scripts\set-performance-paths.ps1"
```

현재 중첩 폴더 구조를 유지한다면:

```powershell
. ".\src\performance-tests\performance-tests\scripts\set-performance-paths.ps1"
```

맨 앞의 점과 공백은 dot-sourcing 문법이다. `&`로 실행하면 스크립트에서 만든 변수가 현재 터미널에 유지되지 않는다.

설정 결과 확인:

```powershell
$performancePaths
```

사용 가능한 주요 변수:

```text
$performanceRoot
$prepareDbPath
$prepareDbScript
$preflightPath
$concurrencyRunnerPath
$loadRunnerPath
$generatedDataPath
$performancePaths
```

## 4. 성능테스트 컨테이너 실행

```powershell
docker compose `
  -f docker-compose.performance.yml `
  up -d
```

상태 확인:

```powershell
docker compose `
  -f docker-compose.performance.yml `
  ps
```

다음 컨테이너가 `healthy` 상태여야 한다.

```text
gudit-performance-postgres
gudit-performance-redis
```

연결 정보:

```text
PostgreSQL: localhost:5433/gudit
Redis:      localhost:6380
```

## 5. 테스트 데이터 초기화

Spring이 실행 중이라면 먼저 종료한다.

```powershell
& $prepareDbScript.FullName `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres"
```

성공 시 마지막에 다음과 비슷한 결과가 출력된다.

```text
COMMIT
Performance-test data loaded into database 'gudit'
```

데이터 수 확인:

```powershell
docker exec gudit-performance-postgres `
  psql -U postgres -d gudit `
  -c "SELECT
        (SELECT COUNT(*) FROM users) AS users,
        (SELECT COUNT(*) FROM goods) AS goods,
        (SELECT COUNT(*) FROM goods_sales) AS sales,
        (SELECT COUNT(*) FROM purchases) AS purchases,
        (SELECT COUNT(*) FROM payments) AS payments;"
```

예상 수량:

| 데이터 | 수량 |
|---|---:|
| 사용자 | 1,002 |
| 상품 | 104 |
| 판매 | 104 |
| 구매 | 1 |
| 결제 | 1 |

## 6. Spring 성능테스트 설정 실행

IntelliJ에서 다음 Run Configuration을 실행한다.

```text
GuditApplication - Performance
```

확인 항목:

```text
활성 프로필: performance
PostgreSQL:   localhost:5433/gudit
Redis:        localhost:6380
Spring:       localhost:8080
```

Hibernate 설정은 적재한 데이터를 유지할 수 있도록 `update`를 사용한다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

`create` 또는 `create-drop`을 사용하면 Spring 재시작 시 시드 데이터가 삭제될 수 있다.

## 7. preflight 실행

본 테스트 전에 반드시 실행한다.

```powershell
k6 run `
  -e "BASE_URL=http://localhost:8080" `
  $preflightPath
```

통과 조건:

```text
checks_succeeded: 100%
checks_failed:    0
http_req_failed:  0%
```

preflight가 실패하면 부하를 실행하지 않고 다음 항목을 확인한다.

- Spring이 실행 중인지
- 활성 프로필이 `performance`인지
- datasource가 `5433`을 사용하는지
- Redis가 `6380`을 사용하는지
- 테스트 JWT 키가 JSON의 키와 일치하는지
- 시드 데이터가 정상 적재됐는지

## 8. 동시성 테스트 모니터링

별도 PowerShell 터미널을 열고 프로젝트 루트로 이동한 후 경로 스크립트를 다시 dot-source한다.

```powershell
Set-Location "C:\Users\a\Desktop\강좌\project\Goods Shop\Gudit"
. ".\performance-tests\scripts\set-performance-paths.ps1"
```

현재 중첩 구조라면 실제 중첩 경로를 사용한다.

모니터 경로 준비:

```powershell
$monitorPath = Join-Path `
  $performanceRoot `
  "scripts\monitor-load.ps1"
```

모니터 실행:

```powershell
& $monitorPath `
  -DatabaseContainer "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres" `
  -DurationSeconds 600 `
  -IntervalSeconds 5
```

## 9. 동시성 테스트 실행

첫 번째 PowerShell 터미널에서 실행한다.

```powershell
& $concurrencyRunnerPath `
  -BaseUrl "http://localhost:8080"
```

내부 실행 순서:

```text
preflight
→ 재고 100개에 구매 요청 1,000건
→ 단일 판매 행에 구매 요청 1,000건
→ 판매 100건으로 요청 분산
→ 동일 사용자 중복 구매 50건
→ 동일 구매 동시 취소 50건
```

`04-duplicate-purchase-race`와 `05-cancel-race`가 실패하면 스크립트 문제보다 서비스의 동시성 정합성 문제일 가능성이 있다.

결과 위치:

```text
performance-tests/results/concurrency-실행시간/
```

## 10. 부하테스트 전 데이터 재초기화

동시성 테스트가 재고 및 구매 데이터를 변경했으므로 다음 순서를 반드시 수행한다.

```text
Spring 종료
→ 테스트 데이터 초기화
→ Spring performance 재실행
→ preflight 재실행
```

데이터 초기화:

```powershell
& $prepareDbScript.FullName `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres"
```

Spring을 재실행한 후:

```powershell
k6 run `
  -e "BASE_URL=http://localhost:8080" `
  $preflightPath
```

## 11. 부하테스트 모니터링

별도 터미널에서 실행한다.

```powershell
& $monitorPath `
  -DatabaseContainer "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres" `
  -DurationSeconds 1800 `
  -IntervalSeconds 5
```

IntelliJ Profiler에서도 다음 항목을 함께 확인한다.

- JVM CPU
- Heap 사용량
- GC 횟수 및 pause
- Thread 수
- HikariCP 연결 수

## 12. 기본 부하테스트 실행

```powershell
& $loadRunnerPath `
  -BaseUrl "http://localhost:8080"
```

내부 실행 순서:

```text
preflight
→ 워밍업 10 RPS
→ 기준 부하 50 RPS
→ 스트레스 최대 500 RPS
→ 스파이크 50 RPS에서 500 RPS
→ 조회 부하와 구매 1,000건 혼합
```

결과 위치:

```text
performance-tests/results/load-실행시간/
```

주요 결과 파일:

```text
00-warmup.summary.json
01-baseline-load.summary.json
02-stress-load.summary.json
03-spike-load.summary.json
05-mixed-purchase-peak.summary.json
comparison.csv
```

## 13. 소크 테스트 포함 실행

30분 장기 테스트까지 포함하려면 데이터를 초기화한 상태에서 처음부터 다음 명령을 실행한다.

```powershell
& $loadRunnerPath `
  -BaseUrl "http://localhost:8080" `
  -IncludeSoak
```

실행 순서:

```text
워밍업
→ 기준 부하
→ 스트레스
→ 스파이크
→ 소크 30분
→ 혼합 구매
```

장시간 테스트에서 확인할 항목:

- Heap이 지속적으로 증가하는지
- Full GC가 반복되는지
- Thread 수가 계속 증가하는지
- DB 연결 수가 정상 수준으로 회복되는지
- 테스트 후반 p95가 초반보다 느려지는지

## 14. 전체 실행 순서 요약

### 동시성 테스트

```text
경로 변수 설정
→ 성능테스트 Compose 실행
→ Spring 종료
→ DB 초기화
→ Spring performance 실행
→ preflight
→ 모니터링
→ 동시성 테스트
→ 결과 확인
```

### 부하테스트

```text
Spring 종료
→ DB 재초기화
→ Spring performance 재실행
→ preflight
→ 모니터링
→ 기본 부하테스트 또는 소크 포함 부하테스트
→ comparison.csv 확인
```

## 15. 핵심 규칙

1. 새 PowerShell 터미널을 열 때마다 `set-performance-paths.ps1`을 dot-source한다.
2. 본 테스트 전에는 항상 preflight를 실행한다.
3. 동시성 테스트 후 부하테스트 전에 반드시 DB를 재초기화한다.
4. `prepare-db.ps1` 대상 컨테이너는 `gudit-performance-postgres`로 명시한다.
5. Spring은 `performance` 프로필과 테스트 JWT 키로 실행한다.
6. `ddl-auto`는 `update`를 사용해 적재 데이터를 유지한다.
7. k6 결과뿐 아니라 JVM과 PostgreSQL 지표를 함께 기록한다.
