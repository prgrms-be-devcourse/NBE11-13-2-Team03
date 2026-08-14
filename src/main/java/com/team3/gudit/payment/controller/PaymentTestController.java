package com.team3.gudit.payment.controller;

import com.team3.gudit.payment.config.TossPaymentProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentTestController {

    private final TossPaymentProperties tossPaymentProperties;

    public PaymentTestController(
            TossPaymentProperties tossPaymentProperties
    ) {
        this.tossPaymentProperties = tossPaymentProperties;
    }

    @GetMapping("/payments/test")
    public String paymentTest(
            @RequestParam String orderId,
            @RequestParam int amount,
            Model model
    ) {
        model.addAttribute(
                "clientKey",
                tossPaymentProperties.getClientKey()
        );
        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amount);

        return "payment/test";
    }

    @GetMapping("/payments/test/success")
    public String paymentSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam int amount,
            Model model
    ) {
        model.addAttribute("paymentKey", paymentKey);
        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amount);

        return "payment/success";
    }

    @GetMapping("/payments/test/fail")
    public String paymentFail(
            @RequestParam String code,
            @RequestParam String message,
            Model model
    ) {
        model.addAttribute("code", code);
        model.addAttribute("message", message);

        return "payment/fail";
    }
}