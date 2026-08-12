package com.team3.gudit.global.exception;

import org.springframework.http.HttpStatus;

public class SaleNotFoundException extends BusinessException {
  private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
  private static final String CODE = "Sale_NOT_FOUND";


  public SaleNotFoundException() {

        this("해당 판매 상품을 찾을 수 없습니다.");
  }

  public SaleNotFoundException(String message) {
        super(STATUS, CODE, message);
    }

  public SaleNotFoundException(Long id) {
    super(STATUS, CODE, String.format("해당 판매 상품을 찾을 수 없습니다. (saleId: %d)", id));
  }
}
