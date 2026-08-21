package com.team3.gudit.payment.client;

import com.team3.gudit.payment.config.TossPaymentProperties;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Profile("performance")
public class PerformanceTossPaymentClient extends TossPaymentClient {

    public PerformanceTossPaymentClient(
            TossPaymentProperties properties
    ) {
        super(properties);
    }

    @Override
    public TossPaymentResponse confirm(
            TossPaymentConfirmRequest request,
            String idempotencyKey
    ) {
        return new TossPaymentResponse(
                request.paymentKey(),
                request.orderId(),
                "DONE",
                request.amount(),
                OffsetDateTime.now()
        );
    }
}