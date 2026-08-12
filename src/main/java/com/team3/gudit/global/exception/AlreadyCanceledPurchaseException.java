package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class AlreadyCanceledPurchaseException extends BusinessException {

    public AlreadyCanceledPurchaseException() {
        super(
                HttpStatus.CONFLICT,
                "PURCHASE_ALREADY_CANCELED",
                "이미 취소된 구매입니다."
        );
    }
}