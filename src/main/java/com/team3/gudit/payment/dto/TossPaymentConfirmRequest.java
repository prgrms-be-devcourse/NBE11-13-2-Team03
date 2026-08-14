package com.team3.gudit.payment.dto;

public record TossPaymentConfirmRequest(
        String paymentKey,
        String orderId,
        int amount
) {
}