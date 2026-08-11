package com.team3.gudit.domain.goodsSales.service;

import com.team3.gudit.domain.goodsSales.dto.SaleResponseDto;

public interface InventoryService {

    SaleResponseDto getSaleInfo(Long saleId);

    void decreaseStock(Long saleId, int quantity);

    void restoreStock(Long saleId, int quantity);
}