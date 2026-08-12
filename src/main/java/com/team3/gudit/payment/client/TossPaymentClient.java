package com.team3.gudit.payment.client;

import com.team3.gudit.payment.config.TossPaymentProperties;
import com.team3.gudit.payment.dto.TossPaymentCancelRequest;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TossPaymentClient {

    private static final String TOSS_API_URL = "https://api.tosspayments.com";

    private final RestClient restClient;

    public TossPaymentClient(TossPaymentProperties properties) {
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString(
                        (properties.getSecretKey() + ":")
                                .getBytes(StandardCharsets.UTF_8)
                );

        this.restClient = RestClient.builder()
                .baseUrl(TOSS_API_URL)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + encodedSecretKey
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public TossPaymentResponse confirm(
            TossPaymentConfirmRequest request,
            String idempotencyKey
    ) {
        return restClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    public TossPaymentResponse getPayment(String paymentKey) {
        return restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentResponse.class);
    }

    public TossPaymentResponse cancel(
            String paymentKey,
            TossPaymentCancelRequest request,
            String idempotencyKey
    ) {
        return restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TossPaymentResponse.class);
    }
}