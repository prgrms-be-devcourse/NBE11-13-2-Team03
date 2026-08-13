package com.team3.gudit.sale.service;

public interface InventoryService {


    void decreaseStock(Long saleId, int quantity);

    void restoreStock(Long saleId, int quantity);
}