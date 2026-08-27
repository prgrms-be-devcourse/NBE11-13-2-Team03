package com.team3.gudit.sale.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SaleListResponseDto(
        Long saleId,
        String goodsName,
        Integer price,
        Integer remainingStock,
        SaleStatus status,
        String description,
        String imageUrl,
        Integer initialStock,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime startAt,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime endAt
) {
    public static SaleListResponseDto from(Sale sale) {
        return SaleListResponseDto.builder()
                .saleId(sale.getId())
                .goodsName(sale.getGoods().getName())
                .price(sale.getGoods().getPrice())
                .remainingStock(sale.getRemainingStock())
                .status(sale.getStatus())
                .description(sale.getGoods().getDescription())
                .imageUrl(sale.getGoods().getImageUrl())
                .initialStock(sale.getInitialStock())
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .build();
    }

    public static SaleListResponseDto from(
            Sale sale,
            Integer remainingStock,
            SaleStatus status
    ) {
        return SaleListResponseDto.builder()
                .saleId(sale.getId())
                .goodsName(sale.getGoods().getName())
                .price(sale.getGoods().getPrice())
                .remainingStock(remainingStock)
                .status(status)
                .description(sale.getGoods().getDescription())
                .imageUrl(sale.getGoods().getImageUrl())
                .initialStock(sale.getInitialStock())
                .startAt(sale.getStartAt())
                .endAt(sale.getEndAt())
                .build();
    }
}