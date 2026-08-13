package com.team3.gudit.goods.dto.request;

import com.team3.gudit.goods.domain.enums.GoodsStatus;
import jakarta.validation.constraints.NotNull;

public record GoodsStatusUpdateRequest(
        @NotNull(message = "상태값은 필수입니다.")
        GoodsStatus status
) {
}
