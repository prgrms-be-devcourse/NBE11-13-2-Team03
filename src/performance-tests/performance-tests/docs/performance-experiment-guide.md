# 성능 저하·Connection Refused 원인 분리 실험

## 1. 목적과 확인하려는 문제

이 실험은 높은 동시 요청에서 발생하는 성능 저하와 `Connection Refused`가 어느 계층의 제한과 관계있는지
확인한다. 한 그룹 안에서는 기준 설정에서 변수 하나만 변경하고, 동일 케이스를 반복해서 일시적인 Windows
스케줄링이나 순간 부하의 영향을 줄인다.

주요 가설은 다음과 같다.

- `accept-count`가 작으면 순간 연결 폭주가 OS pending connection queue를 넘어서 연결이 거절될 수 있다.
- Tomcat worker나 `max-connections`가 작으면 애플리케이션 진입·처리 지연이 증가할 수 있다.
- HikariCP가 작거나 DB 행 잠금 경합이 크면 TCP 연결 이후의 `waiting`과 응답시간이 증가할 수 있다.
- k6와 애플리케이션을 같은 Windows 호스트에서 실행하면 CPU·네트워크 스택을 서로 경쟁할 수 있다.

## 2. 실험 변수

실험값은 `config/performance-experiment-matrix.csv`에 있다.

| 그룹 | 변경 변수 | 기본 제공 값 |
|---|---|---|
| `tomcat-accept` | `server.tomcat.accept-count` | 100, 300, 1000 |
| `tomcat-threads` | `server.tomcat.threads.max` | 100, 300, 600 |
| `tomcat-connections` | `server.tomcat.max-connections` | 500, 1500, 8192 |
| `hikari` | `spring.datasource.hikari.maximum-pool-size` | 10, 40, 80 |
| `vu` | 동시 VU | 100, 300, 500, 700, 900, 1000 |
| `load-model` | 부하 증가 방식 | 동시 시작, 10초 분산, ramping VU, ramping arrival rate |
| `db-lock` | 재고 처리 방식 | Redis Lua, DB 비관적 잠금 |
| `environment` | k6 / 애플리케이션 위치 | host/host, Docker/host, host/Docker, Docker/Docker |

`inventory_mode=redis`는 기존 기본 동작이며 재고 DB 행을 잠그지 않고 Redis Lua 원자 연산을 사용한다.
`inventory_mode=db-pessimistic`는 기존 `findByIdWithLock()`을 선택해 PostgreSQL의 `SELECT FOR UPDATE`를
사용한다. 애플리케이션 기본값은 계속 `redis`이므로 일반 실행 동작은 바뀌지 않는다.

`burst`와 `linear-spread`는 모두 VU당 요청 한 건을 실행한다. 따라서 같은 요청 수를 즉시 시작했을 때와
일정 시간에 분산했을 때를 직접 비교할 수 있다. `ramping-vus`와 `ramping-arrival-rate`는 일정 시간 동안
반복 요청하므로 총 요청 수보다 초당 처리율과 시간대별 지표를 중심으로 비교한다.

## 3. 실행 준비

프로젝트 루트에서 성능테스트 PostgreSQL과 Redis를 먼저 실행한다.

```powershell
docker compose -f .\docker-compose.performance.yml up -d
```

실행기는 테스트마다 Spring 애플리케이션을 직접 시작하므로 IntelliJ에서 실행 중인 8080 포트의 서버는
먼저 종료한다. Docker 환경 케이스는 최초 실행 시 k6/JRE 이미지를 내려받을 수 있다.

경로 변수를 준비한다.

```powershell
. ".\src\performance-tests\performance-tests\scripts\set-performance-paths.ps1"
```

## 4. 테스트 진행 방법

원인별 그룹 하나를 선택해 실행한다.

```powershell
& $experimentRunnerPath `
  -ResetPerformanceDatabase `
  -Group "tomcat-accept"
```

Connection Refused가 `accept-count`에 따라 줄어드는지 확인하는 전용 실험은 다음 명령을 사용한다. 이 실행은
`accept-count=100/300/1000`을 각각 정확히 5회 반복하고 다른 Tomcat/Hikari/VU 조건은 고정한다.

```powershell
& $acceptCountExperimentRunnerPath `
  -ResetPerformanceDatabase
```

다른 그룹 예시:

```powershell
& $experimentRunnerPath -ResetPerformanceDatabase -Group "hikari"
& $experimentRunnerPath -ResetPerformanceDatabase -Group "vu"
& $experimentRunnerPath -ResetPerformanceDatabase -Group "load-model"
& $experimentRunnerPath -ResetPerformanceDatabase -Group "db-lock"
& $experimentRunnerPath -ResetPerformanceDatabase -Group "environment"
```

케이스 한 개만 재실행할 수도 있다.

```powershell
& $experimentRunnerPath `
  -ResetPerformanceDatabase `
  -CaseId "accept-1000"
```

모든 그룹을 한꺼번에 실행하려면 `-All`을 사용할 수 있지만 실행 시간이 길다. 기본 matrix는 케이스당
3회 반복한다. 최종 결론을 내릴 때는 CSV의 `repeats`를 5 이상으로 늘리는 것을 권장한다.

실행기는 각 케이스에서 다음 순서를 지킨다.

```text
기존 fixture로 PostgreSQL·Redis 초기화
→ 지정한 Tomcat/Hikari/재고 모드로 Spring 시작
→ preflight
→ k6 실험
→ Spring 종료
→ 기존 fixture로 PostgreSQL·Redis 재초기화
→ 다음 케이스
```

Tomcat과 Hikari 설정은 애플리케이션 시작 인자로 전달하므로 각 케이스의 서버 재시작이 필수다. 실행기가
서버를 관리하기 때문에 이전 케이스의 listener, worker, connection pool 상태도 다음 케이스에 이어지지 않는다.

## 5. 데이터 초기화

기존 `test-data/prepare-db.ps1`을 그대로 호출한다. 이 스크립트가 사용하는
`generate-data.mjs`, `generate-seed-sql.mjs`도 수정하지 않는다.

- 본 테스트 직전 초기화: 이전 실행이나 수동 테스트가 남긴 상태를 제거한다.
- 테스트 종료 후 `finally` 초기화: 성공·실패와 관계없이 후속 테스트가 기준 상태에서 시작하게 한다.
- 구매 실험도 매 반복마다 사용자, 판매, 구매, 결제, Redis 재고가 같은 상태로 돌아간다.

초기화 대상은 격리된 `gudit-performance-postgres`, `gudit-performance-redis`로 고정되어 있다.

## 6. 주요 확인 지표

| 결과 열 | 의미 |
|---|---|
| `requests`, `successes`, `failures` | 실험 대상 요청의 전체·성공·실패 수 |
| `connection_refused` | k6 오류 코드 1212 또는 명시적 연결 거절 메시지 수 |
| `server_arrivals`, `server_missing` | Spring 최상단 probe filter 진입 수와 미진입 수 |
| `response_p95_ms` | 제어 요청을 제외한 실험 요청 응답시간 p95 |
| `http_req_failed_rate` | k6 기본 `http_req_failed` 비율 |
| `http_req_blocked_p95_ms` | k6 내부에서 소켓·연결 사용을 기다린 시간 p95 |
| `http_req_connecting_p95_ms` | TCP 연결 수립 시간 p95 |
| `http_req_waiting_p95_ms` | 요청 전송 후 첫 응답 바이트까지 시간 p95 |
| `dropped_iterations` | arrival rate를 k6가 생성하지 못한 반복 수 |

`http_req_*` 기본 지표에는 setup/teardown의 소수 제어 요청도 포함된다. 정확한 실험 요청 수와 응답시간은
`experiment_*` 기반 열인 `requests`, `response_p95_ms`를 우선 사용한다.

## 7. 결과 비교 방법

결과는 다음 위치에 생성된다.

```text
results/performance-experiments-실행시각/
├─ comparison.csv
├─ comparison-aggregate.csv
└─ 케이스/repeat-N/
   ├─ case.json
   ├─ summary.json
   └─ application 로그
```

`comparison.csv`는 반복별 원본 비교표이고 `comparison-aggregate.csv`는 케이스별 합계·평균이다.

- `accept-count` 증가 후 `connection_refused`와 `server_missing`이 함께 줄면 pending queue 포화 가설을
  강하게 지지한다. p95만 늘면 거절되던 연결이 큐에서 기다리게 된 것이다.
- `blocked`만 크게 늘면 애플리케이션보다 k6 실행 호스트의 자원 한계를 먼저 의심한다.
- `connecting`과 거절 수가 늘고 Spring 진입은 줄면 TCP/listener 이전 구간 문제에 가깝다.
- `waiting`과 p95가 늘지만 연결 거절과 미진입이 없으면 Tomcat worker, HikariCP, DB 처리 구간을 본다.
- DB 비관적 잠금에서만 `waiting`과 p95가 커지면 단일 판매 행 lock 경합의 영향이다.
- Docker k6에서 거절이 줄면 동일 Windows 호스트에서 k6와 서버가 경쟁하는 영향이 있었음을 뜻한다.

한 번의 결과로 경계를 확정하지 않는다. 동일 케이스의 반복 결과가 비단조적이면 Wireshark TCP 캡처와
애플리케이션 로그를 같은 시간축으로 확인하고 반복 횟수를 늘린다.
