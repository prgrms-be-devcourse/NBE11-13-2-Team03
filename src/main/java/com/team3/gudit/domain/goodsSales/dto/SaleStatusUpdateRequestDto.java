package com.team3.gudit.domain.goodsSales.dto;

import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
