package com.team3.gudit.domain.goodsSales.dto.reqeust;

import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
