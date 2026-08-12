package com.team3.gudit.goodsSales.service;

import com.team3.gudit.goodsSales.domain.entity.Sale;
import com.team3.gudit.goodsSales.domain.repository.SaleRepository;
import com.team3.gudit.global.exception.SaleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final SaleRepository saleRepository;

    @Override
    @Transactional
    public void decreaseStock(Long saleId, int quantity) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        sale.validateSalePeriod();
        sale.validatePurchaseQuantity(quantity);
        sale.decreaseStock(quantity);
    }

    @Override
    @Transactional
    public void restoreStock(Long saleId, int quantity) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));

        sale.restoreStock(quantity);
    }

}