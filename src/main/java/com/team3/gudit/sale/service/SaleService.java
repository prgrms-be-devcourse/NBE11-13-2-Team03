package com.team3.gudit.sale.service;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.sale.dto.response.SaleListResponseDto;
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto;

import java.util.List;

public interface SaleService {

    SaleCreateResponseDto createSale(SaleCreateRequestDto request);

    SaleDetailResponseDto saleDetail(Long id);

    List<SaleListResponseDto> saleList();

    SaleDetailResponseDto updateSale(Long saleId, SaleUpdateRequestDto request);

    SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request);

    void deleteSale(Long id);

    void warmupSaleInfo(Long id);

    void startSale(Long id);

    void endSale(Long id);

    boolean syncFinalRemainingStock(Long saleId);
}
