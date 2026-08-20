package com.team3.gudit.purchase.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.service.PaymentService;
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
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.exception.SaleErrorCode;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final PaymentService paymentService;

    @Transactional
    public PurchaseCreateResponse purchase(Long userId, Long saleId) {



        if (purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(
                userId,
                saleId,
                PurchaseStatus.CANCELED
        )) {
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

        inventoryService.decreaseStock(
                saleId,
                userId,
                1
        );

        registerStockRollback(
                saleId,
                userId,
                1
        );

        int purchasePrice = sale.getGoods().getPrice();

        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                purchasePrice
        );

        Purchase savedPurchase = purchaseRepository.save(purchase);

        Payment payment = paymentService.createPayment(savedPurchase);

        return new PurchaseCreateResponse(
                savedPurchase.getId(),
                savedPurchase.getSale().getId(),
                savedPurchase.getQuantity(),
                savedPurchase.getPurchasePrice(),
                savedPurchase.getStatus(),
                savedPurchase.getPurchasedAt(),
                payment.getOrderId()
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

/*        Purchase purchase = purchaseRepository.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new BusinessException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND,
                        "Purchase not found. purchaseId=" + purchaseId
                ));*/

        Purchase purchase = purchaseRepository.findByIdAndUserIdWithLock(purchaseId, userId)
                .orElseThrow(() -> new BusinessException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND,
                        "Purchase not found. purchaseId=" + purchaseId
                ));

        if (purchase.getStatus() == PurchaseStatus.CANCELED) {
            throw new BusinessException(
                    PurchaseErrorCode.PURCHASE_ALREADY_CANCELED
            );
        }

        validateCancellationPeriod(purchase);

        if (purchase.getStatus() == PurchaseStatus.PENDING_PAYMENT) {
            cancelPendingPayment(purchase, userId);
        } else if (purchase.getStatus() == PurchaseStatus.PURCHASED) {
            cancelCompletedPayment(purchase, userId);
        }

        return new PurchaseCancelResponse(
                purchase.getId(),
                purchase.getStatus(),
                purchase.getCanceledAt()
        );
    }

    private void cancelPendingPayment(Purchase purchase, Long userId) {

        Payment payment = paymentService.getPaymentByPurchaseId(
                        purchase.getId()
                );

        // 결제가 시작되지 않은 READY 상태에서만 즉시 취소 가능
        payment.cancelReady();

        inventoryService.restoreStock(
                purchase.getSale().getId(),
                userId,
                purchase.getQuantity()
        );

        purchase.cancel();
    }

    private void cancelCompletedPayment(Purchase purchase, Long userId) {

        Payment payment = paymentService.getPaymentByPurchaseId(
                purchase.getId()
        );

        paymentService.cancelCompletedPayment(
                payment.getPaymentKey()
        );

        inventoryService.restoreStock(
                purchase.getSale().getId(),
                userId,
                purchase.getQuantity()
        );

        purchase.cancel();
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

    private void registerStockRollback(
            Long saleId,
            Long userId,
            int quantity
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            inventoryService.restoreStock(
                                    saleId,
                                    userId,
                                    quantity
                            );
                        }
                    }
                }
        );
    }

    private void validateCancellationPeriod(Purchase purchase) {
        LocalDateTime cancellationDeadline =
                purchase.getSale()
                        .getEndAt()
                        .plusDays(1);

        if (!LocalDateTime.now().isBefore(cancellationDeadline)) {
            throw new BusinessException(
                    PurchaseErrorCode
                            .PURCHASE_CANCELLATION_PERIOD_EXPIRED
            );
        }
    }
}