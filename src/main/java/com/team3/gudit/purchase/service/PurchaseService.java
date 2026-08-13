package com.team3.gudit.purchase.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.dto.PurchaseDetailResponse;
import com.team3.gudit.purchase.dto.PurchaseListResponse;
import com.team3.gudit.purchase.dto.PurchaseSummaryResponse;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.exception.SaleErrorCode;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.user.exception.UserErrorCode;
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
            throw new BusinessException(PurchaseErrorCode.DUPLICATE_PURCHASE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        UserErrorCode.USER_NOT_FOUND,
                        "User not found. userId=" + userId
                ));

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(
                        SaleErrorCode.SALE_NOT_FOUND,
                        "Sale not found. saleId=" + saleId
                ));

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(sale.getStartAt()) || !now.isBefore(sale.getEndAt())) {
            throw new BusinessException(SaleErrorCode.INVALID_SALE_PERIOD);
        }

        if (sale.getStatus() == SaleStatus.SOLD_OUT) {
            throw new BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK);
        }

        if (sale.getStatus() != SaleStatus.ON_SALE) {
            throw new BusinessException(SaleErrorCode.SALE_CLOSED);
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
                .orElseThrow(() -> new BusinessException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND,
                        "Purchase not found. purchaseId=" + purchaseId
                ));

        return toDetailResponse(purchase);
    }

    @Transactional
    public PurchaseCancelResponse cancel(Long userId, Long purchaseId) {

        Purchase purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new BusinessException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND,
                        "Purchase not found. purchaseId=" + purchaseId
                ));

        if (purchase.getStatus() == PurchaseStatus.CANCELED) {
            throw new BusinessException(PurchaseErrorCode.PURCHASE_ALREADY_CANCELED);
        }

        Sale sale = purchase.getSale();
        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(sale.getEndAt())) {
            throw new BusinessException(PurchaseErrorCode.PURCHASE_CANNOT_CANCEL);
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