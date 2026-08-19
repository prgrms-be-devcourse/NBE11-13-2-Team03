package com.team3.gudit.purchase.service;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseTimeoutServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryService inventoryService;

    private PurchaseTimeoutService purchaseTimeoutService;

    @BeforeEach
    void setUp() {
        purchaseTimeoutService = new PurchaseTimeoutService(
                purchaseRepository,
                paymentRepository,
                inventoryService
        );
    }

    @Test
    @DisplayName(
            "PENDING_PAYMENT Purchase와 READY Payment를 "
                    + "timeout 취소하고 재고를 복구한다"
    )
    void cancelExpiredPurchase() {
        // given
        Long purchaseId = 100L;
        Long saleId = 10L;
        Long userId = 1L;
        int quantity = 2;

        Purchase lockedPurchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        User user = mock(User.class);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(lockedPurchase.getSale())
                .willReturn(sale);

        given(lockedPurchase.getUser())
                .willReturn(user);

        given(lockedPurchase.getQuantity())
                .willReturn(quantity);

        given(sale.getId())
                .willReturn(saleId);

        given(user.getId())
                .willReturn(userId);

        Payment payment = Payment.create(
                lockedPurchase,
                20_000
        );

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.of(payment));

        // when
        boolean canceled =
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId);

        // then
        assertThat(canceled).isTrue();

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        verify(inventoryService).restoreStock(
                saleId,
                userId,
                quantity
        );

        verify(lockedPurchase).cancel();
    }

    @Test
    @DisplayName(
            "Purchase가 이미 처리된 상태이면 "
                    + "timeout 취소와 재고 복구를 하지 않는다"
    )
    void skipAlreadyProcessedPurchase() {
        // given
        Long purchaseId = 100L;

        Purchase lockedPurchase = mock(Purchase.class);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        // when
        boolean canceled =
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId);

        // then
        assertThat(canceled).isFalse();

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName(
            "Payment가 IN_PROGRESS이면 "
                    + "결제 승인 중이므로 timeout 처리에서 제외한다"
    )
    void skipInProgressPayment() {
        // given
        Long purchaseId = 100L;

        Purchase lockedPurchase = mock(Purchase.class);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        Payment payment = Payment.create(
                lockedPurchase,
                20_000
        );

        payment.start("test-payment-key");

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.of(payment));

        // when
        boolean canceled =
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId);

        // then
        assertThat(canceled).isFalse();

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);

        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName(
            "Payment가 없으면 Purchase와 재고를 "
                    + "변경하지 않고 timeout 처리를 보류한다"
    )
    void skipWhenPaymentDoesNotExist() {
        // given
        Long purchaseId = 100L;

        Purchase lockedPurchase = mock(Purchase.class);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.empty());

        // when
        boolean canceled =
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId);

        // then
        assertThat(canceled).isFalse();

        verifyNoInteractions(inventoryService);
        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName(
            "재고 복구에서 예외가 발생하면 예외를 전파하고 "
                    + "Purchase 취소를 실행하지 않는다"
    )
    void propagateRestoreStockFailure() {
        // given
        Long purchaseId = 100L;
        Long saleId = 10L;
        Long userId = 1L;
        int quantity = 2;

        Purchase lockedPurchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        User user = mock(User.class);

        given(lockedPurchase.getId())
                .willReturn(purchaseId);

        given(lockedPurchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(lockedPurchase.getSale())
                .willReturn(sale);

        given(lockedPurchase.getUser())
                .willReturn(user);

        given(lockedPurchase.getQuantity())
                .willReturn(quantity);

        given(sale.getId())
                .willReturn(saleId);

        given(user.getId())
                .willReturn(userId);

        Payment payment = Payment.create(
                lockedPurchase,
                20_000
        );

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(lockedPurchase));

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.of(payment));

        doThrow(new RuntimeException("Redis 재고 복구 실패"))
                .when(inventoryService)
                .restoreStock(
                        saleId,
                        userId,
                        quantity
                );

        // when & then
        assertThatThrownBy(() ->
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId)
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Redis 재고 복구 실패");

        verify(lockedPurchase, never()).cancel();
    }

    @Test
    @DisplayName(
            "Purchase를 찾을 수 없으면 "
                    + "timeout 처리를 하지 않는다"
    )
    void skipWhenPurchaseDoesNotExist() {
        // given
        Long purchaseId = 100L;

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.empty());

        // when
        boolean canceled =
                purchaseTimeoutService
                        .cancelExpiredPurchase(purchaseId);

        // then
        assertThat(canceled).isFalse();

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(inventoryService);
    }
}