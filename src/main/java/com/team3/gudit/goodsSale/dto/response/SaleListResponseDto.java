package com.team3.gudit.goodsSale.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.goodsSale.domain.entity.Sale;
import com.team3.gudit.goodsSale.domain.enums.SaleStatus;

import java.time.LocalDateTime;

public record SaleListResponseDto(
        Long saleId,
        String goodsName,
        Integer price,
        SaleStatus status,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime startAt,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime endAt
) {
    public static SaleListResponseDto from(Sale sale) {
        return new SaleListResponseDto(
                sale.getId(),
                sale.getGoods().getName(),
                sale.getGoods().getPrice(),
                sale.getStatus(),
                sale.getStartAt(),
                sale.getEndAt()
        );
    }
}