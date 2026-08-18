package com.team3.gudit.sale.dto;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleRedisDto {
    private Long saleId;
    private Long startAtMilli;
    private Long endAtMilli;
    private Integer maxPurchaseQuantity;
    private SaleStatus status;

    public static SaleRedisDto from(Sale sale) {
        ZoneId zoneId = ZoneId.systemDefault();
        return SaleRedisDto.builder()
                .saleId(sale.getId())
                .startAtMilli(sale.getStartAt().atZone(zoneId).toInstant().toEpochMilli())
                .endAtMilli(sale.getEndAt().atZone(zoneId).toInstant().toEpochMilli())
                .maxPurchaseQuantity(sale.getMaxPurchaseQuantity())
                .status(sale.getStatus())
                .build();
    }

    public Map<String, String> toHashFields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("startAt", String.valueOf(startAtMilli));
        fields.put("endAt", String.valueOf(endAtMilli));
        fields.put("maxPurchaseQuantity", String.valueOf(maxPurchaseQuantity));
        fields.put("status", status.name());
        return fields;
    }
}
