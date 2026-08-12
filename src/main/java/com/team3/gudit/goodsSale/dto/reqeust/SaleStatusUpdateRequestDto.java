package com.team3.gudit.goodsSales.dto.reqeust;

import com.team3.gudit.goodsSales.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
