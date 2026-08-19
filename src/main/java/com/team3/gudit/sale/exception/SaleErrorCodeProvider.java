package com.team3.gudit.sale.exception;

import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.ErrorCodeProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SaleErrorCodeProvider implements ErrorCodeProvider<SaleErrorCode> {
    @Override
    public String getDomain() {
        return "SALE";
    }

    @Override
    public List<ErrorCode> getErrorCodes() {
        return List.of(SaleErrorCode.values());
    }
}
