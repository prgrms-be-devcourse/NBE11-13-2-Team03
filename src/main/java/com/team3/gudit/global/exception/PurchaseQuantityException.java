package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class PurchaseQuantityException extends BusinessException {
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String CODE = "INVALID_SALE_PERIOD";

    public PurchaseQuantityException() { super(STATUS, CODE, "해당 상품의 재고가 부족합니다."); }

    public PurchaseQuantityException(String message) {
        super(STATUS, CODE, message);
    }
}
