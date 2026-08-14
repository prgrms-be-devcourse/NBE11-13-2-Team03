package com.team3.gudit.payment.dto;

public record TossPaymentErrorResponse(
        String code,
        String message
) {
}