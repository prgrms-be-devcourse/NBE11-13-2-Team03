package com.team3.gudit.sale.dto.reqeust;

import com.team3.gudit.sale.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
