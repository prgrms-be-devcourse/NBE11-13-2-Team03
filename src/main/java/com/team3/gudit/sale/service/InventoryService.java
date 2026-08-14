package com.team3.gudit.sale.service;

import com.team3.gudit.sale.domain.entity.Sale;

public interface InventoryService {


    void decreaseStock(Long saleId, Long userId, int quantity);

    void restoreStock(Long saleId, Long userId, int quantity);
}