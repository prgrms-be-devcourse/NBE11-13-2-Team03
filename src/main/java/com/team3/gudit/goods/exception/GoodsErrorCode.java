package com.team3.gudit.goods.exception;

import com.team3.gudit.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GoodsErrorCode implements ErrorCode {

    GOODS_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "GOODS_001",
            "해당 상품을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
