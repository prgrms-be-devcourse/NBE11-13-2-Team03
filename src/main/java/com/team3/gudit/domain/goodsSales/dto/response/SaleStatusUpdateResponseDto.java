package com.team3.gudit.domain.goodsSales.dto.response;

import com.team3.gudit.domain.goodsSales.domain.entity.Sale;
import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;

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
