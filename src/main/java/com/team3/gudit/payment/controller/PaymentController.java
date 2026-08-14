package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.dto.PaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ResponseEntity<TossPaymentResponse> confirm(
            @RequestBody PaymentConfirmRequest request
    ) {
        TossPaymentResponse response = paymentService.confirm(request);

        return ResponseEntity.ok(response);
    }
}