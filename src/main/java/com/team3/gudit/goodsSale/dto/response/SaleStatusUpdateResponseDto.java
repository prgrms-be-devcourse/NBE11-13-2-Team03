package com.team3.gudit.goodsSale.dto.response;

import com.team3.gudit.goodsSale.domain.entity.Sale;
import com.team3.gudit.goodsSale.domain.enums.SaleStatus;

public record SaleStatusUpdateResponseDto(
        Long saleId,
        SaleStatus status
) {
    public static SaleStatusUpdateResponseDto from(Sale sale) {
        return new SaleStatusUpdateResponseDto(
                sale.getId(),
                sale.getStatus()
        );
    }
}
