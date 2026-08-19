package com.team3.gudit.purchase.controller;

import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.dto.PurchaseDetailResponse;
import com.team3.gudit.purchase.dto.PurchaseListResponse;
import com.team3.gudit.purchase.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Purchase",
        description = "굿즈 구매 및 구매 내역 관리 API"
)
@RestController
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;


    @Operation(
            summary = "상품 구매",
            description = """
                    특정 판매 상품을 구매합니다.
                    
                    로그인한 사용자를 기준으로 구매를 처리하며,
                    판매 기간, 판매 상태, 재고 및 중복 구매 여부를 검증합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "구매 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            구매 요청 실패
                            
                            - SALE_001: 상품 판매 기간이 아님
                            - SALE_002: 재고 부족
                            - SALE_003: 최대 구매 가능 수량 초과
                            - SALE_004: 판매 중인 상품이 아님
                            """
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            판매 상품을 찾을 수 없음
                            
                            - SALE_005: 해당 판매 상품을 찾을 수 없음
                            """
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            중복 구매
                            
                            - PURCHASE_002: 이미 구매한 판매 상품
                            """
            )
    })
    @SecurityRequirement(name = "accessCookie")
    @PostMapping("/api/sales/{saleId}/purchases")
    public ResponseEntity<PurchaseCreateResponse> purchase(
            @PathVariable Long saleId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseCreateResponse response =
                purchaseService.purchase(userDetails.getUserId(), saleId);

        return ResponseEntity.ok(response);
    }


    @Operation(
            summary = "내 구매 내역 조회",
            description = """
                    현재 로그인한 사용자의 구매 내역을 조회합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "구매 내역 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            )
    })
    @SecurityRequirement(name = "accessCookie")
    @GetMapping("/api/purchases")
    public ResponseEntity<PurchaseListResponse> getMyPurchases(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PurchaseListResponse response =
                purchaseService.getMyPurchases(userDetails.getUserId());

        return ResponseEntity.ok(response);
    }


    @Operation(
            summary = "구매 상세 조회",
            description = """
                    특정 구매 내역의 상세 정보를 조회합니다.
                    
                    로그인한 사용자의 구매 내역을 기준으로 조회합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "구매 상세 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            구매 내역을 찾을 수 없음
                            
                            - PURCHASE_001: 구매 내역을 찾을 수 없음
                            """
            )
    })
    @SecurityRequirement(name = "accessCookie")
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


    @Operation(
            summary = "구매 취소",
            description = """
                    특정 구매를 취소합니다.
                    
                    로그인한 사용자의 구매 내역을 기준으로 취소하며,
                    이미 취소된 구매는 다시 취소할 수 없습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "구매 취소 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            구매 내역을 찾을 수 없음
                            
                            - PURCHASE_001: 구매 내역을 찾을 수 없음
                            """
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            구매 취소 충돌
                            
                            - PURCHASE_003: 이미 취소된 구매
                            """
            )
    })
    @SecurityRequirement(name = "accessCookie")
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