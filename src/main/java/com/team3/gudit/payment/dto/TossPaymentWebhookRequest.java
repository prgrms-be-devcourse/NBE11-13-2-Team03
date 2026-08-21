package com.team3.gudit.payment.dto;

import java.time.LocalDateTime;

public record TossPaymentWebhookRequest(
        String eventType,
        LocalDateTime createdAt,
        TossPaymentResponse data
) {
}
