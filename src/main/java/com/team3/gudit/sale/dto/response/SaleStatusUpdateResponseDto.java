package com.team3.gudit.sale.dto.response;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;

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
