package com.team3.gudit.payment.exception;

import com.team3.gudit.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PAYMENT_001",
            "결제 정보를 찾을 수 없습니다."
    ),

    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_002",
            "결제 금액이 일치하지 않습니다."
    ),

    PAYMENT_ORDER_ID_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_003",
            "주문 번호가 일치하지 않습니다."
    ),

    PAYMENT_CONFIRM_FAILED(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_004",
            "결제 승인에 실패했습니다."
    ),

    PAYMENT_PROCESSING_ERROR(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_005",
            "결제 처리 상태를 확인할 수 없습니다."
    ),

    INVALID_PAYMENT_STATUS(
            HttpStatus.CONFLICT,
            "PAYMENT_006",
            "현재 결제 상태에서는 요청한 작업을 수행할 수 없습니다."
    ),

    PAYMENT_FINALIZATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_007",
            "결제 승인 후 처리에 실패하여 결제를 취소했습니다."
    ),

    PAYMENT_COMPENSATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_008",
            "결제 승인 후 보상 처리에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}