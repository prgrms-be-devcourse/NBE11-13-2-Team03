package com.team3.gudit.goods.mapper;

import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.domain.goods.dto.response.*;
import com.team3.gudit.goods.dto.response.*;
import org.springframework.stereotype.Component;

@Component
public class GoodsMapper {

    public Goods toEntity(GoodsCreateRequest request) {
        return toEntity(request, request.imageUrl());
    }

    public Goods toEntity(GoodsCreateRequest request, String imageUrl) {
        return Goods.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(imageUrl)
                .status(GoodsStatus.ACTIVE)
                .build();
    }

    public void updateEntity(
            Goods goods,
            GoodsUpdateRequest request,
            String imageUrl
    ) {
        goods.updateGoodsInfo(
                request.name(),
                request.description(),
                request.price(),
                imageUrl
        );
    }

    public void updateStatus(Goods goods, GoodsStatusUpdateRequest request) {
        goods.updateGoodsStatus(request.status());
    }

    public GoodsCreateResponse toCreateResponse(Goods goods) {
        return GoodsCreateResponse.builder()
                .id(goods.getId())
                .name(goods.getName())
                .description(goods.getDescription())
                .price(goods.getPrice())
                .imageUrl(goods.getImageUrl())
                .status(goods.getStatus())
                .createdAt(goods.getCreatedAt())
                .build();
    }

    public GoodsListResponse toListResponse(Goods goods) {
        return GoodsListResponse.builder()
                .id(goods.getId())
                .name(goods.getName())
                .price(goods.getPrice())
                .imageUrl(goods.getImageUrl())
                .status(goods.getStatus())
                .build();
    }

    public GoodsDetailResponse toDetailResponse(Goods goods) {
        return GoodsDetailResponse.builder()
                .goodsId(goods.getId())
                .name(goods.getName())
                .description(goods.getDescription())
                .price(goods.getPrice())
                .imageUrl(goods.getImageUrl())
                .status(goods.getStatus())
                .createdAt(goods.getCreatedAt())
                .build();
    }


    public GoodsUpdateResponse toUpdateResponse(Goods goods) {
        return GoodsUpdateResponse.builder()
                .id(goods.getId())
                .name(goods.getName())
                .description(goods.getDescription())
                .price(goods.getPrice())
                .imageUrl(goods.getImageUrl())
                .status(goods.getStatus())
                .build();
    }

    public GoodsStatusUpdateResponse toStatusUpdateResponse(Goods goods) {
        return GoodsStatusUpdateResponse.builder()
                .id(goods.getId())
                .status(goods.getStatus())
                .updatedAt(goods.getUpdatedAt())
                .build();
    }

}