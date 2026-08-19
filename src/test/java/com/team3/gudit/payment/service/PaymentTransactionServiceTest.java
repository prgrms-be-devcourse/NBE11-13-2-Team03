package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PurchaseRepository purchaseRepository;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;

    @Test
    @DisplayName("결제를 시작하면 잠근 구매 상태와 금액을 검증하고 IN_PROGRESS로 변경한다")
    void startPayment() {
        // given
        String orderId = "GUDIT_test-order-id";
        String paymentKey = "payment-key";
        int amount = 15_000;
        Long purchaseId = 100L;

        Purchase purchase = mock(Purchase.class);
        Payment payment = Payment.create(
                purchase,
                amount
        );

        given(purchase.getId())
                .willReturn(purchaseId);

        given(purchase.getPurchasePrice())
                .willReturn(amount);

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase));

        // when
        paymentTransactionService.startPayment(
                orderId,
                paymentKey,
                amount
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);

        assertThat(payment.getPaymentKey())
                .isEqualTo(paymentKey);

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);
    }

    @Test
    @DisplayName("결제 금액과 요청 금액이 다르면 예외가 발생한다")
    void startPaymentAmountMismatch() {
        // given
        String orderId = "GUDIT_test-order-id";

        Purchase purchase = mock(Purchase.class);
        Payment payment = Payment.create(
                purchase,
                15000
        );

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.startPayment(
                        orderId,
                        "payment-key",
                        20000
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            );
                });
    }

    @Test
    @DisplayName("구매 금액과 요청 금액이 다르면 예외가 발생한다")
    void startPaymentPurchaseAmountMismatch() {
        // given
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        Purchase purchase = mock(Purchase.class);
        Payment payment = Payment.create(
                purchase,
                amount
        );

        given(purchase.getPurchasePrice())
                .willReturn(20000);

        given(paymentRepository.findByOrderId(orderId))
                .willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.startPayment(
                        orderId,
                        "payment-key",
                        amount
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            );
                });
    }

    @Test
    @DisplayName("결제 승인 응답이 정상이라면 잠근 구매와 결제를 완료한다")
    void completePayment() {
        // given
        Long purchaseId = 100L;

        Purchase purchase = mock(Purchase.class);

        Payment payment = Payment.create(
                purchase,
                15_000
        );
        payment.start("payment-key");

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(purchase.getId())
                .willReturn(purchaseId);

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(response.orderId())
                .willReturn(payment.getOrderId());

        given(response.totalAmount())
                .willReturn(15_000);

        given(response.approvedAt())
                .willReturn(
                        OffsetDateTime.parse(
                                "2026-08-14T11:00:00+09:00"
                        )
                );

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        ))
                .willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase));

        // when
        paymentTransactionService.completePayment(
                payment.getOrderId(),
                response
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.DONE);

        assertThat(payment.getApprovedAt())
                .isEqualTo(
                        OffsetDateTime.parse(
                                "2026-08-14T11:00:00+09:00"
                        ).toLocalDateTime()
                );

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);

        verify(purchase)
                .complete();
    }

    @Test
    @DisplayName("Toss 승인 응답의 orderId가 다르면 예외가 발생한다")
    void completePaymentOrderIdMismatch() {
        // given
        Purchase purchase = mock(Purchase.class);

        Payment payment = Payment.create(
                purchase,
                15000
        );

        payment.start("payment-key");

        TossPaymentResponse response = mock(TossPaymentResponse.class);

        given(response.orderId())
                .willReturn("GUDIT_other-order-id");

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        ))
                .willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.completePayment(
                        payment.getOrderId(),
                        response
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_ORDER_ID_MISMATCH
                            );
                });
    }

    @Test
    @DisplayName("Toss 승인 응답 금액이 결제 금액과 다르면 예외가 발생한다")
    void completePaymentAmountMismatch() {
        // given
        Purchase purchase = mock(Purchase.class);

        Payment payment = Payment.create(
                purchase,
                15000
        );

        payment.start("payment-key");

        TossPaymentResponse response = mock(TossPaymentResponse.class);

        given(response.orderId())
                .willReturn(payment.getOrderId());

        given(response.totalAmount())
                .willReturn(20000);

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        ))
                .willReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.completePayment(
                        payment.getOrderId(),
                        response
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
                            );
                });
    }

    @Test
    @DisplayName("결제 실패 시 잠근 구매가 PENDING_PAYMENT이면 재고를 복구하고 취소한다")
    void failPayment() {
        // given
        Long purchaseId = 100L;

        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        User user = mock(User.class);

        Payment payment = Payment.create(
                purchase,
                15_000
        );
        payment.start("payment-key");

        given(purchase.getId())
                .willReturn(purchaseId);

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(purchase.getSale())
                .willReturn(sale);

        given(purchase.getUser())
                .willReturn(user);

        given(purchase.getQuantity())
                .willReturn(1);

        given(sale.getId())
                .willReturn(10L);

        given(user.getId())
                .willReturn(1L);

        given(paymentRepository.findByOrderId(
                payment.getOrderId()
        ))
                .willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase));

        // when
        paymentTransactionService.failPayment(
                payment.getOrderId()
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);

        verify(inventoryService)
                .restoreStock(10L, 1L, 1);

        verify(purchase)
                .cancel();
    }

    @Test
    @DisplayName("승인 후 처리 실패 보상 시 잠근 구매의 재고를 복구하고 취소한다")
    void compensateApprovalFailure() {
        // given
        Long purchaseId = 100L;

        Purchase purchase = mock(Purchase.class);
        Sale sale = mock(Sale.class);
        User user = mock(User.class);

        Payment payment = Payment.create(
                purchase,
                15_000
        );
        payment.start("payment-key");

        given(purchase.getId())
                .willReturn(purchaseId);

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PENDING_PAYMENT);

        given(purchase.getSale())
                .willReturn(sale);

        given(purchase.getUser())
                .willReturn(user);

        given(purchase.getQuantity())
                .willReturn(1);

        given(sale.getId())
                .willReturn(10L);

        given(user.getId())
                .willReturn(1L);

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment));

        given(purchaseRepository.findByIdWithLock(purchaseId))
                .willReturn(Optional.of(purchase));

        // when
        paymentTransactionService.compensateApprovalFailure(
                "payment-key"
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        assertThat(payment.getCanceledAt())
                .isNotNull();

        verify(purchaseRepository)
                .findByIdWithLock(purchaseId);

        verify(inventoryService)
                .restoreStock(10L, 1L, 1);

        verify(purchase)
                .cancel();
    }

    @Test
    @DisplayName("완료된 결제를 취소하면 CANCELED 상태로 변경한다")
    void completeCancel() {
        // given
        Purchase purchase = mock(Purchase.class);

        Payment payment = Payment.create(
                purchase,
                15000
        );

        payment.start("payment-key");
        payment.complete(
                OffsetDateTime.parse(
                        "2026-08-14T11:00:00+09:00"
                ).toLocalDateTime()
        );

        given(paymentRepository.findByPaymentKey("payment-key"))
                .willReturn(Optional.of(payment));

        // when
        paymentTransactionService.completeCancel(
                "payment-key"
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        assertThat(payment.getCanceledAt())
                .isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 결제를 조회하면 예외가 발생한다")
    void getPaymentByOrderIdNotFound() {
        // given
        given(paymentRepository.findByOrderId("unknown-order-id"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.getPaymentByOrderId(
                        "unknown-order-id"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            );
                });
    }

    @Test
    @DisplayName("존재하지 않는 paymentKey로 결제를 조회하면 예외가 발생한다")
    void getPaymentByPaymentKeyNotFound() {
        // given
        given(paymentRepository.findByPaymentKey("unknown-payment-key"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> paymentTransactionService.getPaymentByPaymentKey(
                        "unknown-payment-key"
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            );
                });
    }
}