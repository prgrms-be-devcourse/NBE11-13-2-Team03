package com.team3.gudit.sale.service;

import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.dto.SaleRedisDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.sale.exception.SaleErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private GoodsRepository goodsRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private SaleServiceImpl saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleServiceImpl(
                saleRepository,
                goodsRepository,
                redisTemplate,
                purchaseRepository
        );
    }

    @Test
    @DisplayName("Warm-up은 판매 상태를 변경하지 않고 Redis stock과 info를 저장한다")
    void warmupSaleInfo() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusDays(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);

        // when
        saleService.warmupSaleInfo(1L);

        // then
        verify(valueOperations).set(
                "sale:1:stock",
                "100"
        );

        Map<String, String> expectedInfo =
                SaleRedisDto.from(sale)
                        .toHashFields();

        verify(hashOperations).putAll(
                "sale:1:info",
                expectedInfo
        );

        // Warm-up은 캐시 적재만 하고 상태를 바꾸지 않는다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.READY);
    }

    @Test
    @DisplayName("Warm-up 시 stock과 info 키에 동일한 endAt + 2일 기준 TTL을 적용한다")
    void warmupSaleInfoAppliesTtl() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusDays(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);

        // when
        saleService.warmupSaleInfo(1L);

        // then
        ArgumentCaptor<Duration> stockTtlCaptor =
                ArgumentCaptor.forClass(Duration.class);
        ArgumentCaptor<Duration> infoTtlCaptor =
                ArgumentCaptor.forClass(Duration.class);

        verify(redisTemplate).expire(
                eq("sale:1:stock"),
                stockTtlCaptor.capture()
        );

        verify(redisTemplate).expire(
                eq("sale:1:info"),
                infoTtlCaptor.capture()
        );

        Duration stockTtl =
                stockTtlCaptor.getValue();
        Duration infoTtl =
                infoTtlCaptor.getValue();

        assertThat(stockTtl.isPositive()).isTrue();
        assertThat(infoTtl.isPositive()).isTrue();
        assertThat(stockTtl).isEqualTo(infoTtl);
    }

    @Test
    @DisplayName("READY 판매라도 endAt + 2일이 지났으면 Redis 캐시를 즉시 삭제한다")
    void warmupExpiredSaleDeletesCache() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                20,
                LocalDateTime.now().minusDays(3)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);

        // when
        saleService.warmupSaleInfo(1L);

        // then
        verify(redisTemplate).delete(
                List.of(
                        "sale:1:stock",
                        "sale:1:info"
                )
        );

        verify(redisTemplate, never()).expire(
                any(String.class),
                any(Duration.class)
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = SaleStatus.class,
            names = "READY",
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("READY가 아닌 판매는 Warm-up할 수 없고 Redis 데이터를 변경하지 않는다")
    void warmupNonReadySaleIsRejected(
            SaleStatus status
    ) {
        // given
        Sale sale = createSale(
                status,
                100,
                LocalDateTime.now().plusDays(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));

        // when & then
        assertThatThrownBy(
                () -> saleService.warmupSaleInfo(1L)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode
                                        .CANNOT_WARMUP_NON_READY_SALE
                        )
                );

        // 상태 검증에서 차단되므로 Redis stock/info를 조회하거나
        // 덮어쓰는 동작이 없어야 한다.
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("판매 수정 시 전체 정보를 변경하고 Redis 캐시와 TTL을 다시 설정한다")
    void updateSaleRewarmsCache() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusDays(1)
        );

        LocalDateTime updatedStartAt =
                LocalDateTime.now().plusDays(2);
        LocalDateTime updatedEndAt =
                updatedStartAt.plusHours(2);

        SaleUpdateRequestDto request =
                new SaleUpdateRequestDto(
                        200,
                        5,
                        updatedStartAt,
                        updatedEndAt
                );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);

        // when
        SaleDetailResponseDto response =
                saleService.updateSale(1L, request);

        // then
        assertThat(sale.getInitialStock())
                .isEqualTo(200);
        assertThat(sale.getRemainingStock())
                .isEqualTo(200);
        assertThat(sale.getMaxPurchaseQuantity())
                .isEqualTo(5);
        assertThat(sale.getStartAt())
                .isEqualTo(updatedStartAt);
        assertThat(sale.getEndAt())
                .isEqualTo(updatedEndAt);

        assertThat(response.remainingStock())
                .isEqualTo(200);

        verify(valueOperations).set(
                "sale:1:stock",
                "200"
        );

        verify(redisTemplate).expire(
                eq("sale:1:stock"),
                any(Duration.class)
        );
        verify(redisTemplate).expire(
                eq("sale:1:info"),
                any(Duration.class)
        );

        // updateSale() 조회와 warmupSaleInfo() 조회
        verify(saleRepository, times(2))
                .findById(1L);
    }

    @Test
    @DisplayName("판매 삭제 시 Sale을 DELETED로 변경하고 Redis stock과 info 키를 삭제한다")
    void deleteSaleDeletesRedisKeys() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusDays(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));

        // when
        saleService.deleteSale(1L);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.DELETED);

        verify(redisTemplate).delete(
                List.of(
                        "sale:1:stock",
                        "sale:1:info"
                )
        );
    }

    @Test
    @DisplayName("판매 시작 시 RDB와 Redis 상태를 ON_SALE로 변경한다")
    void startSale() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);

        // when
        saleService.startSale(1L);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);

        verify(hashOperations).put(
                "sale:1:info",
                "status",
                SaleStatus.ON_SALE.name()
        );
    }

    @Test
    @DisplayName("판매 종료 시 Redis 구매를 먼저 차단하고 최종 재고를 RDB에 동기화한다")
    void endSale() {
        // given
        Sale sale = createSale(
                SaleStatus.ON_SALE,
                100,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForHash())
                .willReturn(hashOperations);
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn("35");

        // when
        saleService.endSale(1L);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.CLOSED);
        assertThat(sale.getRemainingStock())
                .isEqualTo(35);

        InOrder inOrder = inOrder(
                hashOperations,
                valueOperations
        );

        // Redis 상태를 먼저 CLOSED로 변경
        inOrder.verify(hashOperations).put(
                "sale:1:info",
                "status",
                SaleStatus.CLOSED.name()
        );

        // 신규 구매 차단 후 재고 조회
        inOrder.verify(valueOperations).get(
                "sale:1:stock"
        );
    }

    @Test
    @DisplayName("ON_SALE 판매 상세 조회 시 Redis 실시간 재고를 우선 사용한다")
    void saleDetailUsesRedisStock() {
        // given
        Sale sale = createSale(
                SaleStatus.ON_SALE,
                100,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn("35");

        // when
        SaleDetailResponseDto response =
                saleService.saleDetail(1L);

        // then
        assertThat(response.remainingStock())
                .isEqualTo(35);
        assertThat(response.status())
                .isEqualTo(SaleStatus.ON_SALE);

        // 조회 응답만 Redis 재고를 사용하고 엔티티 값은 변경하지 않는다.
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("ON_SALE 판매의 Redis 재고가 0이면 응답에서만 SOLD_OUT으로 표시한다")
    void saleDetailDisplaysSoldOut() {
        // given
        Sale sale = createSale(
                SaleStatus.ON_SALE,
                100,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn("0");

        // when
        SaleDetailResponseDto response =
                saleService.saleDetail(1L);

        // then
        assertThat(response.remainingStock())
                .isZero();
        assertThat(response.status())
                .isEqualTo(SaleStatus.SOLD_OUT);

        // RDB 엔티티 상태는 ON_SALE을 유지한다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("ON_SALE 판매의 Redis stock 키가 없으면 RDB 재고로 fallback한다")
    void saleDetailFallsBackWhenRedisKeyMissing() {
        // given
        Sale sale = createSale(
                SaleStatus.ON_SALE,
                70,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn(null);

        // when
        SaleDetailResponseDto response =
                saleService.saleDetail(1L);

        // then
        assertThat(response.remainingStock())
                .isEqualTo(70);
        assertThat(response.status())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("Redis stock 값이 숫자가 아니면 RDB 재고로 fallback한다")
    void saleDetailFallsBackWhenRedisValueInvalid() {
        // given
        Sale sale = createSale(
                SaleStatus.ON_SALE,
                70,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn("invalid-stock");

        // when
        SaleDetailResponseDto response =
                saleService.saleDetail(1L);

        // then
        assertThat(response.remainingStock())
                .isEqualTo(70);
        assertThat(response.status())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("READY 판매 조회 시 Redis를 조회하지 않고 RDB 재고를 사용한다")
    void saleDetailUsesRdbStockWhenReady() {
        // given
        Sale sale = createSale(
                SaleStatus.READY,
                100,
                LocalDateTime.now().plusHours(1)
        );

        given(saleRepository.findById(1L))
                .willReturn(Optional.of(sale));

        // when
        SaleDetailResponseDto response =
                saleService.saleDetail(1L);

        // then
        assertThat(response.remainingStock())
                .isEqualTo(100);
        assertThat(response.status())
                .isEqualTo(SaleStatus.READY);

        verify(redisTemplate, never())
                .opsForValue();
    }

    @Test
    @DisplayName("취소 유예기간이 지나고 미결제 구매가 없으면 Redis 최종 재고를 RDB에 동기화한다")
    void syncFinalRemainingStock() {
        // given
        Sale sale = createSale(
                SaleStatus.CLOSED,
                100,
                LocalDateTime.now().minusDays(2)
        );

        given(saleRepository.findByIdWithLock(1L))
                .willReturn(Optional.of(sale));
        given(purchaseRepository.existsBySaleIdAndStatus(
                1L,
                PurchaseStatus.PENDING_PAYMENT
        )).willReturn(false);
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn("42");

        // when
        boolean synced =
                saleService.syncFinalRemainingStock(1L);

        // then
        assertThat(synced).isTrue();
        assertThat(sale.getRemainingStock())
                .isEqualTo(42);
        assertThat(sale.getFinalStockSyncedAt())
                .isNotNull();

        verify(saleRepository)
                .findByIdWithLock(1L);
    }

    @Test
    @DisplayName("취소 유예기간이 지나지 않았으면 최종 재고 동기화를 보류한다")
    void syncFinalRemainingStockBeforeDeadline() {
        // given
        Sale sale = createSale(
                SaleStatus.CLOSED,
                100,
                LocalDateTime.now().minusHours(1)
        );

        given(saleRepository.findByIdWithLock(1L))
                .willReturn(Optional.of(sale));

        // when
        boolean synced =
                saleService.syncFinalRemainingStock(1L);

        // then
        assertThat(synced).isFalse();
        assertThat(sale.getFinalStockSyncedAt())
                .isNull();

        verifyNoInteractions(purchaseRepository);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("PENDING_PAYMENT 구매가 남아 있으면 최종 재고 동기화를 보류한다")
    void syncFinalRemainingStockWithPendingPurchase() {
        // given
        Sale sale = createSale(
                SaleStatus.CLOSED,
                100,
                LocalDateTime.now().minusDays(2)
        );

        given(saleRepository.findByIdWithLock(1L))
                .willReturn(Optional.of(sale));
        given(purchaseRepository.existsBySaleIdAndStatus(
                1L,
                PurchaseStatus.PENDING_PAYMENT
        )).willReturn(true);

        // when
        boolean synced =
                saleService.syncFinalRemainingStock(1L);

        // then
        assertThat(synced).isFalse();
        assertThat(sale.getFinalStockSyncedAt())
                .isNull();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("최종 동기화할 Redis stock 키가 없으면 RDB 재고를 변경하지 않는다")
    void syncFinalRemainingStockWithoutRedisKey() {
        // given
        Sale sale = createSale(
                SaleStatus.CLOSED,
                100,
                LocalDateTime.now().minusDays(2)
        );

        given(saleRepository.findByIdWithLock(1L))
                .willReturn(Optional.of(sale));
        given(purchaseRepository.existsBySaleIdAndStatus(
                1L,
                PurchaseStatus.PENDING_PAYMENT
        )).willReturn(false);
        given(redisTemplate.opsForValue())
                .willReturn(valueOperations);
        given(valueOperations.get("sale:1:stock"))
                .willReturn(null);

        // when
        boolean synced =
                saleService.syncFinalRemainingStock(1L);

        // then
        assertThat(synced).isFalse();
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
        assertThat(sale.getFinalStockSyncedAt())
                .isNull();
    }

    private Sale createSale(
            SaleStatus status,
            int remainingStock,
            LocalDateTime endAt
    ) {
        Goods goods = Goods.builder()
                .id(10L)
                .name("테스트 상품")
                .description("테스트 설명")
                .price(10_000)
                .imageUrl("test-image.jpg")
                .status(GoodsStatus.ACTIVE)
                .build();

        return Sale.builder()
                .id(1L)
                .goods(goods)
                .initialStock(100)
                .remainingStock(remainingStock)
                .maxPurchaseQuantity(2)
                .status(status)
                .startAt(endAt.minusHours(1))
                .endAt(endAt)
                .build();
    }
}