package com.team3.gudit.goods.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GoodsUpdateRequest(
        @NotBlank(message = "상품명은 필수 입력값입니다.")
        @Size(max = 255, message = "상품명은 255자 이하이어야 합니다.")
        String name,

        @Size(max = 100, message = "상품 설명은 100자 이하이어야 합니다.")
        String description,

        @NotNull(message = "가격은 필수 입력값입니다.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        String imageUrl
) {
}
