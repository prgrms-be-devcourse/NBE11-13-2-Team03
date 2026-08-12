package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class PurchaseNotFoundException extends BusinessException {

    public PurchaseNotFoundException(Long purchaseId) {
        super(
                HttpStatus.NOT_FOUND,
                "PURCHASE_NOT_FOUND",
                "구매 내역을 찾을 수 없습니다. purchaseId=" + purchaseId
        );
    }
}