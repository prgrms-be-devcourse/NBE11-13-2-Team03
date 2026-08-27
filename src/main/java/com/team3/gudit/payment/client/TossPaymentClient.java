package com.team3.gudit.payment.client;

import com.team3.gudit.payment.config.TossPaymentProperties;
import com.team3.gudit.payment.dto.TossPaymentCancelRequest;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentErrorResponse;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.exception.TossPaymentException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Profile("!performance")
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
        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(TossPaymentResponse.class);

        } catch (RestClientResponseException e) {
            TossPaymentErrorResponse error =
                    e.getResponseBodyAs(TossPaymentErrorResponse.class);

            if (error == null) {
                throw e;
            }

            throw new TossPaymentException(
                    error.code(),
                    error.message()
            );

        } catch (ResourceAccessException e) {
            throw new TossPaymentException(
                    "NETWORK_ERROR",
                    "결제 승인 요청 중 네트워크 오류가 발생했습니다."
            );
        }
    }

    public TossPaymentResponse getPayment(String paymentKey) {
        try {
            return restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossPaymentResponse.class);

        } catch (RestClientResponseException e) {
            TossPaymentErrorResponse error =
                    e.getResponseBodyAs(TossPaymentErrorResponse.class);

            if (error == null) {
                throw e;
            }

            throw new TossPaymentException(
                    error.code(),
                    error.message()
            );

        } catch (ResourceAccessException e) {
            throw new TossPaymentException(
                    "NETWORK_ERROR",
                    "결제 조회 중 네트워크 오류가 발생했습니다."
            );
        }
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