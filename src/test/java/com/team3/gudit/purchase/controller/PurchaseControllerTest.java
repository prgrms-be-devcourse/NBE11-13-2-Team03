package com.team3.gudit.purchase.controller;

import com.team3.gudit.auth.jwt.TokenProvider;
import com.team3.gudit.auth.security.CustomUserDetails;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.dto.PurchaseDetailResponse;
import com.team3.gudit.purchase.dto.PurchaseListResponse;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.service.PurchaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseController.class)
public class PurchaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseService purchaseService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("로그인 사용자가 판매 상품을 구매한다")
    void purchase() throws Exception {
        // given
        Long userId = 1L;
        Long saleId = 10L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getUserId()).willReturn(userId);

        PurchaseCreateResponse response = new PurchaseCreateResponse(
                100L,
                saleId,
                1,
                15000,
                PurchaseStatus.PURCHASED,
                LocalDateTime.now()
        );

        given(purchaseService.purchase(userId, saleId))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/sales/{saleId}/purchases", saleId)
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleId").value(saleId))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.purchasePrice").value(15000));

        verify(purchaseService).purchase(userId, saleId);
    }

    @Test
    @DisplayName("로그인 사용자의 구매 목록을 조회한다")
    void getMyPurchases() throws Exception {
        // given
        Long userId = 1L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getUserId()).willReturn(userId);

        PurchaseListResponse response = new PurchaseListResponse(
                List.of()
        );

        given(purchaseService.getMyPurchases(userId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/purchases")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchases").isArray());

        verify(purchaseService).getMyPurchases(userId);
    }

    @Test
    @DisplayName("로그인 사용자가 자신의 구매 상세 내역을 조회한다")
    void getPurchase() throws Exception {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;
        Long saleId = 10L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getUserId()).willReturn(userId);

        PurchaseDetailResponse response = new PurchaseDetailResponse(
                purchaseId,
                saleId,
                20L,
                "테스트 굿즈",
                "https://example.com/image.jpg",
                1,
                15000,
                PurchaseStatus.PURCHASED,
                LocalDateTime.now(),
                null
        );

        given(purchaseService.getPurchase(userId, purchaseId))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/purchases/{purchaseId}", purchaseId)
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseId").value(purchaseId))
                .andExpect(jsonPath("$.saleId").value(saleId))
                .andExpect(jsonPath("$.goodsName").value("테스트 굿즈"))
                .andExpect(jsonPath("$.purchasePrice").value(15000))
                .andExpect(jsonPath("$.status").value("PURCHASED"));

        verify(purchaseService).getPurchase(userId, purchaseId);
    }

    @Test
    @DisplayName("로그인 사용자가 자신의 구매를 취소한다")
    void cancel() throws Exception {
        // given
        Long userId = 1L;
        Long purchaseId = 100L;

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getUserId()).willReturn(userId);

        PurchaseCancelResponse response = new PurchaseCancelResponse(
                purchaseId,
                PurchaseStatus.CANCELED,
                LocalDateTime.now()
        );

        given(purchaseService.cancel(userId, purchaseId))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/purchases/{purchaseId}/cancel", purchaseId)
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseId").value(purchaseId))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.canceledAt").isNotEmpty());

        verify(purchaseService).cancel(userId, purchaseId);
    }
}
