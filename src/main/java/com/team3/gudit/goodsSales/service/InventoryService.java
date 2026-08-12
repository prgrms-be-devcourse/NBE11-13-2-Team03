package com.team3.gudit.domain.goodsSales.service;

public interface InventoryService {


    void decreaseStock(Long saleId, int quantity);

    void restoreStock(Long saleId, int quantity);
}