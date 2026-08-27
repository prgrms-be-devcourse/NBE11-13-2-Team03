package com.team3.gudit.sale.dto.reqeust;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.team3.gudit.goods.constant.DateformatConstant;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record SaleCreateRequestDto(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long goodsId,

        @NotNull(message = "초기 재고는 필수입니다.")
        @Positive(message = "초기 재고는 1개 이상이어야 합니다.")
        Integer initialStock,

        @NotNull(message = "1인당 최대 구매 수량은 필수입니다.")
        @Positive(message = "1인당 최대 구매 수량은 1개 이상이어야 합니다.")
        Integer maxPurchaseQuantity,

        @NotNull(message = "판매 시작 시간은 필수입니다.")
        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime startAt,

        @NotNull(message = "판매 종료 시간은 필수입니다.")
        @JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
        LocalDateTime endAt
) {
    public Sale toEntity(Goods goods) {
        return Sale.builder()
                .goods(goods)
                .initialStock(this.initialStock)
                .remainingStock(this.initialStock)
                .maxPurchaseQuantity(this.maxPurchaseQuantity)
                .startAt(this.startAt)
                .endAt(this.endAt)
                .status(SaleStatus.READY)
                .build();
    }

}