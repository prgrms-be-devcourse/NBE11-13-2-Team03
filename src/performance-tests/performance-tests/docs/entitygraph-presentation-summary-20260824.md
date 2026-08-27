# 발표용: Sale 목록 N+1 개선 과정

## 1. 테스트 설명

**문제:** 판매 목록 1회 조회에 Sale SQL 1회와 Goods SQL 106회가 실행됐다.

**가설:** Goods를 한 번에 조회하면 DB 부하와 목록 응답시간이 감소하고, 고부하에서 처리 가능한 요청이 늘어난다.

**조건:** 판매 106개, Docker Spring+k6, 동일 부하 suite 3회, 최대 500 RPS.

**측정 지표:** SQL 호출 수, 목록 p95, dropped iterations, 완료 요청, App/DB CPU, HTTP·업무 오류.

## 2. 개선 전 테스트 결과

- 목록 1회: **SQL 107회**
- 상품 단건 SQL: 회차 평균 **767만 회**
- stress: 목록 p95 **3.49초**, dropped **4,413건**
- spike: 목록 p95 **4.17초**, dropped **8,015건**
- 평균 CPU: App **143%**, DB **70.63%**
- HTTP 실패·Connection Refused: **0건**

**결론:** 요청은 응답하지만 N+1이 CPU와 응답 지연을 키워 목표 RPS를 유지하지 못했다.

## 3. 개선

```java
@EntityGraph(attributePaths = "goods")
List<Sale> findAllByGoods_Status(GoodsStatus goodsStatus);
```

- `Sale.goods`는 `@ManyToOne` 단건 관계라 컬렉션 fetch join의 행 폭증·메모리 페이징 문제가 발생하지 않는다.
- 목록 DTO가 Goods 필드를 항상 사용하므로 필요한 데이터를 미리 읽는 변경이다.
- 단일 목록 SQL은 약 48% 무거워졌지만, 목록+상품 전체 DB 실행시간은 약 78% 감소했다.

## 4. 개선 후 테스트 결과

| 지표 | 개선 전 | 개선 후 | 결론 |
|---|---:|---:|---|
| 목록 1회 SQL | 107회 | 1회 | N+1 제거 |
| baseline 목록 p95 | 64.45ms | 34.34ms | 46.7% 감소 |
| stress 목록 p95 | 3.49초 | 2.77초 | 20.8% 감소 |
| stress dropped | 4,413 | 1,418 | 67.9% 감소 |
| spike 목록 p95 | 4.17초 | 3.37초 | 19.2% 감소 |
| spike dropped | 8,015 | 5,173 | 35.5% 감소 |
| App CPU 평균 | 143% | 90.67% | 36.6% 감소 |
| DB CPU 평균 | 70.63% | 8.20% | 88.4% 감소 |
| HTTP/업무 오류 | 0 | 0 | 정합성 유지 |

**최종 결론:** EntityGraph 적용은 현재 관계 구조에서 안전하며, N+1 제거가 응답시간·CPU·처리량 개선으로 이어졌다. 다만 stress와 spike에서 p95 임계값과 dropped가 남아 500 RPS 안정 처리에는 추가 개선이 필요하다.
