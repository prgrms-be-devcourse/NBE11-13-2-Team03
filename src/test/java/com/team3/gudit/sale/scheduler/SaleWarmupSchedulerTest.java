package com.team3.gudit.sale.scheduler;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.sale.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleWarmupSchedulerTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private SaleService saleService;

    private SaleWarmupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SaleWarmupScheduler(
                saleRepository,
                inventoryService,
                saleService
        );
    }

    @Test
    @DisplayName("판매 시작 10분 이내의 READY 판매를 Warm-up한다")
    void autoWarmupSales() {
        // given
        Sale sale = mock(Sale.class);

        given(sale.getId()).willReturn(1L);

        given(saleRepository.findByStatusAndStartAtBetween(
                eq(SaleStatus.READY),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(List.of(sale));

        // when
        scheduler.autoWarmupSales();

        // then
        verify(saleService).warmupSaleInfo(1L);
        verify(saleService, never()).startSale(anyLong());
    }

    @Test
    @DisplayName("한 판매의 Warm-up 실패가 다음 판매의 Warm-up을 막지 않는다")
    void autoWarmupContinuesAfterFailure() {
        // given
        Sale firstSale = mock(Sale.class);
        Sale secondSale = mock(Sale.class);

        given(firstSale.getId()).willReturn(1L);
        given(secondSale.getId()).willReturn(2L);

        given(saleRepository.findByStatusAndStartAtBetween(
                eq(SaleStatus.READY),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(List.of(firstSale, secondSale));

        willThrow(new RuntimeException("Redis 오류"))
                .given(saleService)
                .warmupSaleInfo(1L);

        // when
        scheduler.autoWarmupSales();

        // then
        verify(saleService).warmupSaleInfo(1L);
        verify(saleService).warmupSaleInfo(2L);
    }

    @Test
    @DisplayName("판매 시작 대상은 Warm-up 후 ON_SALE 전환을 수행한다")
    void startSales() {
        // given
        Sale sale = mock(Sale.class);

        given(sale.getId()).willReturn(1L);

        given(saleRepository.findByStatusAndStartAtLessThanEqual(
                eq(SaleStatus.READY),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(List.of(sale));

        // when
        scheduler.startSales();

        // then
        InOrder inOrder = inOrder(saleService);

        inOrder.verify(saleService)
                .warmupSaleInfo(1L);
        inOrder.verify(saleService)
                .startSale(1L);
    }

    @Test
    @DisplayName("한 판매의 시작 실패가 다음 판매 시작을 막지 않는다")
    void startSalesContinuesAfterFailure() {
        // given
        Sale firstSale = mock(Sale.class);
        Sale secondSale = mock(Sale.class);

        given(firstSale.getId()).willReturn(1L);
        given(secondSale.getId()).willReturn(2L);

        given(saleRepository.findByStatusAndStartAtLessThanEqual(
                eq(SaleStatus.READY),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).willReturn(List.of(firstSale, secondSale));

        willThrow(new RuntimeException("판매 시작 실패"))
                .given(saleService)
                .startSale(1L);

        // when
        scheduler.startSales();

        // then
        verify(saleService).startSale(1L);
        verify(saleService).startSale(2L);
    }

    @Test
    @DisplayName("종료 시간이 지난 ON_SALE 판매를 종료 처리한다")
    void endSales() {
        // given
        Sale sale = mock(Sale.class);

        given(sale.getId()).willReturn(1L);

        given(saleRepository.findByStatusAndEndAtLessThanEqual(
                eq(SaleStatus.ON_SALE),
                any(LocalDateTime.class)
        )).willReturn(List.of(sale));

        // when
        scheduler.endSales();

        // then
        verify(saleService).endSale(1L);
    }
}