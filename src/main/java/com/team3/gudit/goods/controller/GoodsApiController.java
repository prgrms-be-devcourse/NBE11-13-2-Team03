package com.team3.gudit.goods.controller;

import com.team3.gudit.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.goods.dto.response.*;
import com.team3.gudit.goods.service.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(
        name = "Goods",
        description = "상품 등록, 조회, 수정 및 상태 관리 API"
)
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsApiController {
    private final GoodsService goodsService;

    @Operation(
            summary = "상품 등록",
            description = "상품 정보와 선택 이미지 파일을 multipart/form-data 형식으로 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "상품 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 이미지 파일 형식이 올바르지 않음"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 등록 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "이미지 저장 실패"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GoodsCreateResponse> createGoods(
            @Valid @RequestPart GoodsCreateRequest request,
            @RequestPart(value = "fileImage", required = false) MultipartFile fileImage

    ) {
        GoodsCreateResponse response = goodsService.create(request, fileImage);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(
            summary = "판매 가능한 상품 목록 조회",
            description = "활성 상태인 상품 목록을 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "상품 목록 조회 성공"
    )
    @GetMapping
    public ResponseEntity<List<GoodsListResponse>> getGoodsList() {
        List<GoodsListResponse> response = goodsService.goodsList();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "상품 상세 조회",
            description = "활성 상태인 특정 상품의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 상세 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GOODS_001: 해당 상품을 찾을 수 없음"
            )
    })
    @GetMapping("/{goodsId}")
    public ResponseEntity<GoodsDetailResponse> getGoods(
            @PathVariable Long goodsId
    ) {
        GoodsDetailResponse response = goodsService.goodsDetail(goodsId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "관리자 상품 목록 조회",
            description = "상품 상태와 관계없이 전체 상품 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 상품 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 관리 권한 없음"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/admin")
    public ResponseEntity<List<GoodsListResponse>> getAdminGoodsList() {
        List<GoodsListResponse> response =
                goodsService.adminGoodsList();

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "관리자 상품 상세 조회",
            description = "상품 상태와 관계없이 특정 상품의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "관리자 상품 상세 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 관리 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GOODS_001: 해당 상품을 찾을 수 없음"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/admin/{goodsId}")
    public ResponseEntity<GoodsDetailResponse> getAdminGoods(
            @PathVariable Long goodsId
    ) {
        GoodsDetailResponse response =
                goodsService.adminGoodsDetail(goodsId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "상품 수정",
            description = "상품 정보와 선택 이미지 파일을 multipart/form-data 형식으로 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 또는 이미지 파일 형식이 올바르지 않음"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 수정 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GOODS_001: 해당 상품을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "이미지 저장 실패"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @PutMapping(
            value = "/{goodsId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<GoodsUpdateResponse> updateGoods(
            @PathVariable Long goodsId,
            @Valid @RequestPart GoodsUpdateRequest request,
            @RequestPart(value = "fileImage", required = false) MultipartFile fileImage
    ) {
        GoodsUpdateResponse response = goodsService.updateGoods(goodsId, request, fileImage);
        return ResponseEntity.ok().body(response);
    }

    @Operation(
            summary = "상품 상태 변경",
            description = "특정 상품의 활성 상태를 변경합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "상품 상태 변경 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청한 상품 상태가 올바르지 않음"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 상태 변경 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GOODS_001: 해당 상품을 찾을 수 없음"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @PatchMapping("/{goodsId}/status")
    public ResponseEntity<GoodsStatusUpdateResponse> updateGoodsStatus(
            @PathVariable Long goodsId,
            @Valid @RequestBody GoodsStatusUpdateRequest request
    ) {
        GoodsStatusUpdateResponse response = goodsService.updateGoodsStatus(goodsId, request);
        return ResponseEntity.ok().body(response);
    }

    @Operation(
            summary = "상품 삭제",
            description = "특정 상품을 비활성 상태로 변경하여 삭제 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "상품 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "상품 삭제 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "GOODS_001: 해당 상품을 찾을 수 없음"
            )
    })
    @SecurityRequirement(name = "cookieAuth")
    @DeleteMapping("/{goodsId}")
    public ResponseEntity<Void> deleteGoods(@PathVariable Long goodsId) {
        goodsService.deleteGoods(goodsId);
        return ResponseEntity.noContent().build();
    }

}
