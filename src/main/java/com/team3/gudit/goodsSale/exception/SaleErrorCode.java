package com.team3.gudit.goodsSale.exception;

import com.team3.gudit.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SaleErrorCode implements ErrorCode {

    INVALID_SALE_PERIOD(
            HttpStatus.BAD_REQUEST,
            "SALE_001",
            "상품 판매 기간이 아닙니다."
    ),

    NOT_ENOUGH_STOCK(
            HttpStatus.BAD_REQUEST,
            "SALE_002",
            "재고가 부족합니다."
    ),

    EXCEEDED_PURCHASE_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "SALE_003",
            "최대 구매 가능 수량을 초과했습니다."
    ),

    SALE_CLOSED(
            HttpStatus.BAD_REQUEST,
            "SALE_004",
            "해당 상품은 판매 상태가 아닙니다."
    ),
    SALE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SALE_005",
            "해당 판매 상품을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
