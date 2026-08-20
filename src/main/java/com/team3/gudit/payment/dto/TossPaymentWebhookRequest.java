package com.team3.gudit.payment.dto;

import java.time.OffsetDateTime;

public record TossPaymentWebhookRequest(
        String eventType,
        OffsetDateTime createdAt,
        TossPaymentResponse data
) {
}
