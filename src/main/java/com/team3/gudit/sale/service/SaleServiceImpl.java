package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.goods.exception.GoodsErrorCode;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.sale.dto.response.SaleListResponseDto;
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto;
import com.team3.gudit.sale.exception.SaleErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {
    private final SaleRepository saleRepository;
    private final GoodsRepository goodsRepository;

    @Override
    @Transactional
    public SaleCreateResponseDto createSale(SaleCreateRequestDto request) {
        Goods goods = goodsRepository.findById(request.goodsId())
                .orElseThrow(() -> new BusinessException(GoodsErrorCode.GOODS_NOT_FOUND));

        Sale sale = request.toEntity(goods);


        Sale savedSale = saleRepository.save(sale);
        return SaleCreateResponseDto.from(savedSale);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleDetailResponseDto saleDetail(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        return SaleDetailResponseDto.from(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleListResponseDto> saleList() {
        return saleRepository.findAll().stream()
                .map(SaleListResponseDto::from)
                .toList();
    }

    @Override
    @Transactional
    public SaleDetailResponseDto updateSale(Long saleId, SaleUpdateRequestDto request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        sale.updateSaleInfo(
                request.initialStock(),
                request.maxPurchaseQuantity(),
                request.startAt(),
                request.endAt()
        );

        return SaleDetailResponseDto.from(sale);
    }

    @Override
    @Transactional
    public SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        sale.updateSaleStatus(request.status());

        return SaleStatusUpdateResponseDto.from(sale);
    }

    @Override
    @Transactional
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        sale.deleteSale();
    }
}
