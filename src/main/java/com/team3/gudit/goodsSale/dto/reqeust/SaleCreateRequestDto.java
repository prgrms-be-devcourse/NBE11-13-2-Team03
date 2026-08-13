package com.team3.gudit.goodsSale.dto.reqeust;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goodsSale.domain.entity.Sale;
import com.team3.gudit.goodsSale.domain.enums.SaleStatus;

import java.time.LocalDateTime;

public record SaleCreateRequestDto(
    Long goodsId,

    Integer initialStock,

    Integer maxPurchaseQuantity,

    @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    LocalDateTime startAt,

    @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    LocalDateTime endAt
) {
    public Sale toEntity(Goods goods) {
        return Sale.builder()
                .goods(goods)
                .initialStock(this.initialStock)
                .remainingStock(this.initialStock)
                .maxPurchaseQuantity(this.maxPurchaseQuantity)
                .startAt(this.startAt)
                .endAt(this.endAt)
                .status(SaleStatus.READY)
                .build();
    }

}