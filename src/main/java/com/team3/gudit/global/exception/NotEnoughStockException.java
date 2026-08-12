package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class NotEnoughStockException extends BusinessException {
  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
  private static final String CODE = "STOCK_NOT_ENOUGH";

  public NotEnoughStockException() {
    super(STATUS, CODE, "재고가 부족합니다.");
  }


  public NotEnoughStockException(String message) {
    super(STATUS, CODE, message);
  }
}
