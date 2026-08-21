# Gudit 성능테스트 환경 구성 시행착오 및 해결 기록

## 1. 문서 목적

Gudit 프로젝트의 k6 동시성·부하테스트 환경을 준비하면서 발생한 오류와 해결 과정을 기록한다. 동일한 환경을 다시 구성하거나 다른 팀원이 테스트를 실행할 때 같은 문제를 반복하지 않는 것을 목적으로 한다.

## 2. 최종 환경 구조

개발 환경과 성능테스트 환경은 컨테이너 수준에서 분리한다.

```text
개발 환경
├─ PostgreSQL: localhost:5432
└─ Redis:      localhost:6379

성능테스트 환경
├─ PostgreSQL: localhost:5433
│  └─ container: gudit-performance-postgres
└─ Redis:      localhost:7002
   └─ container: gudit-performance-redis
```

Spring 프로젝트 내부의 권장 디렉터리 구조는 다음과 같다.

```text
Gudit/
├─ build.gradle
├─ docker-compose.yml
├─ docker-compose.performance.yml
├─ src/
│  └─ main/resources/
│     ├─ application.yaml
│     └─ application-performance.yaml
└─ src/performance-tests/performance-tests/
   ├─ docs/
   ├─ test-data/
   ├─ k6/
   ├─ scripts/
   └─ results/
```

## 3. Docker Compose healthcheck 오류

### 증상

IntelliJ 또는 Docker Compose 검증에서 healthcheck에 단일 값이 필요하다는 오류가 발생했다.

### 원인

`healthcheck.test`를 여러 줄 YAML 목록으로 작성하면서 들여쓰기나 탭이 섞였다. `CMD-SHELL`의 실제 명령도 하나의 문자열이어야 한다.

### 해결

인라인 배열을 사용한다.

PostgreSQL:

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres -d gudit"]
  interval: 5s
  timeout: 3s
  retries: 10
```

Redis:

```yaml
healthcheck:
  test: ["CMD", "redis-cli", "ping"]
  interval: 5s
  timeout: 3s
  retries: 10
```

Compose 파일은 실행 전에 검증한다.

```powershell
docker compose -f docker-compose.performance.yml config
```

## 4. `prepare-db.ps1` 경로를 찾지 못한 오류

### 증상

```text
The term '.\performance-tests\test-data\prepare-db.ps1' is not recognized
```

### 원인

- IntelliJ 터미널의 현재 위치가 프로젝트 루트가 아니었다.
- ZIP을 특정 폴더 안에 풀면서 `performance-tests/performance-tests`처럼 중첩됐다.
- 실제로는 `Gudit/src/performance-tests/performance-tests` 아래에 파일이 있었다.
- 존재 여부를 확인하지 않고 상대경로를 추측해서 실행했다.

### 해결

현재 저장소의 프로젝트 루트에서는 실제 경로를 사용해 바로 실행한다.

```powershell
.\src\performance-tests\performance-tests\test-data\prepare-db.ps1 `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres"
```

경로를 추측하지 않고 파일을 검색한다.

```powershell
$projectRoot = "C:\Users\a\Desktop\강좌\project\Goods Shop\Gudit"

$script = Get-ChildItem `
  -LiteralPath $projectRoot `
  -Recurse `
  -File `
  -Filter "prepare-db.ps1" `
  -ErrorAction SilentlyContinue |
  Select-Object -First 1

$script.FullName
```

경로가 확인되면 호출 연산자 `&`로 실행한다.

```powershell
& $script.FullName `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres"
```

### 재발 방지

현재 저장소에서는 프로젝트 루트에서 다음 스크립트를 dot-source해 실제 경로 변수를 사용한다.

```powershell
. ".\src\performance-tests\performance-tests\scripts\set-performance-paths.ps1"
& $prepareDbPath -ResetPerformanceDatabase -Container "gudit-performance-postgres"
```

`prepare-db.ps1`의 기본 컨테이너도 개발용 `postgres-db`가 아니라 격리된
`gudit-performance-postgres`로 수정했다. 그래도 파괴적인 초기화 명령에서는 대상 컨테이너를 명시한다.

## 5. IntelliJ 환경변수 입력 오류

### 증상

Environment variables 입력란에 다음과 같이 `.env` 경로와 환경변수가 섞였다.

```text
SPRING_PROFILES_ACTIVE=performance;
C:/.../Gudit/.env=;
C:/.../Gudit/.env;
JWT_SECERT_KEY=...
```

### 원인

- `.env` 파일 경로를 환경변수 이름처럼 입력했다.
- `JWT_SECRET_KEY`를 `JWT_SECERT_KEY`로 잘못 입력했다.
- Spring 설정과 k6 설정의 적용 위치가 혼동됐다.

### 해결

`.env`는 프로젝트 루트에 둔다.

```text
Gudit/.env
```

프로젝트에 `spring-dotenv`가 있으므로 IntelliJ Working directory를 프로젝트 루트로 설정하면 `.env`를 로딩할 수 있다.

```text
Working directory:
C:\Users\a\Desktop\강좌\project\Goods Shop\Gudit
```

성능테스트 Run Configuration에는 덮어쓸 값만 넣는다.

```text
SPRING_PROFILES_ACTIVE=performance
PERFORMANCE_JWT_SECRET_KEY=<성능테스트 JSON의 metadata.requiredEnvironment.JWT_SECRET_KEY 값>
```

현재 `application-performance.yaml`이 참조하는 정확한 환경변수 이름은
`PERFORMANCE_JWT_SECRET_KEY`이다. JSON의 `JWT_SECRET_KEY` 항목은 환경변수 이름이 아니라 적용할
테스트 키 값을 제공한다.

### 설정 적용 범위

| 설정 | 적용 위치 |
|---|---|
| 카카오·토스 테스트 설정 | 프로젝트 루트 `.env` |
| 성능테스트 DB·Redis 주소 | `application-performance.yaml` |
| 활성 프로필 | IntelliJ Spring Run Configuration |
| 성능테스트 JWT 키(`PERFORMANCE_JWT_SECRET_KEY`) | IntelliJ Spring Run Configuration |
| `BASE_URL`, RPS, 임계값 | k6 실행 명령의 `-e` |

## 6. Spring 성능테스트 프로필 구성

### 파일 위치

```text
Gudit/src/main/resources/application-performance.yaml
```

### 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/gudit
    username: postgres
    password: 1234
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: localhost
      port: 7002
      database: 0

  jpa:
    hibernate:
      ddl-auto: update
```

IntelliJ에는 별도의 성능테스트용 Spring 실행 설정을 만든다.

```text
Main class: com.team3.gudit.GuditApplication
Working directory: 프로젝트 루트

VU 기준 측정:  Name=GuditApplication - Performance, Active profiles=performance
동시성 테스트: Name=GuditApplication - Performance Concurrency, Active profiles=performance,concurrency
부하테스트:    Name=GuditApplication - Performance Load, Active profiles=performance,load
```

`Active profiles` 입력란이 없으면 다음 환경변수를 사용한다.

```text
SPRING_PROFILES_ACTIVE=performance
```

## 7. `relation "payments" does not exist` 오류

### 증상

테스트 JSON과 SQL 생성은 성공했지만 PostgreSQL 적재 과정에서 실패했다.

```text
BEGIN
ERROR: relation "payments" does not exist
PostgreSQL seed import failed.
```

### 직접 원인

성능테스트 PostgreSQL 컨테이너는 실행했지만 Spring 애플리케이션을 성능테스트 프로필로 한 번도 실행하지 않았다.

`POSTGRES_DB: gudit`은 데이터베이스만 생성한다. `payments`, `purchases`, `users` 같은 애플리케이션 테이블은 생성하지 않는다. 이 프로젝트에서는 Hibernate가 Spring 시작 과정에서 테이블을 생성한다.

시드 SQL의 첫 작업은 다음 테이블들을 초기화하는 것이다.

```sql
TRUNCATE TABLE payments, purchases, goods_sales, goods, users
RESTART IDENTITY CASCADE;
```

따라서 테이블이 하나라도 없으면 시드 적재가 실패한다.

### 해결

1. 성능테스트 Compose를 실행한다.
2. `performance` 프로필로 Spring을 실행한다.
3. Hibernate가 테이블을 생성할 때까지 정상 시작을 완료한다.
4. 테이블을 확인한다.
5. Spring을 종료한 후 시드 스크립트를 실행한다.
6. Spring을 다시 실행한다.

테이블 확인:

```powershell
docker exec gudit-performance-postgres `
  psql -U postgres -d gudit `
  -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;"
```

최소한 다음 테이블이 확인돼야 한다.

```text
goods
goods_sales
payments
purchases
users
```

### 참고

시드 적재는 `BEGIN` 안에서 실행되므로 중간에 오류가 발생하면 변경 내용은 커밋되지 않는다. 테이블을 생성한 후 스크립트를 다시 실행하면 된다.

## 8. `pg_stat_statements` 설정

`shared_preload_libraries`만 지정하면 PostgreSQL 라이브러리는 로딩되지만 DB 확장은 자동으로 생성되지 않는다.

최초 한 번 실행한다.

```powershell
docker exec gudit-performance-postgres `
  psql -U postgres -d gudit `
  -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
```

확인:

```powershell
docker exec gudit-performance-postgres `
  psql -U postgres -d gudit `
  -c "SELECT extname FROM pg_extension WHERE extname = 'pg_stat_statements';"
```

## 9. 최종 정상 실행 순서

### 최초 1회

```text
1. docker-compose.performance.yml 문법 검증
2. 성능테스트 PostgreSQL·Redis 실행
3. IntelliJ에 performance/performance,concurrency/performance,load Run Configuration 설정
4. 우선 performance 프로필로 Spring 실행
5. Hibernate 테이블 생성 확인
6. pg_stat_statements 확장 생성
7. Spring 종료
8. 테스트 데이터 적재
9. Spring 재실행
10. k6 preflight 실행
```

명령 예시:

```powershell
docker compose -f docker-compose.performance.yml config
docker compose -f docker-compose.performance.yml up -d
```

Spring 최초 실행과 테이블 확인 후:

```powershell
& $prepareDbPath `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres" `
  -Database "gudit" `
  -DatabaseUser "postgres"
```

Spring 재실행 후:

```powershell
k6 run `
  -e BASE_URL=http://localhost:8080 `
  .\src\performance-tests\performance-tests\k6\preflight.js
```

### 테스트 재실행

구매·취소 테스트는 DB와 Redis 상태를 변경한다. 현재 VU 실행기와 동시성 전체 실행기는 종료 시 기준
상태를 자동 복원하지만, 자동 복원이 실패했거나 개별 k6 스크립트를 실행했다면 다음 순서를 반복한다.

```text
Spring 종료
→ Redis FLUSHDB 및 테스트 데이터 재적재
→ 테스트 목적에 맞는 프로필로 Spring 실행
→ preflight
→ 동시성 또는 부하테스트
```

## 10. 실행 전 체크리스트

- [ ] `docker compose -f docker-compose.performance.yml config`가 성공한다.
- [ ] `gudit-performance-postgres`가 healthy 상태다.
- [ ] `gudit-performance-redis`가 healthy 상태다.
- [ ] VU는 `performance`, 동시성은 `performance,concurrency`, 부하는 `performance,load` 프로필이다.
- [ ] Spring datasource가 `localhost:5433/gudit`을 사용한다.
- [ ] Redis가 `localhost:7002`를 사용한다.
- [ ] `application-performance.yaml`이 `src/main/resources`에 있다.
- [ ] `PERFORMANCE_JWT_SECRET_KEY` 이름에 오타가 없다.
- [ ] 테스트 JWT 키가 생성된 JSON의 키와 일치한다.
- [ ] PostgreSQL에 `payments`, `purchases`, `users` 테이블이 있다.
- [ ] `prepare-db.ps1`의 대상 컨테이너가 `gudit-performance-postgres`다.
- [ ] 시드 데이터 적재 후 k6 preflight가 통과한다.

## 11. preflight의 역할과 `checks`만 실패한 경우

### 증상

```text
checks
✗ rate==1 rate=66.98%

http_req_failed
✓ rate==0 rate=0.00%
```

### 해석

`http_req_failed=0`은 서버와 HTTP 통신은 성공했다는 뜻이다. `checks` 실패는 응답 내용이 테스트 기준
상태와 다르다는 뜻이므로 Tomcat 연결 문제로 해석하면 안 된다.

preflight는 부하를 발생시키는 테스트가 아니라 다음 단계 실행 가능 여부를 판정하는 게이트다.

```text
애플리케이션 연결 확인
→ JWT 인증 확인
→ 판매 104건 존재·재고 확인
→ 판매 스케줄러의 READY → ON_SALE 전환 확인
→ 취소 시나리오용 구매 1번 확인
```

DB 초기화 직후 판매 상태는 `READY`다. 판매 시작 스케줄러가 Redis를 워밍업하고 `ON_SALE`로 바꾸기 전에
검사하면 응답은 200이어도 상태 check가 실패한다.

### 적용한 해결

`preflight.js`가 판매 104건이 모두 `ON_SALE`이 될 때까지 기본 최대 90초 동안 2초 간격으로 기다리도록
수정했다. 애플리케이션에 연결할 수 없으면 기다리지 않고 즉시 실패한다.

주의할 점은 `run-vu-capacity.ps1`의 최초 preflight는 테스트 반복문의 `try/finally`에 들어가기 전에
실행된다는 것이다. 최초 preflight가 실패하면 VU 단계와 최종 복원 절차가 시작되지 않는다.

## 12. `cancel fixture purchase is PENDING_PAYMENT` 실패

### 증상

```text
✗ cancel fixture purchase is PENDING_PAYMENT
  ↳ 0% — ✓ 0 / ✗ 1
```

다른 preflight 검사는 통과하고 이 항목만 실패했다.

### 실제 확인 결과

```text
purchase 1 created_at:  01:29:01
purchase 1 canceled_at: 01:42:00
purchase status:         CANCELED
payment status:          CANCELED
```

`PurchaseTimeoutScheduler`는 매분 실행되며 생성 후 10분이 지난 `PENDING_PAYMENT` 구매를 자동 취소한다.
구매 조회와 `saleId=104` 관계는 유지되므로 존재·판매 관계 검사는 통과하고 상태 검사만 실패했다.

### 해결 및 재발 방지

preflight 직전에 `prepare-db.ps1`을 실행한다. 이 스크립트는 JSON과 SQL을 매번 다시 생성하므로 구매
1번의 `created_at`도 실행 시각으로 갱신된다.

```powershell
& $prepareDbPath `
  -ResetPerformanceDatabase `
  -Container "gudit-performance-postgres"
```

동시성 시나리오 1~4가 10분을 넘으면 5번 시작 전에 취소용 구매가 다시 만료될 수 있다. 이 현상이
재현되면 5번 시나리오 직전에 취소용 fixture만 새로 만드는 방식이 가장 안전하다. 전체 DB를 중간에
초기화하면 1~5번 상태를 이어가는 테스트 설계가 깨진다.

상태 확인 SQL:

```powershell
docker exec gudit-performance-postgres `
  psql -U postgres -d gudit `
  -c "SELECT id, status, created_at, updated_at, canceled_at FROM purchases WHERE id = 1;"
```

## 13. VU 결과가 전부 0으로 표시된 오류

### 증상

```text
vus=100~1000
attempts=0
app=0
responses=0
result=load-generator-did-not-start-all
```

### 원인

k6 2.2가 내보낸 summary는 일부 메트릭을 다음과 같은 평면 구조로 저장했다.

```json
"probe_attempts": {
  "count": 100,
  "rate": 69.5
}
```

기존 요약기와 PowerShell 실행기는 이전 구조인 `metric.values.count`만 읽었다. 실제 요청은 실행됐지만
요약 단계에서 존재하지 않는 경로를 읽어 모두 0으로 표시했다.

### 적용한 해결

두 형식을 모두 지원하도록 수정했다.

```text
metric.values.count
→ 없으면 metric.count
→ 둘 다 없으면 0
```

수정 후 기존 raw summary를 다시 요약했을 때 VU 100에서 `attempts=100`이 복원됐다. 원본 JSON을 보존하면
요약기 오류를 수정한 뒤 테스트를 다시 실행하지 않고도 표를 재생성할 수 있다.

## 14. VU 단계 사이 Redis 상태가 남아 4xx가 증가한 문제

### 증상

- PostgreSQL은 매 VU 단계 전에 초기화했다.
- 뒤 단계로 갈수록 구매 요청의 HTTP 4xx가 증가했다.
- 단계별 결과가 앞 단계의 실행 여부에 따라 달라졌다.

### 원인

구매 상태는 PostgreSQL만이 아니라 Redis에도 저장된다. Redis에 다음과 같은 사용자별 구매 키가 남아
있으면 PostgreSQL을 다시 적재해도 애플리케이션은 이미 구매한 사용자로 판단할 수 있다.

```text
sale:{saleId}:user:{userId}
sale:{saleId}:stock
```

실제 성능테스트 Redis에는 이전 VU 실행 후 1,000개가 넘는 키가 남아 있었다. 따라서 당시 결과는 VU별
독립 비교가 아니라 이전 단계 상태가 누적된 결과였다.

### 적용한 해결

구매 VU 단계마다 다음 순서를 사용한다.

```text
gudit-performance-redis DB 0 FLUSHDB SYNC
→ 현재 시각 기준 fixture JSON·SQL 재생성
→ gudit-performance-postgres 초기화
→ preflight가 Redis 워밍업과 ON_SALE 전환 확인
→ 해당 VU 단계 실행
```

조회 전용 `Read` 모드는 DB와 Redis를 변경하지 않으므로 단계 사이 초기화를 생략한다. 초기화 대상은
반드시 격리된 `gudit-performance-redis`와 `gudit-performance-postgres`여야 한다.

## 15. 400·900 VU는 실패하고 1,000 VU는 통과한 비단조 결과

### 관측 결과

| VU | attempts | Spring 도달 | 미도달 | 연결 오류 | 결과 |
|---:|---:|---:|---:|---:|---|
| 400 | 400 | 279 | 121 | 121 | 실패 |
| 900 | 900 | 802 | 98 | 98 | 실패 |
| 1,000 | 1,000 | 1,000 | 0 | 0 | 통과 |

### 분석

표는 raw summary와 일치했다. 400과 900에서 사라진 요청은 timeout이나 HTTP 4xx/5xx가 아니라 모두
TCP 연결 단계 오류였으며 Spring `PerformanceProbeFilter`까지 도달하지 않았다.

`per-vu-iterations`는 VU마다 요청 한 번을 보장하지만 모든 요청이 정확히 같은 시각에 출발하는 것은
보장하지 않는다. 또한 프로브는 `noConnectionReuse: true`이므로 매 요청이 새 TCP 연결을 만든다.

```text
900 VU:  약 374.7 attempts/s, connecting p95 약 16.6ms
1000 VU: 약 349.0 attempts/s, connecting p95 약 62.0ms
```

1,000 VU 실행은 연결 생성이 더 오래 걸리면서 요청이 시간상 분산됐고, 900 VU는 더 짧은 구간에 연결이
몰렸다. 따라서 `900 실패 → 1000 성공`은 1,000이 더 가벼운 부하라는 뜻이 아니라 부하 발생 모양과
실행 시점의 TCP·CPU·Tomcat accept 상태가 달랐다는 뜻이다.

### 판정 원칙

한 번의 표는 정확한 실행 기록이지만 서버 용량 경계로 바로 사용할 수 없다. 결과가 비단조이면 요약기는
`monotonicBoundary=false`로 기록하며 `lastPassingVu`를 용량 보장값으로 사용하지 않는다.

경계 후보는 같은 VU를 최소 3회 반복해 판단한다.

```powershell
& $vuCapacityRunnerPath `
  -BaseUrl "http://localhost:8080" `
  -VuSteps 400,900,1000 `
  -CooldownSeconds 30 `
  -ResetPerformanceDatabase
```

- 3회 모두 같은 단계부터 실패: 재현 가능한 경계 후보
- 일부 실행만 실패: 순간적인 부하 발생기·TCP 연결 잡음
- VU가 커졌는데 다시 통과: 용량 경계 확정 금지

## 16. 동시성 시나리오의 상태 연결과 최종 초기화

동시성 테스트 1~5번은 서로 다른 판매 fixture를 사용하지만 하나의 연속된 DB 상태에서 실행하도록
설계했다. 시나리오 사이에 전체 DB를 초기화하면 앞 시나리오가 만든 상태를 이어가는 목적이 사라진다.

적용한 실행 순서:

```text
preflight
→ 시나리오 1
→ 시나리오 2
→ 시나리오 3
→ 시나리오 4
→ 시나리오 5
→ finally에서 Redis FLUSHDB
→ PostgreSQL 기준 데이터 재적재
```

k6 임계값이 실패해도 다음 시나리오는 실행하고, 5번 종료 후 `finally`에서 한 번만 초기화한다. 최종
복원 실패는 별도의 오류로 보고해 다음 부하테스트가 오염된 상태에서 시작하지 않게 한다.

## 17. Tomcat/Hikari 설정이 다른 테스트를 오염시킨 문제

Tomcat과 Hikari 설정을 `application-performance.yaml`에 직접 넣으면 VU 기준 테스트, 순간 동시성
테스트, 장시간 부하테스트가 모두 같은 튜닝값을 사용한다. 그러면 설정 변경 전후의 기준이 섞이고 한
테스트를 위한 대기열·풀 크기가 다른 테스트 결과에 영향을 준다.

### 적용한 해결

공통 datasource, Redis, JPA, JWT 설정은 `application-performance.yaml`에 유지하고 Tomcat/Hikari
차이만 별도 프로필 파일에 넣었다.

| 테스트 | 활성 프로필 | 목적 |
|---|---|---|
| VU 기준 측정 | `performance` | Tomcat/Hikari 추가 튜닝 전 기준값 유지 |
| 동시성 테스트 | `performance,concurrency` | 1,000 VU 순간 연결과 잠금 경합 수용 |
| 부하테스트 | `performance,load` | 지속 부하에서 제한된 큐와 DB 풀로 포화 관측 |

동시성 프로필:

```yaml
server:
  tomcat:
    max-connections: 2000
    accept-count: 1000
    threads:
      max: 300

spring:
  datasource:
    hikari:
      maximum-pool-size: 40
```

부하 프로필:

```yaml
server:
  tomcat:
    max-connections: 1500
    accept-count: 300
    threads:
      max: 240

spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      connection-timeout: 5000
```

성능테스트 PostgreSQL의 `max_connections=100`과 실행 PC의 논리 CPU 12개를 기준으로 시작값을 정했다.
이 값은 최종 정답이 아니며 반복 측정 결과와 JVM·DB 지표를 보고 조정한다. 테스트 종류를 바꿀 때는
Spring 애플리케이션을 해당 프로필로 재시작한다.

## 18. 현재 권장 실행 흐름

### VU 기준 테스트

```text
Spring: performance
→ Redis·PostgreSQL 초기화
→ preflight
→ 구매 모드는 각 VU 단계 전 Redis·PostgreSQL 재초기화 및 preflight
→ 결과 요약
→ finally에서 기준 상태 복원 및 preflight
```

### 동시성 테스트

```text
Spring 재시작: performance,concurrency
→ preflight
→ 시나리오 1~5 연속 실행
→ 시나리오 5 종료 후 Redis·PostgreSQL 자동 초기화
```

### 부하테스트

```text
Spring 재시작: performance,load
→ preflight
→ 워밍업
→ baseline/stress/spike/(soak)/mixed purchase
→ 결과 비교
```

## 19. 핵심 교훈

1. 상대경로를 추측하지 말고 현재 저장소의 경로 변수 스크립트로 실제 파일을 확인한다.
2. `.env` 경로를 환경변수 입력란에 직접 넣지 않는다.
3. 성능테스트용 DB·Redis는 컨테이너와 Spring profile로 분리한다.
4. PostgreSQL 데이터베이스 생성과 애플리케이션 테이블 생성은 별개다.
5. 시드 데이터를 넣기 전에 Spring을 한 번 실행해 Hibernate 스키마를 준비한다.
6. PostgreSQL만 초기화하면 Redis 상태가 다음 테스트를 오염시킬 수 있다.
7. preflight의 check 실패와 HTTP/전송 실패를 구분한다.
8. k6 summary 형식이 바뀔 수 있으므로 표가 0이면 raw JSON부터 확인한다.
9. 한 번의 비단조 VU 결과를 서버 용량 경계로 사용하지 않는다.
10. 동시성 시나리오 1~5는 상태를 이어가고 마지막에만 전체 초기화한다.
11. Tomcat/Hikari 설정은 테스트 목적별 프로필로 분리하고 테스트 전환 시 Spring을 재시작한다.
12. 테스트 실패 시 바로 서버 설정을 늘리기보다 데이터·프로필·Redis·DB·부하 발생기 순서로 원인을 분리한다.
