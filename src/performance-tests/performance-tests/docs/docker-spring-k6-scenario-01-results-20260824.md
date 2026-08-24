# Docker Spring/k6 1번 시나리오 실험 결과

## 1. 목적

Windows 호스트에서 Spring과 k6를 같이 실행했을 때 발생한 `Connection Refused`를 피하기 위해, Spring과 k6를 Docker Desktop의 Linux 환경으로 옮겨 동시성 1번 시나리오를 5회 반복한다.

애플리케이션, k6 및 기존 테스트 데이터 생성 코드는 수정하지 않고, 별도 Compose 오버레이만 사용한다.

## 2. 실행 환경

- 호스트: Windows, Docker Desktop WSL2 backend
- Docker Engine: Linux/amd64
- Docker Linux kernel: `6.18.33.2-microsoft-standard-WSL2`
- Spring 이미지: `eclipse-temurin:25-jre`
- k6 이미지: `grafana/k6:2.2.0`
- Spring 프로필: `performance,concurrency`
- PostgreSQL: 기존 `gudit-performance-postgres`
- Redis: 기존 `gudit-performance-redis`
- 테스트: `k6/concurrency/01-oversell-hotspot.js`
- 부하: VU 1,000, VU당 1회 요청
- 기대 결과: 구매 성공 100건, 재고 소진 거절 900건

## 3. Docker 구성

`docker-compose.container-test.yml`을 기존 `docker-compose.performance.yml`과 함께 사용한다.

- Spring은 호스트에서 빌드한 현재 JAR을 읽기 전용으로 마운트한다.
- DB와 Redis 주소는 Compose 서비스 이름과 컨테이너 포트로 환경변수에서 재정의한다.
- k6는 `network_mode: service:spring-performance`로 Spring 컨테이너의 네트워크 네임스페이스를 공유한다.
- 때문에 k6 스크립트는 기존과 같은 `http://localhost:8080`을 사용한다.
- 호스트에도 `127.0.0.1:8080` 하나만 게시하므로 IntelliJ Terminal, 브라우저와 PowerShell에서도 기존처럼 `localhost:8080`으로 확인할 수 있다.

서로 다른 일반 bridge 네트워크만 사용하면 k6 컨테이너의 `localhost`는 k6 자신을 가리키므로 Spring에 도달하지 않는다. 현재 구성에서 `localhost:8080`이 동작하는 이유는 두 컨테이너가 네트워크 네임스페이스를 공유하기 때문이다.

## 4. 진행 방법

IntelliJ의 기존 프로젝트와 Terminal을 그대로 사용할 수 있다. Dockerfile, 애플리케이션 코드, k6 코드 및 데이터 생성 스크립트는 필요하지 않다.

1. `test/performance` 브랜치의 현재 소스로 `bootJar`를 실행한다.
2. 생성 데이터 JSON의 `metadata.requiredEnvironment.PERFORMANCE_JWT_SECRET_KEY`를 현재 PowerShell 세션의 `PERFORMANCE_JWT_SECRET_KEY`로 설정한다.
3. 두 Compose 파일로 `spring-performance`를 시작한다.
4. 새 k6 컨테이너로 preflight를 실행한다.
5. 각 회차 전에 기존 `prepare-db.ps1 -ResetPerformanceDatabase -Scenario 1`로 DB와 Redis를 초기화한다.
6. 1번 시나리오를 실행하고 JSON 요약과 k6 로그를 `results/docker-spring-k6`에 저장한다.
7. 실험 종료 후 DB와 Redis를 `Scenario All`로 기준 상태로 초기화하고, 실험용 Spring 컨테이너만 제거한다.

## 5. 준비 검증

Docker k6에서 `BASE_URL=http://localhost:8080`으로 preflight를 실행했다.

- HTTP 요청: 108건
- `http_req_failed`: 0건, 0.00%
- checks: 321/321 성공
- 결과: 통과

이 결과로 k6 컨테이너의 `localhost:8080`이 Spring 컨테이너로 연결되며, 생성 데이터와 JWT가 정상적으로 적용됨을 확인했다.

## 6. 5회 반복 결과

| 회차 | HTTP 요청 | 구매 성공 | 예상 거절 | 예상 밖 응답 | Connection Refused | `http_req_failed` | business p95 | blocked p95 | connecting p95 | waiting p95 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1,001 | 100 | 900 | 0 | 0 | 89.91% | 1,403.44 ms | 1,008.20 ms | 1,008.08 ms | 1,399.23 ms |
| 2 | 1,001 | 100 | 900 | 0 | 0 | 89.91% | 849.72 ms | 90.02 ms | 89.05 ms | 846.79 ms |
| 3 | 1,001 | 100 | 900 | 0 | 0 | 89.91% | 755.33 ms | 66.32 ms | 65.83 ms | 750.79 ms |
| 4 | 1,001 | 100 | 900 | 0 | 0 | 89.91% | 775.48 ms | 98.91 ms | 96.70 ms | 771.02 ms |
| 5 | 1,001 | 100 | 900 | 0 | 0 | 89.91% | 716.12 ms | 72.12 ms | 68.42 ms | 715.16 ms |
| **5회 p95 평균** | - | - | - | - | **0** | **89.91%** | **900.02 ms** | **267.11 ms** | **265.62 ms** | **896.60 ms** |

모든 회차에서 다음 threshold를 통과했다.

- `business_successes count==100`
- `business_expected_rejections count==900`
- `business_unexpected_responses count==0`
- `business_request_duration p(95)<3000`
- `checks rate==1`

## 7. 결과 해석

### Connection Refused

5회, 총 5,000건의 구매 요청에서 `Connection Refused`, `actively refused`, `connectex` 로그는 0건이었다. 모든 VU가 요청을 시작했고 구매 API 응답을 받았으며, 마지막 재고와 판매 상태 검증도 통과했다.

이 구성에서 k6→Spring 연결은 Docker Linux 네트워크 네임스페이스 내의 loopback에서 종료된다. 따라서 Windows 호스트의 8080 listen queue는 컨테이너 k6의 부하 경로에 포함되지 않는다.

기존 Windows 로컬 실행에서 관찰한 `business_unexpected_responses=516`과 비교하면 이번 실험은 5회 모두 0으로 줄었다. 다만 같은 시간대에 Windows 방식과 Docker 방식을 교차 실행한 A/B 실험은 아니므로, Docker 이동 하나만이 차이의 유일한 원인이라고 단정하지는 않는다.

### `http_req_failed=89.91%`

이 값은 연결 실패율이 아니다. 1번 시나리오는 재고 100개에 1,000명이 동시에 구매를 시도하므로, 900건은 의도한 비-2xx 비즈니스 거절이다. k6의 기본 HTTP 지표는 이 900건을 `http_req_failed`로 집계한다.

- 전체 HTTP 요청: 구매 1,000건 + teardown 검증 1건 = 1,001건
- 비-2xx 예상 거절: 900건
- `900 / 1,001 = 89.91%`
- 예상 밖 응답: 0건
- Connection Refused: 0건

따라서 이 시나리오에서는 `http_req_failed`보다 `business_unexpected_responses`와 k6 전송 오류 로그를 함께 봐야 연결 실패를 구분할 수 있다.

### 첫 회차 워밍업 효과

1회차의 `connecting p95` 1,008.08 ms는 2∼5회의 65.83∼96.70 ms보다 크다. 첫 부하에서 JVM JIT, Spring/Tomcat, DB 커넥션 풀, 컨테이너 네트워크가 초기화되는 콜드 스타트 효과가 포함된 것으로 해석할 수 있다. 이 설명은 지표 패턴에 대한 추론이며, 각 요소의 워밍업 시점을 별도로 계측한 것은 아니다.

## 8. 복원 상태

- 실험 종료 후 기존 생성 스크립트의 `Scenario All`로 성능 DB와 Redis를 기준 상태로 초기화했다.
- 실험 전 백업과 복원된 `performance-test-data.json`, `seed-performance-data.sql`의 SHA-256 해시가 일치한다.
- 실험용 `gudit-performance-spring` 컨테이너는 제거했다.
- 기존 `gudit-performance-postgres`, `gudit-performance-redis`는 실험 전과 같이 실행 중이며 모두 healthy 상태이다.
- 기존 미커밋 파일은 덮어쓰지 않았다.
- 실험 전 생성 파일 백업은 `C:\Users\a\AppData\Local\Temp\gudit-container-test-backup-20260824`에 유지했다.

## 9. 결론

Compose 오버레이 하나만으로 Spring과 k6를 Docker Linux 환경으로 옮겨 기존 k6의 `localhost:8080`을 그대로 사용할 수 있다. 이 실험에서는 1,000 VU 시나리오를 5회 반복해도 Connection Refused가 발생하지 않았다.

다만 k6와 Spring은 동일 Docker Desktop VM과 물리 CPU/메모리를 공유한다. 따라서 이 구성은 Windows listen queue 개입 여부를 비교하는 로컬 실험에는 적합하지만, 부하 생성기와 서버를 물리적으로 분리한 성능 측정을 대체하지는 않는다.
