package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.dto.PaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(
        name = "Payment",
        description = "결제 승인 및 처리 API"
)
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 승인",
            description = """
                    토스페이먼츠 결제 승인을 요청합니다.

                    결제 성공 후 전달받은 paymentKey, orderId, amount를 검증한 뒤
                    토스페이먼츠 결제 승인 API를 호출합니다.

                    현재 개발 환경에서는 토스페이먼츠 테스트 결제 API를 사용합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "결제 승인 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = """
                            결제 승인 요청 실패

                            - PAYMENT_002: 결제 금액 불일치
                            - PAYMENT_003: 주문 번호 불일치
                            - PAYMENT_004: 결제 승인 실패
                            """
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = """
                            결제 정보 없음

                            - PAYMENT_001: 결제 정보를 찾을 수 없음
                            """
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            잘못된 결제 상태

                            - PAYMENT_006: 현재 결제 상태에서 요청한 작업을 수행할 수 없음
                            """
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = """
                            결제 승인 후 처리 실패

                            - PAYMENT_007: 승인 후 처리 실패로 결제를 취소함
                            - PAYMENT_008: 승인 후 보상 처리 실패
                            """
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = """
                            결제 처리 상태 확인 실패

                            - PAYMENT_005: 결제 처리 상태를 확인할 수 없음
                            """
            )
    })
    @SecurityRequirement(name = "accessCookie")
    @PostMapping("/confirm")
    public ResponseEntity<TossPaymentResponse> confirm(
            @RequestBody PaymentConfirmRequest request
    ) {
        TossPaymentResponse response = paymentService.confirm(request);

        return ResponseEntity.ok(response);
    }
}