package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.exception.SaleErrorCode;
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
    public void decreaseStock(Long saleId, Long userId, int quantity) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));
        sale.validateSalePeriod();
        sale.validatePurchaseQuantity(quantity);
        sale.decreaseStock(quantity);
    }

    @Override
    @Transactional
    public void restoreStock(Long saleId, Long userId, int quantity) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        sale.restoreStock(quantity);
    }

}