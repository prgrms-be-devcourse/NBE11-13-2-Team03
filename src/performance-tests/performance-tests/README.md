# Gudit 성능테스트

Spring 애플리케이션의 구매 동시성 및 지속 부하를 검증하는 독립형 k6 테스트 모음이다.

## 디렉터리

- `docs`: 테스트 계획과 합격 기준
- `test-data`: JSON 및 PostgreSQL 시드 생성·적재
- `k6/lib`: 인증, HTTP, 메트릭, 워크로드, 검증 공통 모듈
- `k6/concurrency`: 재고·중복 구매·취소 동시성 테스트
- `k6/load`: 워밍업, 기준, 스트레스, 스파이크, 소크, 혼합 부하
- `scripts`: 전체 실행, 모니터링, 결과 비교
- `config`: 로컬 환경변수 예시
- `results`: 실행 결과 저장 위치

## 준비

1. 이 폴더를 Spring 프로젝트 루트에 복사한다.
2. `test-data/generated/performance-test-data.json`의 `metadata.requiredEnvironment.JWT_SECRET_KEY` 값을 IntelliJ Spring Boot Run Configuration에 설정한다.
3. PostgreSQL과 Spring 애플리케이션을 테스트 전용 환경에서 실행한다.
4. 테스트 DB를 초기화한다.

```powershell
.\performance-tests\test-data\prepare-db.ps1 -ResetPerformanceDatabase
```

5. 사전조건을 확인한다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 .\performance-tests\k6\preflight.js
```

## 실행

동시성 테스트 전체 실행:

```powershell
.\performance-tests\scripts\run-concurrency-suite.ps1 -BaseUrl http://localhost:8080
```

부하테스트 전체 실행:

```powershell
.\performance-tests\scripts\run-load-suite.ps1 -BaseUrl http://localhost:8080
```

30분 소크 테스트 포함:

```powershell
.\performance-tests\scripts\run-load-suite.ps1 -BaseUrl http://localhost:8080 -IncludeSoak
```

상세 내용은 `docs/concurrency-test-plan.md`와 `docs/load-test-plan.md`를 참고한다.

## 주의

- 테스트 데이터와 JWT 키는 격리된 성능테스트 환경에서만 사용한다.
- `prepare-db.ps1`은 지정한 5개 테이블을 초기화한다.
- 혼합 구매 및 동시성 시나리오 실행 후에는 DB 데이터를 다시 적재해야 재실행할 수 있다.
- 운영 DB나 공용 개발 DB에서는 데이터 초기화 스크립트를 실행하지 않는다.
