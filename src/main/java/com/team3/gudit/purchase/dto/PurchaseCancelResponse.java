package com.team3.gudit.purchase.dto;

import com.team3.gudit.purchase.entity.PurchaseStatus;

import java.time.LocalDateTime;

public record PurchaseCancelResponse(
        Long purchaseId,
        PurchaseStatus status,
        LocalDateTime canceledAt
) {
}