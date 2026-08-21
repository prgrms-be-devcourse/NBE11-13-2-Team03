# Gudit k6 동시성 테스트

## 시나리오

| 스크립트 | 부하 | 검증 목적 |
|---|---:|---|
| `01-oversell-hotspot.js` | 1,000 VU / 재고 100 | 초과 판매 방지와 품절 전환 |
| `02-single-row-lock-capacity.js` | 1,000 VU / 재고 1,000 | 단일 비관적 잠금 행의 대기 시간 |
| `03-distributed-baseline.js` | 1,000 VU / 판매 100건 | Redis 재고 Key 100개로 요청을 분산한 기준 성능 |
| `04-duplicate-purchase-race.js` | 동일 사용자 요청 50건 | 중복 구매 1건만 생성되는지 확인 |
| `05-cancel-race.js` | 동일 구매 취소 50건 | 재고가 한 번만 복구되는지 확인 |

각 시나리오는 `per-vu-iterations` 실행기를 사용해 모든 VU가 요청을 한 번씩 보냅니다. 응답 건수 임계값과 테스트 종료 후 재고·구매 상태 검증이 모두 통과해야 성공입니다.

## 사전 준비

1. Spring 애플리케이션은 `performance,concurrency` 프로필로, PostgreSQL은 테스트 전용 환경에서
   실행합니다. IntelliJ의 Active profiles 또는 `SPRING_PROFILES_ACTIVE`에 두 프로필을 쉼표로
   지정해야 `application-performance.yaml`의 공통 설정과 `application-concurrency.yaml`의
   Tomcat/Hikari 설정이 함께 적용됩니다.
2. `test-data/generated/performance-test-data.json`의 `metadata.requiredEnvironment.JWT_SECRET_KEY` 값을 IntelliJ Spring Boot Run Configuration의 `JWT_SECRET_KEY` 환경 변수로 설정합니다.
3. 아래 명령으로 테스트 DB를 초기화합니다. 이 명령은 지정된 5개 테이블의 기존 데이터를 제거하므로 운영·개발 공용 DB에서는 실행하면 안 됩니다.

```powershell
.\src\performance-tests\performance-tests\test-data\prepare-db.ps1 -ResetPerformanceDatabase
```

4. 애플리케이션을 시작한 후 사전 검사를 실행합니다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 .\src\performance-tests\performance-tests\k6\preflight.js
```

## 개별 실행

```powershell
k6 run -e BASE_URL=http://localhost:8080 .\src\performance-tests\performance-tests\k6\concurrency\01-oversell-hotspot.js
```

응답시간 기준을 바꾸려면 `P95_MS`를 전달합니다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e P95_MS=5000 .\src\performance-tests\performance-tests\k6\concurrency\02-single-row-lock-capacity.js
```

## 전체 실행

```powershell
.\src\performance-tests\performance-tests\scripts\run-concurrency-suite.ps1 -BaseUrl http://localhost:8080 -P95Milliseconds 3000
```

전체 실행에서는 1~5번 시나리오 사이의 DB 상태를 그대로 이어갑니다. 중간에는 초기화하지 않으며,
5번 시나리오가 끝나면 성공·실패 여부와 관계없이 `finally`에서 성능테스트용 Redis와 PostgreSQL을
생성 데이터의 기준 상태로 한 번만 초기화합니다. 기본 대상은 `gudit-performance-redis`와
`gudit-performance-postgres`이므로 운영·공용 컨테이너 이름을 전달하면 안 됩니다.

`04-duplicate-purchase-race`와 `05-cancel-race`는 현재 코드에서 동시성 결함을 찾기 위한 무결성 테스트입니다. 임계값 실패는 스크립트 오류가 아니라 중복 구매 생성 또는 중복 재고 복구가 발생했다는 신호일 수 있습니다.
