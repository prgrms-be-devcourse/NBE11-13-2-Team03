package com.team3.gudit.purchase.dto;

import java.util.List;

public record PurchaseListResponse(
        List<PurchaseSummaryResponse> purchases
) {
}