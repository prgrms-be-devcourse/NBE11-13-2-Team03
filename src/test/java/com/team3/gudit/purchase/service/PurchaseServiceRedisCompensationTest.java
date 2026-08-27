package com.team3.gudit.purchase.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceRedisCompensationTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentService paymentService;

    private PurchaseService purchaseService;

    private Long userId;
    private Long saleId;
    private Long purchaseId;

    @BeforeEach
    void setUp() {
        purchaseService = new PurchaseService(
                purchaseRepository,
                userRepository,
                saleRepository,
                inventoryService,
                paymentService
        );

        userId = 1L;
        saleId = 10L;
        purchaseId = 100L;
    }

    @Test
    @DisplayName("PENDING_PAYMENT 구매 취소 시 잠금 조회 후 READY 결제를 취소하고 Redis 재고를 복구한다")
    void cancelPendingPayment() {
        // given
        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        )).willReturn(Optional.of(purchase));

        // 취소 전에는 PENDING_PAYMENT,
        // 취소 응답을 만들 때는 CANCELED 상태를 반환
        given(purchase.getStatus())
                .willReturn(
                        PurchaseStatus.PENDING_PAYMENT,
                        PurchaseStatus.PENDING_PAYMENT,
                        PurchaseStatus.CANCELED
                );

        given(purchase.getId())
                .willReturn(purchaseId);
        given(purchase.getSale())
                .willReturn(sale);
        given(purchase.getQuantity())
                .willReturn(1);
        given(purchase.getCanceledAt())
                .willReturn(LocalDateTime.now());

        given(sale.getId())
                .willReturn(saleId);
        given(sale.getEndAt())
                .willReturn(LocalDateTime.now().plusHours(1));

        Payment payment = Payment.create(
                purchase,
                15_000
        );

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        )).willReturn(payment);

        // when
        PurchaseCancelResponse response =
                purchaseService.cancel(
                        userId,
                        purchaseId
                );

        // then
        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(purchaseRepository)
                .findByIdAndUserIdWithLock(
                        purchaseId,
                        userId
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        verify(inventoryService)
                .restoreStock(
                        saleId,
                        userId,
                        1
                );

        verify(purchase).cancel();

        assertThat(response.status())
                .isEqualTo(PurchaseStatus.CANCELED);
    }

    @Test
    @DisplayName("Payment가 IN_PROGRESS이면 사용자 취소와 Redis 재고 복구를 차단한다")
    void cancelPendingPaymentWhenPaymentInProgress() {
        // given
        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        )).willReturn(Optional.of(purchase));

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(purchase.getSale())
                .willReturn(sale);

        given(sale.getEndAt())
                .willReturn(LocalDateTime.now().plusHours(1));

        Payment payment = Payment.create(
                purchase,
                15_000
        );
        payment.start("payment-key");

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        )).willReturn(payment);

        // when & then
        assertThatThrownBy(() ->
                purchaseService.cancel(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                PaymentErrorCode.INVALID_PAYMENT_STATUS
                        )
                );

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);

        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(inventoryService, never())
                .restoreStock(
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(purchase, never()).cancel();
    }

    @Test
    @DisplayName("이미 취소된 Purchase는 다시 취소하거나 Redis 재고를 복구하지 않는다")
    void cancelAlreadyCanceledPurchase() {
        // given
        Purchase purchase = mock(Purchase.class);
        Payment payment = mock(Payment.class);

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        )).willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        )).willReturn(Optional.of(purchase));

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        // when & then
        assertThatThrownBy(() ->
                purchaseService.cancel(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                PurchaseErrorCode
                                        .PURCHASE_ALREADY_CANCELED
                        )
                );

        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(inventoryService, never())
                .restoreStock(
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(purchase, never()).cancel();
    }

    @Test
    @DisplayName("판매 종료 후 1일이 지나면 사용자 취소와 Redis 재고 복구를 차단한다")
    void cancelAfterCancellationDeadline() {
        // given
        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        Payment payment = mock(Payment.class);

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        )).willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        )).willReturn(Optional.of(purchase));

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);
        given(purchase.getSale())
                .willReturn(sale);

        given(sale.getEndAt())
                .willReturn(LocalDateTime.now().minusDays(2));

        // when & then
        assertThatThrownBy(() ->
                purchaseService.cancel(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                PurchaseErrorCode
                                        .PURCHASE_CANCELLATION_PERIOD_EXPIRED
                        )
                );

        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(inventoryService, never())
                .restoreStock(
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(purchase, never()).cancel();
    }

    @Test
    @DisplayName("취소 유예기간 안의 PURCHASED 구매는 결제 취소 후 Redis 재고를 복구한다")
    void cancelPurchasedWithinCancellationPeriod() {
        // given
        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        Payment payment = mock(Payment.class);

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        )).willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        )).willReturn(Optional.of(purchase));

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PURCHASED);
        given(purchase.getId())
                .willReturn(purchaseId);
        given(purchase.getSale())
                .willReturn(sale);
        given(purchase.getQuantity())
                .willReturn(1);

        given(sale.getId())
                .willReturn(saleId);
        given(sale.getEndAt())
                .willReturn(LocalDateTime.now().plusHours(1));

        given(payment.getPaymentKey())
                .willReturn("payment-key");

        // when
        purchaseService.cancel(
                userId,
                purchaseId
        );

        // then
        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(paymentService)
                .cancelCompletedPayment("payment-key");

        verify(inventoryService)
                .restoreStock(
                        saleId,
                        userId,
                        1
                );

        verify(purchase).cancel();
    }
}