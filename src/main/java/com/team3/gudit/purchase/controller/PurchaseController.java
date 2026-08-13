package com.team3.gudit.purchase.controller;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.dto.PurchaseDetailResponse;
import com.team3.gudit.purchase.dto.PurchaseListResponse;
import com.team3.gudit.purchase.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/api/sales/{saleId}/purchases")
    public ResponseEntity<PurchaseCreateResponse> purchase(
            @PathVariable Long saleId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseCreateResponse response =
                purchaseService.purchase(userDetails.getUserId(), saleId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/purchases")
    public ResponseEntity<PurchaseListResponse> getMyPurchases(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseListResponse response =
                purchaseService.getMyPurchases(userDetails.getUserId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/purchases/{purchaseId}")
    public ResponseEntity<PurchaseDetailResponse> getPurchase(
            @PathVariable Long purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseDetailResponse response =
                purchaseService.getPurchase(
                        userDetails.getUserId(),
                        purchaseId
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/purchases/{purchaseId}/cancel")
    public ResponseEntity<PurchaseCancelResponse> cancel(
            @PathVariable Long purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseCancelResponse response =
                purchaseService.cancel(
                        userDetails.getUserId(),
                        purchaseId
                );

        return ResponseEntity.ok(response);
    }
}