package com.team3.gudit.domain.goodsSales.service;

import com.team3.gudit.domain.goodsSales.dto.*;

import java.util.List;

public interface SaleService {

    SaleCreateResponseDto createSale(SaleCreateRequestDto request);

    SaleDetailResponseDto saleDetail(Long id);

    List<SaleListResponseDto> saleList();

    SaleDetailResponseDto updateSale(Long saleId, SaleUpdateRequestDto request);

    SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request);

    void deleteSale(Long id);

}
