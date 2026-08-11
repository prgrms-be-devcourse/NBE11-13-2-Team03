package com.team3.gudit.domain.goods.controller;

import com.team3.gudit.domain.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.domain.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.domain.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.domain.goods.dto.response.*;
import com.team3.gudit.domain.goods.service.GoodsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsApiController {
    private final GoodsService goodsService;

    @PostMapping
    public ResponseEntity<GoodsCreateResponse> createGoods(
            @RequestBody GoodsCreateRequest request,
            @RequestPart(value = "fileImage", required = false) MultipartFile fileImage

    ) {
        GoodsCreateResponse response = goodsService.create(request, fileImage);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping
    public ResponseEntity<List<GoodsListResponse>> getGoodsList() {
        List<GoodsListResponse> response = goodsService.goodsList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{goodsId}")
    public ResponseEntity<GoodsDetailResponse> getGoods(
            @PathVariable Long goodsId
    ) {
        GoodsDetailResponse response = goodsService.goodsDetail(goodsId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{goodsId}")
    public ResponseEntity<GoodsUpdateResponse> updateGoods(
            @PathVariable Long goodsId,
            @RequestBody GoodsUpdateRequest request,
            @RequestPart(value = "fileImage", required = false) MultipartFile fileImage
    ) {
        GoodsUpdateResponse response = goodsService.updateGoods(goodsId, request, fileImage);
        return ResponseEntity.ok().body(response);
    }

    @PatchMapping("/{goodsId}/status")
    public ResponseEntity<GoodsStatusUpdateResponse> updateGoodsStatus(
            @PathVariable Long goodsId,
            @Valid @RequestBody GoodsStatusUpdateRequest request
    ) {
        GoodsStatusUpdateResponse response = goodsService.updateGoodsStatus(goodsId, request);
        return ResponseEntity.ok().body(response);
    }

}
