package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class SaleClosedException extends BusinessException {
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String CODE = "SALE_CLOSED";

    public SaleClosedException() { super(STATUS, CODE, "해당 상품은 판매 상태가 아닙니다.");}

    public SaleClosedException(String message) {
        super(STATUS, CODE, message);
    }
}
