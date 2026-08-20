package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.client.TossPaymentClient;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.dto.TossPaymentWebhookRequest;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private PaymentWebhookService paymentWebhookService;

    @Test
    @DisplayName("DONE Webhook을 수신하면 Toss 결제를 재조회하고 완료 상태를 보정한다")
    void handleDone() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("DONE");

        TossPaymentResponse actualPayment =
                createPaymentResponse("DONE");

        Payment payment = org.mockito.Mockito.mock(Payment.class);

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        given(paymentTransactionService.getPaymentByOrderId(
                "GUDIT_test-order-id"
        ))
                .willReturn(payment);

        given(payment.getStatus())
                .willReturn(PaymentStatus.READY);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(tossPaymentClient)
                .getPayment("payment-key");

        verify(paymentTransactionService)
                .reconcileDone(actualPayment);

        verify(paymentService, never())
                .cancelPayment("payment-key");
    }

    @Test
    @DisplayName("이미 취소된 결제에 DONE Webhook이 수신되면 Toss 결제를 보상 취소한다")
    void handleDoneWhenPaymentCanceled() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("DONE");

        TossPaymentResponse actualPayment =
                createPaymentResponse("DONE");

        Payment payment = org.mockito.Mockito.mock(Payment.class);

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        given(paymentTransactionService.getPaymentByOrderId(
                "GUDIT_test-order-id"
        ))
                .willReturn(payment);

        given(payment.getStatus())
                .willReturn(PaymentStatus.CANCELED);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(paymentService)
                .cancelPayment("payment-key");

        verify(paymentTransactionService, never())
                .reconcileDone(actualPayment);
    }

    @Test
    @DisplayName("CANCELED Webhook을 수신하면 취소 상태를 보정한다")
    void handleCanceled() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("CANCELED");

        TossPaymentResponse actualPayment =
                createPaymentResponse("CANCELED");

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(paymentTransactionService)
                .reconcileCanceled(actualPayment);
    }

    @Test
    @DisplayName("ABORTED Webhook을 수신하면 실패 상태를 보정한다")
    void handleAborted() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("ABORTED");

        TossPaymentResponse actualPayment =
                createPaymentResponse("ABORTED");

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(paymentTransactionService)
                .reconcileAborted(actualPayment);
    }

    @Test
    @DisplayName("EXPIRED Webhook을 수신하면 만료 상태를 보정한다")
    void handleExpired() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("EXPIRED");

        TossPaymentResponse actualPayment =
                createPaymentResponse("EXPIRED");

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(paymentTransactionService)
                .reconcileExpired(actualPayment);
    }

    @Test
    @DisplayName("중간 결제 상태 Webhook은 상태를 보정하지 않는다")
    void handleInProgress() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("IN_PROGRESS");

        TossPaymentResponse actualPayment =
                createPaymentResponse("IN_PROGRESS");

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when
        paymentWebhookService.handle(request);

        // then
        verify(paymentTransactionService, never())
                .reconcileDone(actualPayment);

        verify(paymentTransactionService, never())
                .reconcileCanceled(actualPayment);

        verify(paymentTransactionService, never())
                .reconcileAborted(actualPayment);

        verify(paymentTransactionService, never())
                .reconcileExpired(actualPayment);
    }

    @Test
    @DisplayName("Webhook paymentKey와 Toss 재조회 결과가 다르면 예외가 발생한다")
    void handlePaymentKeyMismatch() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("DONE");

        TossPaymentResponse actualPayment =
                new TossPaymentResponse(
                        "other-payment-key",
                        "GUDIT_test-order-id",
                        "DONE",
                        15_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                );

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when & then
        assertThatThrownBy(
                () -> paymentWebhookService.handle(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            );
                });
    }

    @Test
    @DisplayName("Webhook orderId와 Toss 재조회 결과가 다르면 예외가 발생한다")
    void handleOrderIdMismatch() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("DONE");

        TossPaymentResponse actualPayment =
                new TossPaymentResponse(
                        "payment-key",
                        "GUDIT_other-order-id",
                        "DONE",
                        15_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                );

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when & then
        assertThatThrownBy(
                () -> paymentWebhookService.handle(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            );
                });
    }

    @Test
    @DisplayName("Webhook 결제 금액과 Toss 재조회 결과가 다르면 예외가 발생한다")
    void handleAmountMismatch() {
        // given
        TossPaymentWebhookRequest request =
                createWebhookRequest("DONE");

        TossPaymentResponse actualPayment =
                new TossPaymentResponse(
                        "payment-key",
                        "GUDIT_test-order-id",
                        "DONE",
                        20_000,
                        OffsetDateTime.parse(
                                "2026-08-20T17:00:00+09:00"
                        )
                );

        given(tossPaymentClient.getPayment("payment-key"))
                .willReturn(actualPayment);

        // when & then
        assertThatThrownBy(
                () -> paymentWebhookService.handle(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode
                                            .PAYMENT_WEBHOOK_VALIDATION_FAILED
                            );
                });
    }

    @Test
    @DisplayName("PAYMENT_STATUS_CHANGED가 아닌 Webhook은 처리하지 않는다")
    void ignoreOtherEventType() {
        // given
        TossPaymentWebhookRequest request =
                new TossPaymentWebhookRequest(
                        "OTHER_EVENT",
                        OffsetDateTime.now(),
                        createPaymentResponse("DONE")
                );

        // when
        paymentWebhookService.handle(request);

        // then
        verify(tossPaymentClient, never())
                .getPayment("payment-key");
    }

    private TossPaymentWebhookRequest createWebhookRequest(
            String status
    ) {
        return new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                OffsetDateTime.parse(
                        "2026-08-20T17:00:00+09:00"
                ),
                createPaymentResponse(status)
        );
    }

    private TossPaymentResponse createPaymentResponse(
            String status
    ) {
        return new TossPaymentResponse(
                "payment-key",
                "GUDIT_test-order-id",
                status,
                15_000,
                OffsetDateTime.parse(
                        "2026-08-20T17:00:00+09:00"
                )
        );
    }
}