# Gudit 성능테스트

Spring 애플리케이션의 구매 동시성 및 지속 부하를 검증하는 독립형 k6 테스트 모음이다.

## 디렉터리

- `docs`: 테스트 계획과 합격 기준
- `test-data`: JSON 및 PostgreSQL 시드 생성·적재
- `k6/lib`: 인증, HTTP, 메트릭, 워크로드, 검증 공통 모듈
- `k6/concurrency`: 재고·중복 구매·취소 동시성 테스트
- `k6/load`: 워밍업, 기준, 스트레스, 스파이크, 소크, 혼합 부하
- `k6/vu-capacity`: VU 증가에 따른 애플리케이션 도달 임계점 탐색
- `scripts`: 전체 실행, 모니터링, 결과 비교
- `config`: 로컬 환경변수 예시
- `results`: 실행 결과 저장 위치

## 준비

1. PowerShell의 현재 위치를 Spring 프로젝트 루트로 맞춘다. 현재 저장소에서 성능테스트 루트는
   `src/performance-tests/performance-tests`다.
2. `test-data/generated/performance-test-data.json`의
   `metadata.requiredEnvironment.PERFORMANCE_JWT_SECRET_KEY` 값을 같은 이름의 Spring Boot 환경변수로 설정한다.
3. PostgreSQL과 Spring 애플리케이션을 테스트 전용 환경에서 실행한다.
   VU 기준 측정은 `performance`, 동시성 테스트는 `performance,concurrency`, 부하테스트는
   `performance,load` 프로필을 사용한다. `application-concurrency.yaml`과
   `application-load.yaml`에는 Tomcat/Hikari 차이만 있으므로 datasource, Redis, JWT 등은
   `application-performance.yaml` 설정을 그대로 사용한다.
4. 테스트 DB를 초기화한다.

```powershell
.\src\performance-tests\performance-tests\test-data\prepare-db.ps1 -ResetPerformanceDatabase
```

5. 사전조건을 확인한다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 .\src\performance-tests\performance-tests\k6\preflight.js
```

## 실행

동시성 테스트 전체 실행:

```powershell
.\src\performance-tests\performance-tests\scripts\run-concurrency-suite.ps1 -BaseUrl http://localhost:8080
```

부하테스트 전체 실행:

```powershell
.\src\performance-tests\performance-tests\scripts\run-load-suite.ps1 -BaseUrl http://localhost:8080
```

혼합 구매의 동시 수를 VU 탐색 결과에 맞추는 예:

```powershell
.\src\performance-tests\performance-tests\scripts\run-load-suite.ps1 `
  -BaseUrl http://localhost:8080 `
  -PurchaseVus 100 `
  -PurchaseIterations 1000
```

VU 도달 임계점 탐색(기본: 기존 판매 ID 2에 대한 단일 행 구매):

```powershell
.\src\performance-tests\performance-tests\scripts\run-vu-capacity.ps1 `
  -BaseUrl http://localhost:8080 `
  -ResetPerformanceDatabase
```

이 실행기는 테스트 전과 각 구매 단계 사이에 성능테스트용 Redis와 PostgreSQL을 초기화하고, 100 VU부터
1,000 VU까지 증가시키며 측정한 뒤, 다음 성능테스트가 같은 데이터에서 시작하도록 기준 데이터와
preflight를 다시 복원한다. Spring 애플리케이션에는
`performance` 프로필 전용 `PerformanceProbeFilter`, `PerformanceProbeController`,
`PerformanceProbeRegistry`가 포함되어 있어야 한다. 상세 판정법은
`docs/vu-capacity-test.md`를 참고한다.

Tomcat, HikariCP, VU, 부하 증가 방식, DB Lock, 실행 환경 비교 실험:

```powershell
. ".\src\performance-tests\performance-tests\scripts\set-performance-paths.ps1"

& $experimentRunnerPath `
  -ResetPerformanceDatabase `
  -Group "tomcat-accept"
```

실험 matrix와 그룹별 실행·해석 방법은 `docs/performance-experiment-guide.md`를 참고한다.

30분 소크 테스트 포함:

```powershell
.\src\performance-tests\performance-tests\scripts\run-load-suite.ps1 -BaseUrl http://localhost:8080 -IncludeSoak
```

상세 내용은 `docs/concurrency-test-plan.md`와 `docs/load-test-plan.md`를 참고한다.

## 주의

- 테스트 데이터와 JWT 키는 격리된 성능테스트 환경에서만 사용한다.
- `prepare-db.ps1`은 지정한 5개 테이블을 초기화한다.
- 동시성 전체 실행기는 1~5번 시나리오 사이에는 상태를 유지한다. 이후 6번과 7번은 각각 전용 결제 fixture로 초기화해 실행하고 DB·Redis 최종 상태를 검증하며, 전체 종료 후 기준 상태로 복원한다.
- 자동 초기화 대상은 기본적으로 `gudit-performance-redis`와 `gudit-performance-postgres`다.
- 혼합 부하의 구매 1,000건은 기본적으로 100 VU가 나눠 실행한다. `PURCHASE_VUS`는 VU 탐색에서 확인한 정상 범위 안에서 조정한다.
- 운영 DB나 공용 개발 DB에서는 데이터 초기화 스크립트를 실행하지 않는다.
