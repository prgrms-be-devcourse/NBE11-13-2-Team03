package com.team3.gudit.goods.controller;

import tools.jackson.databind.ObjectMapper;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.goods.dto.response.GoodsCreateResponse;
import com.team3.gudit.goods.dto.response.GoodsUpdateResponse;
import com.team3.gudit.goods.service.GoodsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoodsApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class GoodsApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoodsService goodsService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("상품 정보와 이미지를 multipart 요청으로 생성한다")
    void createGoods() throws Exception {
        // given
        GoodsCreateRequest request =
                new GoodsCreateRequest(
                        "테스트 상품",
                        "테스트 설명",
                        10_000,
                        null
                );

        MockMultipartFile requestPart =
                jsonPart("request", request);

        MockMultipartFile imagePart =
                new MockMultipartFile(
                        "fileImage",
                        "test.png",
                        MediaType.IMAGE_PNG_VALUE,
                        "image".getBytes()
                );

        GoodsCreateResponse response =
                GoodsCreateResponse.builder()
                        .id(1L)
                        .name("테스트 상품")
                        .description("테스트 설명")
                        .price(10_000)
                        .imageUrl("test.png")
                        .status(GoodsStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build();

        given(goodsService.create(any(), any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(multipart("/api/goods")
                        .file(requestPart)
                        .file(imagePart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name")
                        .value("테스트 상품"))
                .andExpect(jsonPath("$.price").value(10_000));

        verify(goodsService).create(any(), any());
    }

    @Test
    @DisplayName("상품명이 비어 있으면 상품 생성 요청은 400을 반환한다")
    void createGoodsWithBlankName() throws Exception {
        // given
        GoodsCreateRequest request =
                new GoodsCreateRequest(
                        "",
                        "테스트 설명",
                        10_000,
                        null
                );

        MockMultipartFile requestPart =
                jsonPart("request", request);

        // when & then
        mockMvc.perform(multipart("/api/goods")
                        .file(requestPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.name")
                        .value("상품명은 필수 입력값입니다."));

        verify(goodsService, never())
                .create(any(), any());
    }

    @Test
    @DisplayName("상품 가격이 음수이면 상품 생성 요청은 400을 반환한다")
    void createGoodsWithNegativePrice() throws Exception {
        // given
        GoodsCreateRequest request =
                new GoodsCreateRequest(
                        "테스트 상품",
                        "테스트 설명",
                        -1,
                        null
                );

        MockMultipartFile requestPart =
                jsonPart("request", request);

        // when & then
        mockMvc.perform(multipart("/api/goods")
                        .file(requestPart))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.price")
                        .value("가격은 0원 이상이어야 합니다."));

        verify(goodsService, never())
                .create(any(), any());
    }

    @Test
    @DisplayName("상품 전체 수정은 multipart PUT 요청으로 처리한다")
    void updateGoods() throws Exception {
        // given
        GoodsUpdateRequest request =
                new GoodsUpdateRequest(
                        "수정 상품",
                        "수정 설명",
                        20_000,
                        null
                );

        MockMultipartFile requestPart =
                jsonPart("request", request);

        GoodsUpdateResponse response =
                GoodsUpdateResponse.builder()
                        .id(1L)
                        .name("수정 상품")
                        .description("수정 설명")
                        .price(20_000)
                        .imageUrl("test.png")
                        .status(GoodsStatus.ACTIVE)
                        .updatedAt(LocalDateTime.now())
                        .build();

        given(goodsService.updateGoods(
                any(),
                any(),
                any()
        )).willReturn(response);

        // when & then
        mockMvc.perform(multipart(
                        "/api/goods/{goodsId}",
                        1L
                )
                        .file(requestPart)
                        .with(requestBuilder -> {
                            requestBuilder.setMethod("PUT");
                            return requestBuilder;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name")
                        .value("수정 상품"))
                .andExpect(jsonPath("$.price").value(20_000));

        verify(goodsService)
                .updateGoods(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("상품 상태가 null이면 400 응답을 반환한다")
    void updateGoodsStatusWithNullStatus() throws Exception {
        // given
        GoodsStatusUpdateRequest request =
                new GoodsStatusUpdateRequest(null);

        // when & then
        mockMvc.perform(patch(
                        "/api/goods/{goodsId}/status",
                        1L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.status")
                        .value("상태값은 필수입니다."));

        verify(goodsService, never())
                .updateGoodsStatus(any(), any());
    }

    private MockMultipartFile jsonPart(
            String name,
            Object request
    ) throws Exception {
        return new MockMultipartFile(
                name,
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );
    }
}