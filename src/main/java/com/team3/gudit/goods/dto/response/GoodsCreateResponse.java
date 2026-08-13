package com.team3.gudit.goods.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GoodsCreateResponse(
        Long id,

        String name,

        String description,

        Integer price,

        String imageUrl,

        GoodsStatus status,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
        LocalDateTime createdAt
) { }
