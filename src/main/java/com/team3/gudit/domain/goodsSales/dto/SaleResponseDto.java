package com.team3.gudit.domain.goodsSales.dto;

import com.team3.gudit.domain.goodsSales.domain.entity.Sale;
import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SaleResponseDto(
        Long id,
        Long goodsId,
        String goodsName,
        Integer price,
        int initialStock,
        int remainingStock,
        Integer maxPurchaseQuantity,
        SaleStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt
) {

    public static SaleResponseDto from(Sale sale) {
        return SaleResponseDto.builder()
                .id(sale.getId())
                .goodsId(sale.getGoods().getId())
                .goodsName(sale.getGoods().getName())
                .price(sale.getGoods().getPrice())
                .initialStock(sale.getInitialStock())
                .remainingStock(sale.getRemainingStock())
                .maxPurchaseQuantity(sale.getMaxPurchaseQuantity())
                .status(sale.getStatus())
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .createdAt(sale.getCreatedAt())
                .build();
    }

}
