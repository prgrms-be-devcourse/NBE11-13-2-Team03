package com.team3.gudit.purchase.scheduler;

import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.purchase.service.PurchaseTimeoutService;
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
class PurchaseTimeoutSchedulerTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PurchaseTimeoutService purchaseTimeoutService;

    private PurchaseTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PurchaseTimeoutScheduler(
                purchaseRepository,
                purchaseTimeoutService
        );
    }

    @Test
    @DisplayName("timeout 대상 Purchase ID를 건별 처리 서비스에 전달한다")
    void delegatesExpiredPurchases() {
        // given
        Purchase firstPurchase = mock(Purchase.class);
        Purchase secondPurchase = mock(Purchase.class);

        given(firstPurchase.getId())
                .willReturn(100L);

        given(secondPurchase.getId())
                .willReturn(200L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(
                        List.of(
                                firstPurchase,
                                secondPurchase
                        )
                );

        given(purchaseTimeoutService
                .cancelExpiredPurchase(100L))
                .willReturn(true);

        given(purchaseTimeoutService
                .cancelExpiredPurchase(200L))
                .willReturn(true);

        // when
        scheduler.cancelExpiredPurchases();

        // then
        verify(purchaseTimeoutService)
                .cancelExpiredPurchase(100L);

        verify(purchaseTimeoutService)
                .cancelExpiredPurchase(200L);
    }

    @Test
    @DisplayName("한 건의 timeout 처리에서 예외가 발생해도 다음 대상을 계속 처리한다")
    void continuesAfterIndividualFailure() {
        // given
        Purchase failedPurchase = mock(Purchase.class);
        Purchase nextPurchase = mock(Purchase.class);

        given(failedPurchase.getId())
                .willReturn(100L);

        given(nextPurchase.getId())
                .willReturn(200L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(
                        List.of(
                                failedPurchase,
                                nextPurchase
                        )
                );

        given(purchaseTimeoutService
                .cancelExpiredPurchase(100L))
                .willThrow(
                        new RuntimeException(
                                "Redis 재고 복구 실패"
                        )
                );

        given(purchaseTimeoutService
                .cancelExpiredPurchase(200L))
                .willReturn(true);

        // when
        scheduler.cancelExpiredPurchases();

        // then
        verify(purchaseTimeoutService)
                .cancelExpiredPurchase(100L);

        // 첫 번째 건이 실패해도 다음 건을 계속 처리해야 한다.
        verify(purchaseTimeoutService)
                .cancelExpiredPurchase(200L);
    }

    @Test
    @DisplayName("timeout 대상이 없으면 건별 처리 서비스를 호출하지 않는다")
    void noExpiredPurchase() {
        // given
        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(List.of());

        // when
        scheduler.cancelExpiredPurchases();

        // then
        verifyNoInteractions(purchaseTimeoutService);
    }
}