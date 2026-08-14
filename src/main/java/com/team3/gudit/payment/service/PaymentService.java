package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.client.TossPaymentClient;
import com.team3.gudit.payment.dto.PaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentCancelRequest;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.exception.TossPaymentException;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentTransactionService paymentTransactionService;

    @Transactional
    public Payment createPayment(Purchase purchase) {
        Payment payment = Payment.create(
                purchase,
                purchase.getPurchasePrice()
        );

        return paymentRepository.save(payment);
    }

    public TossPaymentResponse confirmPayment(
            String paymentKey,
            String orderId,
            int amount
    ) {
        TossPaymentConfirmRequest request =
                new TossPaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        String idempotencyKey = "GUDIT_CONFIRM_" + orderId;

        return tossPaymentClient.confirm(
                request,
                idempotencyKey
        );
    }

    public TossPaymentResponse cancelPayment(String paymentKey) {
        return cancelPayment(
                paymentKey,
                "구매 취소"
        );
    }

    private TossPaymentResponse cancelPayment(
            String paymentKey,
            String cancelReason
    ) {
        TossPaymentCancelRequest request =
                new TossPaymentCancelRequest(cancelReason);

        String idempotencyKey =
                "GUDIT_CANCEL_" + paymentKey;

        return tossPaymentClient.cancel(
                paymentKey,
                request,
                idempotencyKey
        );
    }

    public TossPaymentResponse confirm(PaymentConfirmRequest request) {

        paymentTransactionService.startPayment(
                request.orderId(),
                request.paymentKey(),
                request.amount()
        );

        try {
            TossPaymentResponse response = confirmPayment(
                    request.paymentKey(),
                    request.orderId(),
                    request.amount()
            );

            try {
                paymentTransactionService.completePayment(
                        request.orderId(),
                        response
                );

                return response;

            } catch (RuntimeException e) {
                compensateAfterApproval(
                        request.paymentKey()
                );

                throw new BusinessException(
                        PaymentErrorCode.PAYMENT_FINALIZATION_FAILED,
                        e
                );
            }

        } catch (TossPaymentException e) {

            if (isDefinitePaymentFailure(e.getCode())) {
                paymentTransactionService.failPayment(
                        request.orderId()
                );

                throw new BusinessException(
                        PaymentErrorCode.PAYMENT_CONFIRM_FAILED,
                        e
                );
            }

            return reconcilePayment(request, e);
        }
    }

    private TossPaymentResponse reconcilePayment(
            PaymentConfirmRequest request,
            TossPaymentException originalException
    ) {
        try {
            TossPaymentResponse response =
                    tossPaymentClient.getPayment(
                            request.paymentKey()
                    );

            if ("DONE".equals(response.status())) {
                try {
                    paymentTransactionService.completePayment(
                            request.orderId(),
                            response
                    );

                    return response;

                } catch (RuntimeException e) {
                    compensateAfterApproval(
                            request.paymentKey()
                    );

                    throw new BusinessException(
                            PaymentErrorCode.PAYMENT_FINALIZATION_FAILED,
                            e
                    );
                }
            }

            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR,
                    originalException
            );

        } catch (TossPaymentException e) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR,
                    originalException
            );
        }
    }

    private void compensateAfterApproval(String paymentKey) {
        try {
            cancelPayment(
                    paymentKey,
                    "결제 승인 후 처리 실패 보상 취소"
            );

            paymentTransactionService.compensateApprovalFailure(
                    paymentKey
            );

        } catch (RuntimeException compensationException) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_COMPENSATION_FAILED,
                    compensationException
            );
        }
    }

    public void cancelCompletedPayment(String paymentKey) {

        cancelPayment(paymentKey);

        paymentTransactionService.completeCancel(
                paymentKey
        );
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByPurchaseId(Long purchaseId) {
        return paymentRepository.findByPurchaseId(purchaseId)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. purchaseId=" + purchaseId
                        )
                );
    }

    private boolean isDefinitePaymentFailure(String tossErrorCode) {
        return switch (tossErrorCode) {
            case "REJECT_ACCOUNT_PAYMENT",
                 "REJECT_CARD_COMPANY",
                 "INVALID_CARD_EXPIRATION",
                 "INVALID_STOPPED_CARD",
                 "INVALID_CARD_LOST_OR_STOLEN",
                 "EXCEED_MAX_DAILY_PAYMENT_COUNT",
                 "EXCEED_MAX_AMOUNT",
                 "EXCEED_MAX_ONE_DAY_AMOUNT",
                 "EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT",
                 "EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT" -> true;

            default -> false;
        };
    }
}