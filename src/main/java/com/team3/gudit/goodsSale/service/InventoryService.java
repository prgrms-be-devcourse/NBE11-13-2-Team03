package com.team3.gudit.goodsSale.service;

public interface InventoryService {


    void decreaseStock(Long saleId, int quantity);

    void restoreStock(Long saleId, int quantity);
}