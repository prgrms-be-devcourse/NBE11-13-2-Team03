package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.sale.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;

    @Transactional
    public void startPayment(
            String orderId,
            String paymentKey,
            int amount
    ) {
        Payment payment = getPaymentByOrderId(orderId);

        validateAmount(payment, amount);

        payment.start(paymentKey);
    }

    @Transactional
    public void completePayment(
            String orderId,
            TossPaymentResponse response
    ) {
        Payment payment = getPaymentByOrderId(orderId);

        validatePaymentResponse(payment, response);

        payment.complete(
                response.approvedAt().toLocalDateTime()
        );

        payment.getPurchase().complete();
    }

    @Transactional
    public void failPayment(String orderId) {
        Payment payment = getPaymentByOrderId(orderId);
        Purchase purchase = payment.getPurchase();

        payment.fail();

        inventoryService.restoreStock(
                purchase.getSale().getId(),
                purchase.getUser().getId(),
                purchase.getQuantity()
        );

        purchase.cancel();
    }

    @Transactional
    public void compensateApprovalFailure(String paymentKey) {
        Payment payment = getPaymentByPaymentKey(paymentKey);
        Purchase purchase = payment.getPurchase();

        payment.cancelAfterApprovalFailure();

        inventoryService.restoreStock(
                purchase.getSale().getId(),
                purchase.getUser().getId(),
                purchase.getQuantity()
        );

        purchase.cancel();
    }

    @Transactional
    public void completeCancel(String paymentKey) {
        Payment payment = getPaymentByPaymentKey(paymentKey);

        payment.cancel();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. orderId=" + orderId
                        )
                );
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByPaymentKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new BusinessException(
                                PaymentErrorCode.PAYMENT_NOT_FOUND,
                                "Payment not found. paymentKey=" + paymentKey
                        )
                );
    }

    private void validateAmount(Payment payment, int amount) {
        if (payment.getAmount() != amount) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }

        if (payment.getPurchase().getPurchasePrice() != amount) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }
    }

    private void validatePaymentResponse(
            Payment payment,
            TossPaymentResponse response
    ) {
        if (!payment.getOrderId().equals(response.orderId())) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_ORDER_ID_MISMATCH
            );
        }

        if (payment.getAmount() != response.totalAmount()) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH
            );
        }
    }
}