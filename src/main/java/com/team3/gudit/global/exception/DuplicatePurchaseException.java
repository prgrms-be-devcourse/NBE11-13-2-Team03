package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class DuplicatePurchaseException extends BusinessException {

    public DuplicatePurchaseException() {
        super(
                HttpStatus.CONFLICT,
                "DUPLICATE_PURCHASE",
                "이미 구매한 상품입니다."
        );
    }
}