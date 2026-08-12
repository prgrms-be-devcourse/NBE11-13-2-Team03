package com.team3.gudit.domain.goodsSales.controller;

import com.team3.gudit.domain.goodsSales.dto.*;
import com.team3.gudit.domain.goodsSales.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleApiController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleCreateResponseDto> createSale(@RequestBody SaleCreateRequestDto request) {
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
            @RequestBody SaleUpdateRequestDto request) {
        SaleDetailResponseDto response = saleService.updateSale(saleId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{saleId}/status")
    public ResponseEntity<SaleStatusUpdateResponseDto> updateSaleStatus(
            @PathVariable Long saleId,
            @RequestBody SaleStatusUpdateRequestDto request) {
        SaleStatusUpdateResponseDto response = saleService.updateSaleStatus(saleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{saleId}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long saleId) {
        saleService.deleteSale(saleId);
        return ResponseEntity.noContent().build();
    }
}
