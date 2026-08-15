package com.team3.gudit.payment.dto;

import java.time.OffsetDateTime;

public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Integer totalAmount,
        OffsetDateTime approvedAt
) {
}