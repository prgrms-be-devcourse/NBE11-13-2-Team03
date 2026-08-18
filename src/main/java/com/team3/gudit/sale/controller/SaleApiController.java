package com.team3.gudit.sale.controller;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.sale.dto.response.SaleListResponseDto;
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto;
import com.team3.gudit.sale.exception.SaleErrorCode;
import com.team3.gudit.sale.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleApiController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleCreateResponseDto> createSale(
            @Valid @RequestBody SaleCreateRequestDto request) {
        SaleCreateResponseDto response = saleService.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<SaleDetailResponseDto> getSaleDetail(@PathVariable Long saleId) {
        SaleDetailResponseDto response = saleService.saleDetail(saleId);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping
    public ResponseEntity<List<SaleListResponseDto>> getSaleList() {
        List<SaleListResponseDto> response = saleService.saleList();
        return ResponseEntity.ok().body(response);
    }

    @PutMapping("/{saleId}")
    public ResponseEntity<SaleDetailResponseDto> updateSale(
            @PathVariable Long saleId,
            @Valid @RequestBody SaleUpdateRequestDto request) {
        SaleDetailResponseDto response = saleService.updateSale(saleId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{saleId}/status")
    public ResponseEntity<SaleStatusUpdateResponseDto> updateSaleStatus(
            @PathVariable Long saleId,
            @Valid @RequestBody SaleStatusUpdateRequestDto request) {
        SaleStatusUpdateResponseDto response = saleService.updateSaleStatus(saleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{saleId}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long saleId) {
        saleService.deleteSale(saleId);
        return ResponseEntity.noContent().build();
    }

    // Redis warmup 스케줄러 작동 오류 시 관리자가 수동으로 warmup
    @PostMapping("/{saleId}/warmup")
    public ResponseEntity<String> warmupSale(@PathVariable Long saleId) {
        // 1. Redis 웜업 실행 (재고, 정책 캐싱)
        saleService.warmupSaleInfo(saleId);

        return ResponseEntity.ok("타임세일(id=" + saleId + ") 수동 Warm-up이 완료되었습니다.");
    }
}
