package com.team3.gudit.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class GoodsNotFoundException extends BusinessException {
  private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
  private static final String CODE = "GOODS_NOT_FOUND";


  public GoodsNotFoundException() {

        this("해당 굿즈를 찾을 수 없습니다.");
  }

  public GoodsNotFoundException(String message) {
        super(STATUS, CODE, message);
    }

  public GoodsNotFoundException(Long id) {
    super(STATUS, CODE, String.format("해당 굿즈를 찾을 수 없습니다. (goodsId: %d)", id));
  }
}
