package com.team3.gudit.payment.entity;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.purchase.entity.Purchase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentTest {

    @Test
    @DisplayName("결제 정보를 생성하면 READY 상태로 생성된다")
    void createPayment() {
        // given
        Purchase purchase = mock(Purchase.class);

        // when
        Payment payment = Payment.create(
                purchase,
                15000
        );

        // then
        assertThat(payment.getPurchase()).isEqualTo(purchase);
        assertThat(payment.getAmount()).isEqualTo(15000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getOrderId()).startsWith("GUDIT_");
        assertThat(payment.getPaymentKey()).isNull();
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("READY 상태의 결제를 시작하면 IN_PROGRESS 상태가 되고 paymentKey가 저장된다")
    void startPayment() {
        // given
        Payment payment = createReadyPayment();

        // when
        payment.start("payment-key");

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getPaymentKey())
                .isEqualTo("payment-key");
    }

    @Test
    @DisplayName("READY 상태가 아닌 결제를 시작하면 예외가 발생한다")
    void startPaymentInvalidStatus() {
        // given
        Payment payment = createReadyPayment();
        payment.start("payment-key");

        // when & then
        assertThatThrownBy(
                () -> payment.start("another-payment-key")
        )
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 완료하면 DONE 상태가 된다")
    void completePayment() {
        // given
        Payment payment = createInProgressPayment();
        LocalDateTime approvedAt = LocalDateTime.now();

        // when
        payment.complete(approvedAt);

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getApprovedAt())
                .isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 완료하면 예외가 발생한다")
    void completePaymentInvalidStatus() {
        // given
        Payment payment = createReadyPayment();

        // when & then
        assertThatThrownBy(
                () -> payment.complete(LocalDateTime.now())
        )
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 실패 처리하면 FAILED 상태가 된다")
    void failPayment() {
        // given
        Payment payment = createInProgressPayment();

        // when
        payment.fail();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 실패 처리하면 예외가 발생한다")
    void failPaymentInvalidStatus() {
        // given
        Payment payment = createReadyPayment();

        // when & then
        assertThatThrownBy(payment::fail)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("DONE 상태의 결제를 취소하면 CANCELED 상태가 된다")
    void cancelPayment() {
        // given
        Payment payment = createDonePayment();

        // when
        payment.cancel();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt())
                .isNotNull();
    }

    @Test
    @DisplayName("DONE 상태가 아닌 결제를 일반 취소하면 예외가 발생한다")
    void cancelPaymentInvalidStatus() {
        // given
        Payment payment = createInProgressPayment();

        // when & then
        assertThatThrownBy(payment::cancel)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("승인 후 처리 실패 시 IN_PROGRESS 상태의 결제를 CANCELED 상태로 변경한다")
    void cancelAfterApprovalFailure() {
        // given
        Payment payment = createInProgressPayment();

        // when
        payment.cancelAfterApprovalFailure();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt())
                .isNotNull();
    }

    @Test
    @DisplayName("IN_PROGRESS 상태가 아닌 결제를 승인 실패 보상 취소하면 예외가 발생한다")
    void cancelAfterApprovalFailureInvalidStatus() {
        // given
        Payment payment = createDonePayment();

        // when & then
        assertThatThrownBy(
                payment::cancelAfterApprovalFailure
        )
                .isInstanceOf(BusinessException.class);
    }

    private Payment createReadyPayment() {
        Purchase purchase = mock(Purchase.class);

        return Payment.create(
                purchase,
                15000
        );
    }

    private Payment createInProgressPayment() {
        Payment payment = createReadyPayment();

        payment.start("payment-key");

        return payment;
    }

    private Payment createDonePayment() {
        Payment payment = createInProgressPayment();

        payment.complete(LocalDateTime.now());

        return payment;
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 완료하면 DONE 상태가 된다")
    void completeByWebhookFromReady() {
        // given
        Payment payment = createReadyPayment();
        LocalDateTime approvedAt = LocalDateTime.now();

        // when
        payment.completeByWebhook(
                "payment-key",
                approvedAt
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.DONE);

        assertThat(payment.getPaymentKey())
                .isEqualTo("payment-key");

        assertThat(payment.getApprovedAt())
                .isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("IN_PROGRESS 상태의 결제를 Webhook으로 완료하면 DONE 상태가 된다")
    void completeByWebhookFromInProgress() {
        // given
        Payment payment = createInProgressPayment();
        LocalDateTime approvedAt = LocalDateTime.now();

        // when
        payment.completeByWebhook(
                "payment-key",
                approvedAt
        );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.DONE);
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 실패 처리하면 FAILED 상태가 된다")
    void failByWebhookFromReady() {
        // given
        Payment payment = createReadyPayment();

        // when
        payment.failByWebhook();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("READY 상태의 결제를 Webhook으로 취소하면 CANCELED 상태가 된다")
    void cancelByWebhookFromReady() {
        // given
        Payment payment = createReadyPayment();

        // when
        payment.cancelByWebhook();

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        assertThat(payment.getCanceledAt())
                .isNotNull();
    }

    @Test
    @DisplayName("DONE 상태의 결제를 Webhook으로 다시 완료하면 예외가 발생한다")
    void completeByWebhookInvalidStatus() {
        Payment payment = createDonePayment();

        assertThatThrownBy(
                () -> payment.completeByWebhook(
                        "payment-key",
                        LocalDateTime.now()
                )
        )
                .isInstanceOf(BusinessException.class);
    }
}