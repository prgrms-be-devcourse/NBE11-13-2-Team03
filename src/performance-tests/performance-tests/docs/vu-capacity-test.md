# VU 증가에 따른 애플리케이션 도달 임계점 테스트

## 결론

기존 혼합 구매 테스트는 `per-vu-iterations`로 1,000 VU를 한 번에 시작했다. 이 방식은 실제 사용자의
점진적인 유입보다 순간 연결 폭주에 가깝다. 애플리케이션은 Tomcat 작업 스레드, HikariCP DB 연결,
PostgreSQL 잠금 순서로 더 좁은 자원을 통과하므로 1,000개 요청이 동시에 오면 대기열이 길어진다.
클라이언트 타임아웃이나 연결 실패가 발생해도 기존 결과에는 서버 필터 도달 수가 없어서, 서버 진입 전
실패와 서버 내부 대기를 구분할 수 없었다.

가장 간단한 운영 가능한 해결은 다음 두 테스트의 목적을 분리하는 것이다.

1. 혼합 부하테스트는 구매 1,000건을 유지하되 기본 동시 실행 VU를 100으로 제한한다.
2. 정말 1,000명의 동시 접속이 요구사항이면 서버 설정을 먼저 올리지 말고 이 문서의 VU 탐색으로
   마지막 정상 단계와 첫 실패 단계를 찾는다.
3. 첫 실패의 위치가 확인된 뒤에만 Tomcat, HikariCP, PostgreSQL을 해당 병목에 맞춰 조정한다.

`05-mixed-purchase-peak.js`는 이를 위해 `shared-iterations`로 변경되었다. 기본값은 총 1,000건,
동시 100 VU이며 다음 환경값으로 바꿀 수 있다. 100은 시작값일 뿐 보장된 서버 용량이 아니며,
VU 탐색 결과의 `lastPassingVu`를 넘지 않도록 조정한다.

```text
PurchaseIterations=1000
PurchaseVus=100
```

`run-load-suite.ps1`의 `-PurchaseIterations`, `-PurchaseVus` 인자로 전달한다.

## 서버 도달 판정 방법

`reachability-probe.js`는 기존 생성 데이터의 사용자 1~1,000과 판매 데이터를 사용해 VU마다 정확히
한 번 요청한다. 기본 `SingleRowPurchase` 모드는 기존 혼합 구매 테스트와 같은 판매 ID 2에 구매를 보내므로
문제를 그대로 재현한다. 구매 모드에서는 각 VU 단계 전에 성능테스트용 Redis와 PostgreSQL을 함께
초기화한다. Redis의 이전 구매 키가 남으면 뒤 단계에서 4xx가 발생해 VU별 결과를 서로 오염시키기 때문이다.

| 모드 | 대상 | 목적 | 단계별 DB 초기화 |
|---|---|---|---|
| `SingleRowPurchase` (기본) | 판매 ID 2 구매 | 기존 1,000 VU 문제와 단일 행 잠금 재현 | 함 |
| `DistributedPurchase` | 판매 ID 3~102 구매 | 잠금 경합을 분산한 쓰기 경로와 비교 | 함 |
| `Read` | 판매 ID 3~102 상세 조회 | DB 변경 없이 순수 진입·인증·조회 용량 비교 | 안 함 |

각 요청에는 다음 두 헤더가 들어간다.

```text
X-Performance-Test-Run-Id
X-Performance-Test-Request-Id
```

Spring의 `PerformanceProbeFilter`는 보안 필터와 컨트롤러보다 앞에서 고유 요청 ID를 기록한다.
`performance` 프로필에서만 빈이 만들어지므로 일반 실행과 운영 프로필에는 영향을 주지 않는다.

결과는 다음 순서로 해석한다.

| 관측 결과 | 의미 |
|---|---|
| `attempts < VU` | k6가 모든 VU/iteration을 시작하지 못함 |
| `server_arrivals < attempts` | 측정 유예시간 안에 Spring 필터까지 도달하지 못함 |
| `server_completions < server_arrivals` | Spring에는 도달했지만 snapshot 시점에도 요청 처리가 진행 중임 |
| `server_arrivals = attempts`, `http_responses < attempts` | Spring에는 도달했지만 처리/대기 중 k6가 응답을 받지 못함 |
| HTTP 4xx/5xx 증가 | Spring까지 도달했고 애플리케이션 또는 서버 오류 응답을 반환함 |
| `successful_responses = attempts` | 해당 단계의 모든 요청이 정상 완료됨 |

`server_arrivals`는 테스트 종료 후 요청 수와 완료 수가 안정되고 active 요청이 0이 될 때까지 기본 최대
5초 동안 반복 조회한다. 연결 큐가 더 오래 지연될 수 있는 환경은 `-ServerSettleSeconds`를 늘려서
"미도달"의 관측 창을 명시적으로 바꾼다.

## 실행 전 준비

1. 성능테스트용 PostgreSQL과 Redis를 실행한다.
2. 추가된 서버 계측 코드가 포함된 애플리케이션을 `performance` 프로필로 재시작한다.
3. 생성 JSON의 `JWT_SECRET_KEY`를 애플리케이션에 적용한다.
4. 격리된 성능테스트 DB인지 다시 확인한다.

DB 초기화 직후 판매 상태는 `READY`다. preflight는 60초 주기의 판매 시작 스케줄러가 Redis를 워밍업하고
104건을 `ON_SALE`로 전환할 때까지 기본 최대 90초 기다린다. 환경이 느리면
`-PreflightWaitSeconds`를 늘린다.

서버 계측 코드:

```text
src/main/java/com/team3/gudit/performance/PerformanceProbeRegistry.java
src/main/java/com/team3/gudit/performance/PerformanceProbeFilter.java
src/main/java/com/team3/gudit/performance/PerformanceProbeController.java
```

## 기본 실행

```powershell
& $vuCapacityRunnerPath `
  -BaseUrl "http://localhost:8080" `
  -ResetPerformanceDatabase
```

경로 변수를 사용하지 않는다면:

```powershell
.\src\performance-tests\performance-tests\scripts\run-vu-capacity.ps1 `
  -BaseUrl "http://localhost:8080" `
  -ResetPerformanceDatabase
```

잠금 경합과 무관한 도달 한계를 비교하려면 같은 VU 단계로 조회 모드를 실행한다.

```powershell
.\src\performance-tests\performance-tests\scripts\run-vu-capacity.ps1 `
  -BaseUrl "http://localhost:8080" `
  -ProbeMode Read `
  -ResetPerformanceDatabase
```

기본 단계:

```text
100 → 200 → 300 → 400 → 500 → 600 → 700 → 800 → 900 → 1000 VU
```

처음 실패한 구간을 더 좁히는 예:

```powershell
.\src\performance-tests\performance-tests\scripts\run-vu-capacity.ps1 `
  -BaseUrl "http://localhost:8080" `
  -VuSteps 525,550,575,600 `
  -StopAfterFirstMissing `
  -ResetPerformanceDatabase
```

## Redis 및 DB 초기화 순서

실행기는 다음 순서를 보장한다.

```text
성능테스트 Redis DB FLUSHDB 및 기존 생성 JSON·SQL 기반 PostgreSQL 초기화
→ preflight
→ 단계별 VU 테스트(구매 모드는 각 단계 전 Redis·PostgreSQL 재초기화)
→ 결과 요약
→ Redis·PostgreSQL 재초기화
→ preflight
→ 다음 성능테스트 실행 가능 상태
```

마지막 재초기화는 테스트 도중 오류가 나도 `finally`에서 시도한다. 운영/공용 저장소의 실수 방지를 위해
`-ResetPerformanceDatabase`를 명시하지 않으면 실행하지 않는다. 기본 대상은
`gudit-performance-redis`의 Redis DB 0과 `gudit-performance-postgres`이며, 격리된 성능테스트
컨테이너에서만 실행한다.

## 결과 파일

```text
src/performance-tests/performance-tests/results/vu-capacity-실행시간/
├─ vu-100.summary.json
├─ ...
├─ vu-capacity-comparison.csv
└─ vu-capacity-verdict.json
```

`vu-capacity-verdict.json`은 실행한 `probeMode`를 함께 기록한다. `lastPassingVu`는 마지막 정상 단계,
`firstFailingVu`는 최초 미도달 단계다. 예를 들어 500은 통과하고 600이 실패했다면 실제 경계는
500과 600 사이이므로 525, 550, 575처럼 중간 단계를 다시 측정한다.

## 병목별 다음 조치

### 애플리케이션 필터 미도달

- k6의 timeout/connection/DNS/TLS 오류 수를 먼저 확인한다.
- 프록시나 로드밸런서가 있다면 연결 수, 대기열, upstream timeout을 확인한다.
- 직접 Tomcat에 연결한 환경이라면 Tomcat 작업 스레드와 연결 대기열을 함께 관측한다.

### 필터 도달 후 응답 없음

- `monitor-load.ps1`의 DB 연결, active connection, lock waiter를 같은 시간대에 확인한다.
- 단일 판매 행 구매라면 행 잠금 직렬화가 원인인지 분산 판매 조회/구매 결과와 비교한다.
- HikariCP 크기는 PostgreSQL 처리량을 측정한 뒤 조정한다. 1,000 VU에 맞춰 무조건 1,000으로 늘리면
  DB 컨텍스트 스위칭과 잠금 경합이 악화될 수 있다.

### HTTP 5xx

- 예외 로그에서 Hikari connection timeout, PostgreSQL connection 제한, lock timeout, JVM OOM/GC를 확인한다.
- 서버 자원 변경 전 같은 VU 단계에서 3회 이상 반복해 재현성을 확인한다.

## 서버 설정 변경 원칙

Tomcat의 `threads.max`, `max-connections`, `accept-count`와 HikariCP의 `maximum-pool-size`는 서로 다른
대기 구간을 제어한다. 하나의 수치만 1,000으로 맞추는 것은 해결책이 아니다. VU 탐색 결과와
`monitor-load.ps1` 결과로 실제 포화 지점을 확인한 뒤 작은 폭으로 변경하고 같은 단계들을 재실행한다.
