package com.team3.gudit.sale.exception;

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
    ),

    CANNOT_UPDATE_ONGOING_SALE(
            HttpStatus.BAD_REQUEST,
            "SALE_006",
            "판매 대기 상태에서만 정보를 수정할 수 있습니다."
    ),

    CANNOT_DELETE_ONGOING_SALE(
            HttpStatus.BAD_REQUEST,
            "SALE_007",
            "진행 중인 판매 상품은 삭제할 수 없습니다. 먼저 중단 처리하세요."
    ),

    INVALID_STATUS_TRANSITION(
            HttpStatus.BAD_REQUEST,
            "SALE_008",
            "올바르지 않은 판매 상태 변경 요청입니다."
    ),

    INVALID_INITIAL_STOCK(
            HttpStatus.BAD_REQUEST,
        "SALE_009",
                "초기 재고는 1개 이상이어야 합니다."
    ),

    INVALID_MAX_PURCHASE_QUANTITY(
            HttpStatus.BAD_REQUEST,
        "SALE_010",
                "최대 구매 가능 수량은 1개 이상이어야 합니다."
    ),

    INVALID_PURCHASE_QUANTITY(
            HttpStatus.BAD_REQUEST,
        "SALE_011",
                "구매 수량은 1개 이상이어야 합니다."
    ),

    INVALID_REMAINING_STOCK(
            HttpStatus.BAD_REQUEST,
            "SALE_012",
            "남은 재고는 0개 미만일 수 없습니다."
    ),

    CANNOT_WARMUP_NON_READY_SALE(
            HttpStatus.BAD_REQUEST,
            "SALE_013",
            "판매 대기 상태에서만 Redis Warm-up을 실행할 수 있습니다."
    ),

    REDIS_STOCK_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SALE_014",
            "Redis 판매 재고 정보를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
