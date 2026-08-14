package com.team3.gudit.payment.exception;

import lombok.Getter;

@Getter
public class TossPaymentException extends RuntimeException {

    private final String code;

    public TossPaymentException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }
}