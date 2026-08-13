package com.team3.gudit.purchase.dto;

import com.team3.gudit.purchase.entity.PurchaseStatus;

import java.time.LocalDateTime;

public record PurchaseCreateResponse(
        Long purchaseId,
        Long saleId,
        int quantity,
        int purchasePrice,
        PurchaseStatus status,
        LocalDateTime purchasedAt
) {
}