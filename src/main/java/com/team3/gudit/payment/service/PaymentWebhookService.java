package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.client.TossPaymentClient;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.dto.TossPaymentWebhookRequest;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final String PAYMENT_STATUS_CHANGED =
            "PAYMENT_STATUS_CHANGED";

    private final TossPaymentClient tossPaymentClient;
    private final PaymentService paymentService;
    private final PaymentTransactionService paymentTransactionService;

    public void handle(TossPaymentWebhookRequest request) {
        if (!PAYMENT_STATUS_CHANGED.equals(request.eventType())) {
            return;
        }

        TossPaymentResponse webhookPayment = request.data();

        TossPaymentResponse actualPayment =
                tossPaymentClient.getPayment(
                        webhookPayment.paymentKey()
                );

        validatePayment(webhookPayment, actualPayment);

        switch (actualPayment.status()) {
            case "DONE" -> handleDone(actualPayment);
            case "CANCELED" ->
                    paymentTransactionService.reconcileCanceled(actualPayment);
            case "ABORTED" ->
                    paymentTransactionService.reconcileAborted(actualPayment);
            case "EXPIRED" ->
                    paymentTransactionService.reconcileExpired(actualPayment);
            default -> {
                // READY, IN_PROGRESS 등 중간 상태는 보정하지 않는다.
            }
        }
    }

    private void handleDone(TossPaymentResponse actualPayment) {
        Payment payment =
                paymentTransactionService.getPaymentByOrderId(
                        actualPayment.orderId()
                );

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            paymentService.cancelPayment(
                    actualPayment.paymentKey()
            );
            return;
        }

        paymentTransactionService.reconcileDone(
                actualPayment
        );
    }

    private void validatePayment(
            TossPaymentResponse webhookPayment,
            TossPaymentResponse actualPayment
    ) {
        if (!Objects.equals(
                webhookPayment.paymentKey(),
                actualPayment.paymentKey()
        )) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                    "Webhook paymentKey mismatch. paymentKey="
                            + webhookPayment.paymentKey()
            );
        }

        if (!Objects.equals(
                webhookPayment.orderId(),
                actualPayment.orderId()
        )) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                    "Webhook orderId mismatch. orderId="
                            + webhookPayment.orderId()
            );
        }

        if (!Objects.equals(
                webhookPayment.totalAmount(),
                actualPayment.totalAmount()
        )) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_WEBHOOK_VALIDATION_FAILED,
                    "Webhook amount mismatch. orderId="
                            + webhookPayment.orderId()
            );
        }
    }
}