package com.team3.gudit.purchase.scheduler;

import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseTimeoutSchedulerTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentRepository paymentRepository;

    private PurchaseTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PurchaseTimeoutScheduler(
                purchaseRepository,
                inventoryService,
                paymentRepository
        );
    }

    @Test
    @DisplayName("timeout 대상의 READY Payment를 취소하고 잠금 조회한 Purchase 재고를 복구한다")
    void cancelExpiredReadyPayment() {
        // given
        Purchase listedPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);
        Sale lockedSale = mock(Sale.class);
        User lockedUser = mock(User.class);

        given(listedPurchase.getId())
                .willReturn(100L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(List.of(listedPurchase));

        given(purchaseRepository.findByIdWithLock(100L))
                .willReturn(Optional.of(lockedPurchase));

        given(lockedPurchase.getId())
                .willReturn(100L);
        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);
        given(lockedPurchase.getSale())
                .willReturn(lockedSale);
        given(lockedPurchase.getUser())
                .willReturn(lockedUser);
        given(lockedPurchase.getQuantity())
                .willReturn(1);

        given(lockedSale.getId())
                .willReturn(10L);
        given(lockedUser.getId())
                .willReturn(1L);

        Payment payment = Payment.create(
                lockedPurchase,
                15_000
        );

        given(paymentRepository.findByPurchaseId(100L))
                .willReturn(Optional.of(payment));

        // when
        scheduler.cancelExpiredPurchases();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        verify(purchaseRepository)
                .findByIdWithLock(100L);

        verify(inventoryService).restoreStock(
                10L,
                1L,
                1
        );

        // 반드시 잠금 조회한 Purchase를 취소해야 한다.
        verify(lockedPurchase).cancel();
        verify(listedPurchase, never()).cancel();
    }

    @Test
    @DisplayName("잠금 대기 중 Purchase 상태가 변경됐으면 timeout 재고 복구를 하지 않는다")
    void skipWhenLockedPurchaseAlreadyProcessed() {
        // given
        Purchase listedPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(listedPurchase.getId())
                .willReturn(100L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(List.of(listedPurchase));

        given(purchaseRepository.findByIdWithLock(100L))
                .willReturn(Optional.of(lockedPurchase));

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        // when
        scheduler.cancelExpiredPurchases();

        // then
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName("Payment가 IN_PROGRESS이면 timeout에서 제외하고 재고를 복구하지 않는다")
    void skipInProgressPayment() {
        // given
        Purchase listedPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(listedPurchase.getId())
                .willReturn(100L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(List.of(listedPurchase));

        given(purchaseRepository.findByIdWithLock(100L))
                .willReturn(Optional.of(lockedPurchase));

        given(lockedPurchase.getId())
                .willReturn(100L);
        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        Payment payment = Payment.create(
                lockedPurchase,
                15_000
        );
        payment.start("payment-key");

        given(paymentRepository.findByPurchaseId(100L))
                .willReturn(Optional.of(payment));

        // when
        scheduler.cancelExpiredPurchases();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);

        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName("Payment 정보가 없으면 timeout 재고 복구를 하지 않는다")
    void skipWhenPaymentMissing() {
        // given
        Purchase listedPurchase = mock(Purchase.class);
        Purchase lockedPurchase = mock(Purchase.class);

        given(listedPurchase.getId())
                .willReturn(100L);

        given(purchaseRepository
                .findAllByStatusAndCreatedAtBefore(
                        eq(PurchaseStatus.PENDING_PAYMENT),
                        any(LocalDateTime.class)
                ))
                .willReturn(List.of(listedPurchase));

        given(purchaseRepository.findByIdWithLock(100L))
                .willReturn(Optional.of(lockedPurchase));

        given(lockedPurchase.getId())
                .willReturn(100L);
        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(paymentRepository.findByPurchaseId(100L))
                .willReturn(Optional.empty());

        // when
        scheduler.cancelExpiredPurchases();

        // then
        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName("timeout 대상이 없으면 잠금 조회와 재고 복구를 실행하지 않는다")
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
        verify(purchaseRepository, never())
                .findByIdWithLock(anyLong());

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(inventoryService);
    }
}