package com.team3.gudit.global.exception;


import org.springframework.http.HttpStatus;

public class ImageStorageException extends BusinessException{
    public ImageStorageException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public ImageStorageException(String msg) {
        this(HttpStatus.BAD_REQUEST, "IMAGE_ERROR", msg);
    }
    public ImageStorageException(String msg, Exception e) {
        this(HttpStatus.BAD_REQUEST, e.getMessage(), msg);
    }
}
