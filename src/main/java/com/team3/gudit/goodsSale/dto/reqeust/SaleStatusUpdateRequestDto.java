package com.team3.gudit.goodsSale.dto.reqeust;

import com.team3.gudit.goodsSale.domain.enums.SaleStatus;

public record SaleStatusUpdateRequestDto(
        SaleStatus status
) {}
