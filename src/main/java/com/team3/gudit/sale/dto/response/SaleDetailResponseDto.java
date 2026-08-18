package com.team3.gudit.sale.dto.response;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SaleDetailResponseDto(
    Long id,
    Long goodsId,
    String goodsName,
    Integer price,
    int initialStock,
    int remainingStock,
    Integer maxPurchaseQuantity,
    SaleStatus status,
    String description,
    String imageUrl,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime createdAt
) {

    public static SaleDetailResponseDto from(Sale sale) {
        return SaleDetailResponseDto.builder()
                .id(sale.getId())
                .goodsId(sale.getGoods().getId())
                .goodsName(sale.getGoods().getName())
                .price(sale.getGoods().getPrice())
                .initialStock(sale.getInitialStock())
                .remainingStock(sale.getRemainingStock())
                .maxPurchaseQuantity(sale.getMaxPurchaseQuantity())
                .status(sale.getStatus())
                .description(sale.getGoods().getDescription())
                .imageUrl(sale.getGoods().getImageUrl())
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .createdAt(sale.getCreatedAt())
                .build();
    }

    public static SaleDetailResponseDto from(
            Sale sale,
            int remainingStock,
            SaleStatus status
    ) {
        return SaleDetailResponseDto.builder()
                .id(sale.getId())
                .goodsId(sale.getGoods().getId())
                .goodsName(sale.getGoods().getName())
                .price(sale.getGoods().getPrice())
                .initialStock(sale.getInitialStock())
                .remainingStock(remainingStock)
                .maxPurchaseQuantity(sale.getMaxPurchaseQuantity())
                .status(status)
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .createdAt(sale.getCreatedAt())
                .build();
    }

}
