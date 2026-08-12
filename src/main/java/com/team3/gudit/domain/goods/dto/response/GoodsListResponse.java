package com.team3.gudit.domain.goods.dto.response;

import com.team3.gudit.domain.goods.domain.enums.GoodsStatus;
import lombok.Builder;


@Builder
public record GoodsListResponse(
        Long id,
        String name,
        Integer price,
        String imageUrl,
        GoodsStatus status
) {}
