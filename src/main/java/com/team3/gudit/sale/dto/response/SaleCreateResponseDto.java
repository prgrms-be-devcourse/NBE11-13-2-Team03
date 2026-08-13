package com.team3.gudit.sale.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.sale.domain.entity.Sale;

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