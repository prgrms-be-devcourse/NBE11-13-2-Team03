package com.team3.gudit.domain.goodsSales.dto.reqeust;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.domain.goods.constant.DateformatConstant;

import java.time.LocalDateTime;

public record SaleUpdateRequestDto(
        Integer initialStock,
        Integer maxPurchaseQuantity,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime startAt,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime endAt
) {}


