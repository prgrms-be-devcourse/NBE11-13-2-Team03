package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.dto.TossPaymentWebhookRequest;
import com.team3.gudit.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Payment Webhook",
        description = "토스페이먼츠 결제 상태 변경 Webhook API"
)
@RestController
@RequestMapping("/api/webhooks/toss")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @Operation(
            summary = "결제 상태 변경 Webhook",
            description = """
                    토스페이먼츠 PAYMENT_STATUS_CHANGED Webhook을 수신합니다.

                    전달받은 결제 정보를 토스페이먼츠 결제 조회 API로 재검증한 뒤
                    Payment와 Purchase 상태를 보정합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Webhook 처리 성공"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Webhook 처리 실패"
            )
    })
    @PostMapping("/payments")
    public ResponseEntity<Void> handlePaymentStatusChanged(
            @RequestBody TossPaymentWebhookRequest request
    ) {
        paymentWebhookService.handle(request);

        return ResponseEntity.ok().build();
    }
}