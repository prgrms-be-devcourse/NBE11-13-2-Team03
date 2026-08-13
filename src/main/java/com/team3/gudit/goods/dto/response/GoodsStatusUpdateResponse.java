package com.team3.gudit.goods.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GoodsStatusUpdateResponse(
        Long id,

        GoodsStatus status,

        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
        LocalDateTime updatedAt
) {
}
