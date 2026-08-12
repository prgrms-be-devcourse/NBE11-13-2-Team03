package com.team3.gudit.purchase.service;

import com.team3.gudit.domain.goodsSales.domain.entity.Sale;
import com.team3.gudit.domain.goodsSales.domain.enums.SaleStatus;
import com.team3.gudit.domain.goodsSales.domain.repository.SaleRepository;
import com.team3.gudit.domain.goodsSales.service.InventoryService;
import com.team3.gudit.global.exception.*;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.dto.PurchaseDetailResponse;
import com.team3.gudit.purchase.dto.PurchaseListResponse;
import com.team3.gudit.purchase.dto.PurchaseSummaryResponse;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;

    @Transactional
    public PurchaseCreateResponse purchase(Long userId, Long saleId) {

        if (purchaseRepository.existsByUserIdAndSaleId(userId, saleId)) {
            throw new DuplicatePurchaseException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(sale.getStartAt())) {
            throw new SaleUnavailableException("아직 판매 시작 전입니다.");
        }

        if (!now.isBefore(sale.getEndAt())) {
            throw new SaleUnavailableException("판매가 종료되었습니다.");
        }

        if (sale.getStatus() != SaleStatus.ON_SALE) {
            throw new SaleUnavailableException("현재 구매할 수 없는 판매입니다.");
        }

        inventoryService.decreaseStock(saleId, 1);

        int purchasePrice = sale.getGoods().getPrice();

        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                purchasePrice
        );

        Purchase savedPurchase = purchaseRepository.save(purchase);

        return new PurchaseCreateResponse(
                savedPurchase.getId(),
                savedPurchase.getSale().getId(),
                savedPurchase.getQuantity(),
                savedPurchase.getPurchasePrice(),
                savedPurchase.getStatus(),
                savedPurchase.getPurchasedAt()
        );
    }

    public PurchaseListResponse getMyPurchases(Long userId) {

        List<PurchaseSummaryResponse> purchases =
                purchaseRepository.findAllByUserId(userId)
                        .stream()
                        .map(this::toSummaryResponse)
                        .toList();

        return new PurchaseListResponse(purchases);
    }

    public PurchaseDetailResponse getPurchase(Long userId, Long purchaseId) {

        Purchase purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));

        return toDetailResponse(purchase);
    }

    @Transactional
    public PurchaseCancelResponse cancel(Long userId, Long purchaseId) {

        Purchase purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));

        if (purchase.getStatus() == PurchaseStatus.CANCELED) {
            throw new AlreadyCanceledPurchaseException();
        }

        Sale sale = purchase.getSale();
        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(sale.getEndAt())) {
            throw new SaleUnavailableException("판매가 종료되어 구매를 취소할 수 없습니다.");
        }

        inventoryService.restoreStock(
                sale.getId(),
                purchase.getQuantity()
        );

        purchase.cancel();

        return new PurchaseCancelResponse(
                purchase.getId(),
                purchase.getStatus(),
                purchase.getCanceledAt()
        );
    }

    private PurchaseSummaryResponse toSummaryResponse(Purchase purchase) {
        return new PurchaseSummaryResponse(
                purchase.getId(),
                purchase.getSale().getId(),
                purchase.getSale().getGoods().getId(),
                purchase.getSale().getGoods().getName(),
                purchase.getSale().getGoods().getImageUrl(),
                purchase.getQuantity(),
                purchase.getPurchasePrice(),
                purchase.getStatus(),
                purchase.getPurchasedAt()
        );
    }

    private PurchaseDetailResponse toDetailResponse(Purchase purchase) {
        return new PurchaseDetailResponse(
                purchase.getId(),
                purchase.getSale().getId(),
                purchase.getSale().getGoods().getId(),
                purchase.getSale().getGoods().getName(),
                purchase.getSale().getGoods().getImageUrl(),
                purchase.getQuantity(),
                purchase.getPurchasePrice(),
                purchase.getStatus(),
                purchase.getPurchasedAt(),
                purchase.getCanceledAt()
        );
    }
}