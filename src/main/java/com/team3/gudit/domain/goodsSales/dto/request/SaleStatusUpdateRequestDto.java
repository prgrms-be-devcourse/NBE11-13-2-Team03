package com.team3.gudit.domain.goodsSales.dto.request;

import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
