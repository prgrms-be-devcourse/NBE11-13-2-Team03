package com.team3.gudit.goodsSale.service;

import com.team3.gudit.goodsSale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.goodsSale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.goodsSale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.goodsSale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.goodsSale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.goodsSale.dto.response.SaleListResponseDto;
import com.team3.gudit.goodsSale.dto.response.SaleStatusUpdateResponseDto;

import java.util.List;

public interface SaleService {

    SaleCreateResponseDto createSale(SaleCreateRequestDto request);

    SaleDetailResponseDto saleDetail(Long id);

    List<SaleListResponseDto> saleList();

    SaleDetailResponseDto updateSale(Long saleId, SaleUpdateRequestDto request);

    SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request);

    void deleteSale(Long id);

}
