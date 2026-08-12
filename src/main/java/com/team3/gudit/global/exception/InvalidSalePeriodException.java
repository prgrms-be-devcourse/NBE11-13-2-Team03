package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidSalePeriodException extends BusinessException {
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String CODE = "INVALID_SALE_PERIOD";

    public InvalidSalePeriodException() { super(STATUS, CODE, "판마 기간이 아닙니다."); }

    public InvalidSalePeriodException(String message) { super(STATUS, CODE, message); }
}
