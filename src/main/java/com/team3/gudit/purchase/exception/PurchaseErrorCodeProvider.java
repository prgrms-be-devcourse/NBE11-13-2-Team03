package com.team3.gudit.purchase.exception;

import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.ErrorCodeProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseErrorCodeProvider implements ErrorCodeProvider<PurchaseErrorCode> {
    @Override
    public String getDomain() {
        return "PURCHASE";
    }

    @Override
    public List<ErrorCode> getErrorCodes() {
        return List.of(PurchaseErrorCode.values());
    }
}
