package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.client.TossPaymentClient;
import com.team3.gudit.payment.config.TossPaymentProperties;
import com.team3.gudit.payment.dto.TossPaymentCancelRequest;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PaymentTestController {

    private final TossPaymentProperties tossPaymentProperties;

    private final TossPaymentClient tossPaymentClient;

    public PaymentTestController(
            TossPaymentProperties tossPaymentProperties,
            TossPaymentClient tossPaymentClient
    ) {
        this.tossPaymentProperties = tossPaymentProperties;
        this.tossPaymentClient = tossPaymentClient;
    }

    @GetMapping("/payments/test")
    public String paymentTest(Model model) {
        model.addAttribute("clientKey", tossPaymentProperties.getClientKey());

        return "payment/test";
    }

    @GetMapping("/payments/test/success")
    @ResponseBody
    public String paymentSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam int amount
    ) {
        return """
            paymentKey = %s
            orderId = %s
            amount = %d
            """.formatted(paymentKey, orderId, amount);
    }

    @GetMapping("/payments/test/fail")
    @ResponseBody
    public String paymentFail(
            @RequestParam String code,
            @RequestParam String message
    ) {
        return """
            code = %s
            message = %s
            """.formatted(code, message);
    }

    @PostMapping("/payments/test/confirm")
    @ResponseBody
    public TossPaymentResponse confirmPayment(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam int amount
    ) {
        TossPaymentConfirmRequest request =
                new TossPaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        return tossPaymentClient.confirm(
                request,
                "GUDIT_CONFIRM_" + orderId
        );
    }

    @GetMapping("/payments/test/{paymentKey}")
    @ResponseBody
    public TossPaymentResponse getPayment(
            @PathVariable String paymentKey
    ) {
        return tossPaymentClient.getPayment(paymentKey);
    }

    @PostMapping("/payments/test/{paymentKey}/cancel")
    @ResponseBody
    public TossPaymentResponse cancelPayment(
            @PathVariable String paymentKey
    ) {
        TossPaymentCancelRequest request =
                new TossPaymentCancelRequest("Gudit 결제 취소 테스트");

        return tossPaymentClient.cancel(
                paymentKey,
                request,
                "GUDIT_CANCEL_" + paymentKey
        );
    }
}