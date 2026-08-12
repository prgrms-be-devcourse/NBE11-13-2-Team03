package com.team3.gudit.domain.goodsSales.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.domain.goods.constant.DateformatConstant;
import com.team3.gudit.domain.goodsSales.domain.entity.Sale;

import java.time.LocalDateTime;

public record SaleCreateResponseDto(
    Long goodsId,

    Integer initialStock,

    Integer maxPurchaseQuantity,

    @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    LocalDateTime startAt,

    @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    LocalDateTime endAt
) {
    public static SaleCreateResponseDto from(Sale sale) {
        return new SaleCreateResponseDto(
                sale.getGoods().getId(),
                sale.getInitialStock(),
                sale.getMaxPurchaseQuantity(),
                sale.getStartAt(),
                sale.getEndAt()
        );
    }
}