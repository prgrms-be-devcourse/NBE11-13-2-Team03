package com.team3.gudit.payment.exception;

import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.ErrorCodeProvider;

import java.util.List;

public class PaymentErrorCodeProvider implements ErrorCodeProvider<PaymentErrorCode> {
    @Override
    public String getDomain() {
        return "PAYMENT";
    }

    @Override
    public List<ErrorCode> getErrorCodes() {
        return List.of(PaymentErrorCode.values());
    }
}
