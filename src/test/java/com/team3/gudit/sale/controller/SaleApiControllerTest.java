package com.team3.gudit.sale.controller;

import tools.jackson.databind.ObjectMapper;
import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.sale.exception.SaleErrorCode;
import com.team3.gudit.sale.service.SaleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SaleApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class SaleApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SaleService saleService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("올바른 판매 생성 요청은 201 응답을 반환한다")
    void createSale() throws Exception {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        SaleCreateRequestDto request =
                new SaleCreateRequestDto(
                        10L,
                        100,
                        2,
                        startAt,
                        endAt
                );

        SaleCreateResponseDto response =
                new SaleCreateResponseDto(
                        10L,
                        100,
                        2,
                        startAt,
                        endAt
                );

        given(saleService.createSale(any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goodsId").value(10L))
                .andExpect(jsonPath("$.initialStock").value(100))
                .andExpect(jsonPath("$.maxPurchaseQuantity").value(2));

        verify(saleService).createSale(any());
    }

    @Test
    @DisplayName("초기 재고가 0인 판매 생성 요청은 400 응답을 반환한다")
    void createSaleWithZeroInitialStock() throws Exception {
        // given
        SaleCreateRequestDto request =
                new SaleCreateRequestDto(
                        10L,
                        0,
                        2,
                        LocalDateTime.of(2026, 8, 20, 10, 0),
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                );

        // when & then
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath(
                        "$.fieldErrors.initialStock"
                ).value("초기 재고는 1개 이상이어야 합니다."));

        verify(saleService, never())
                .createSale(any());
    }

    @Test
    @DisplayName("필수 판매 생성 정보가 null이면 400 응답을 반환한다")
    void createSaleWithNullValues() throws Exception {
        // given
        String request = """
                {
                  "goodsId": null,
                  "initialStock": null,
                  "maxPurchaseQuantity": null,
                  "startAt": null,
                  "endAt": null
                }
                """;

        // when & then
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.goodsId")
                        .exists())
                .andExpect(jsonPath("$.fieldErrors.initialStock")
                        .exists())
                .andExpect(jsonPath(
                        "$.fieldErrors.maxPurchaseQuantity"
                ).exists())
                .andExpect(jsonPath("$.fieldErrors.startAt")
                        .exists())
                .andExpect(jsonPath("$.fieldErrors.endAt")
                        .exists());

        verify(saleService, never())
                .createSale(any());
    }

    @Test
    @DisplayName("판매 전체 수정은 PUT 요청으로 처리한다")
    void updateSale() throws Exception {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 21, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 21, 12, 0);

        SaleUpdateRequestDto request =
                new SaleUpdateRequestDto(
                        200,
                        5,
                        startAt,
                        endAt
                );

        SaleDetailResponseDto response =
                SaleDetailResponseDto.builder()
                        .id(1L)
                        .goodsId(10L)
                        .goodsName("테스트 상품")
                        .price(10_000)
                        .initialStock(200)
                        .remainingStock(200)
                        .maxPurchaseQuantity(5)
                        .status(SaleStatus.READY)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build();

        given(saleService.updateSale(
                any(Long.class),
                any(SaleUpdateRequestDto.class)
        )).willReturn(response);

        // when & then
        mockMvc.perform(put("/api/sales/{saleId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.initialStock").value(200))
                .andExpect(jsonPath(
                        "$.maxPurchaseQuantity"
                ).value(5));

        verify(saleService)
                .updateSale(
                        any(Long.class),
                        any(SaleUpdateRequestDto.class)
                );
    }

    @Test
    @DisplayName("판매 전체 수정 필수값이 누락되면 400 응답을 반환한다")
    void updateSaleWithMissingValues() throws Exception {
        // given
        String request = """
                {
                  "initialStock": null,
                  "maxPurchaseQuantity": null,
                  "startAt": null,
                  "endAt": null
                }
                """;

        // when & then
        mockMvc.perform(put("/api/sales/{saleId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.initialStock")
                        .exists())
                .andExpect(jsonPath(
                        "$.fieldErrors.maxPurchaseQuantity"
                ).exists())
                .andExpect(jsonPath("$.fieldErrors.startAt")
                        .exists())
                .andExpect(jsonPath("$.fieldErrors.endAt")
                        .exists());

        verify(saleService, never())
                .updateSale(any(), any());
    }

    @Test
    @DisplayName("잘못된 판매 기간은 400 응답으로 변환한다")
    void updateSaleWithInvalidSalePeriod() throws Exception {
        // given
        SaleUpdateRequestDto request =
                new SaleUpdateRequestDto(
                        100,
                        2,
                        LocalDateTime.of(2026, 8, 21, 12, 0),
                        LocalDateTime.of(2026, 8, 21, 10, 0)
                );

        willThrow(new BusinessException(
                SaleErrorCode.INVALID_SALE_PERIOD
        ))
                .given(saleService)
                .updateSale(
                        any(Long.class),
                        any(SaleUpdateRequestDto.class)
                );

        // when & then
        mockMvc.perform(put("/api/sales/{saleId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("SALE_001"));
    }

    @Test
    @DisplayName("판매 상태가 null이면 400 응답을 반환한다")
    void updateSaleStatusWithNullStatus() throws Exception {
        // given
        SaleStatusUpdateRequestDto request =
                new SaleStatusUpdateRequestDto(null);

        // when & then
        mockMvc.perform(patch(
                        "/api/sales/{saleId}/status",
                        1L
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors.status")
                        .value("판매 상태는 필수입니다."));

        verify(saleService, never())
                .updateSaleStatus(any(), any());
    }

    @Test
    @DisplayName("관리자는 수동 Redis Warm-up을 실행할 수 있다")
    void warmupSale() throws Exception {
        // when & then
        mockMvc.perform(post(
                        "/api/sales/{saleId}/warmup",
                        1L
                ))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "타임세일(id=1) 수동 Warm-up이 완료되었습니다."
                ));

        verify(saleService).warmupSaleInfo(1L);
    }
}