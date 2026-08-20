package com.team3.gudit.goods.exception;

import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.ErrorCodeProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoodsErrorCodeProvider implements ErrorCodeProvider<GoodsErrorCode> {
    @Override
    public String getDomain() {
        return "GOODS";
    }

    @Override
    public List<ErrorCode> getErrorCodes() {
        return List.of(GoodsErrorCode.values());
    }
}
