package com.team3.gudit.goods.exception;

import com.team3.gudit.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements ErrorCode {

    INVALID_IMAGE_FILENAME(
            HttpStatus.BAD_REQUEST,
            "IMAGE_001",
            "올바르지 않은 이미지 파일명입니다."
    ),


    IMAGE_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "IMAGE_002",
            "이미지 저장에 실패했습니다."
    ),

    INVALID_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "IMAGE_003",
            "이미지 파일만 업로드할 수 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
