package com.team3.gudit.domain.goodsSalses.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SaleStatus {

    READY("판매 대기"),
    ON_SALE("판매 중"),
    SOLD_OUT("품절"),
    CLOSED("판매 종료");

    private final String description;
}
