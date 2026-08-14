package com.team3.gudit.sale.service;

public interface InventoryService {


    void decreaseStock(Long saleId, Long userId, int quantity);

    void restoreStock(Long saleId, Long userId, int quantity);
}