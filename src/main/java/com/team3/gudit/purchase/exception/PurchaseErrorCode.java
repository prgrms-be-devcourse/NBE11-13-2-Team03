package com.team3.gudit.purchase.exception;

import com.team3.gudit.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PurchaseErrorCode implements ErrorCode {

    PURCHASE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PURCHASE_001",
            "구매 내역을 찾을 수 없습니다."
    ),

    DUPLICATE_PURCHASE(
            HttpStatus.CONFLICT,
            "PURCHASE_002",
            "이미 구매한 판매 상품입니다."
    ),

    PURCHASE_ALREADY_CANCELED(
            HttpStatus.CONFLICT,
            "PURCHASE_003",
            "이미 취소된 구매입니다."
    ),

    PURCHASE_CANNOT_CANCEL(
            HttpStatus.CONFLICT,
            "PURCHASE_004",
            "현재 상태에서는 구매를 취소할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}