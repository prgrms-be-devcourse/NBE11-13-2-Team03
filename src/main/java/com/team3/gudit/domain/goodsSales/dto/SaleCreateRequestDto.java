package com.team3.gudit.domain.goodsSales.dto;

import java.time.LocalDateTime;

public record SaleCreateRequestDto(
        Long goodsId,
        int initialStock,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}