package com.team3.gudit.purchase.dto;

import com.team3.gudit.purchase.entity.PurchaseStatus;

import java.time.LocalDateTime;

public record PurchaseDetailResponse(
        Long purchaseId,
        Long saleId,
        Long goodsId,
        String goodsName,
        String imageUrl,
        int quantity,
        int purchasePrice,
        PurchaseStatus status,
        LocalDateTime purchasedAt,
        LocalDateTime canceledAt
) {
}