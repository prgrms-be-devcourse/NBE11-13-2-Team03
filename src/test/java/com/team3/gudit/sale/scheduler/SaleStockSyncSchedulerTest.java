package com.team3.gudit.sale.scheduler;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleStockSyncSchedulerTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleService saleService;

    private SaleStockSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SaleStockSyncScheduler(
                saleRepository,
                saleService
        );
    }

    @Test
    @DisplayName("종료 후 1일이 지나고 최종 동기화되지 않은 CLOSED 판매를 동기화한다")
    void syncFinalRemainingStocks() {
        // given
        Sale sale = mock(Sale.class);

        given(sale.getId()).willReturn(1L);

        given(
                saleRepository
                        .findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                                eq(SaleStatus.CLOSED),
                                any(LocalDateTime.class)
                        )
        ).willReturn(List.of(sale));

        given(saleService.syncFinalRemainingStock(1L))
                .willReturn(true);

        // when
        scheduler.syncFinalRemainingStocks();

        // then
        verify(saleRepository)
                .findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                        eq(SaleStatus.CLOSED),
                        any(LocalDateTime.class)
                );

        verify(saleService)
                .syncFinalRemainingStock(1L);
    }

    @Test
    @DisplayName("한 판매의 최종 동기화 실패가 다음 판매 처리를 막지 않는다")
    void syncFinalRemainingStocksContinuesAfterFailure() {
        // given
        Sale firstSale = mock(Sale.class);
        Sale secondSale = mock(Sale.class);

        given(firstSale.getId()).willReturn(1L);
        given(secondSale.getId()).willReturn(2L);

        given(
                saleRepository
                        .findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                                eq(SaleStatus.CLOSED),
                                any(LocalDateTime.class)
                        )
        ).willReturn(List.of(firstSale, secondSale));

        given(saleService.syncFinalRemainingStock(1L))
                .willThrow(new RuntimeException("동기화 실패"));

        given(saleService.syncFinalRemainingStock(2L))
                .willReturn(true);

        // when
        scheduler.syncFinalRemainingStocks();

        // then
        verify(saleService)
                .syncFinalRemainingStock(1L);
        verify(saleService)
                .syncFinalRemainingStock(2L);
    }

    @Test
    @DisplayName("최종 재고 동기화 대상이 없으면 서비스를 호출하지 않는다")
    void syncFinalRemainingStocksWithoutTarget() {
        // given
        given(
                saleRepository
                        .findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                                eq(SaleStatus.CLOSED),
                                any(LocalDateTime.class)
                        )
        ).willReturn(List.of());

        // when
        scheduler.syncFinalRemainingStocks();

        // then
        verifyNoInteractions(saleService);
    }
}