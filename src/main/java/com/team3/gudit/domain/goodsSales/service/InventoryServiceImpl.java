package com.team3.gudit.domain.goodsSales.service;

import com.team3.gudit.domain.goodsSales.domain.entity.Sale;
import com.team3.gudit.domain.goodsSales.domain.repository.SaleRepository;
import com.team3.gudit.domain.goodsSales.dto.SaleResponseDto;
import com.team3.gudit.global.exception.SaleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Primary // 우선 RDB 구현체를 기본 Bean으로 주입
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final SaleRepository saleRepository;

    @Override
    public SaleResponseDto getSaleInfo(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));

        return SaleResponseDto.from(sale);
    }

    @Override
    @Transactional
    public void decreaseStock(Long saleId, int quantity) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));

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