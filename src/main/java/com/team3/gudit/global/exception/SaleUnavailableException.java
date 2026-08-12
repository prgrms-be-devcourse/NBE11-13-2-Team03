package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class SaleUnavailableException extends BusinessException {

    public SaleUnavailableException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "SALE_UNAVAILABLE",
                message
        );
    }
}