package com.team3.gudit.purchase.service;

import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseTimeoutService {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;

    /**
     * timeout 대상 Purchase 한 건을 독립된 트랜잭션으로 처리한다.
     *
     * @return 실제 timeout 취소를 처리했으면 true,
     *         다른 요청에서 이미 처리했거나 대상이 아니면 false
     */
    @Transactional
    public boolean cancelExpiredPurchase(
            Long purchaseId
    ) {
        // 사용자 취소, 결제 실패와의 동시 처리를 막기 위해 Purchase 한 건을 비관적 락으로 조회
        Purchase lockedPurchase = purchaseRepository
                .findByIdWithLock(purchaseId)
                .orElse(null);

        if (lockedPurchase == null) {
            log.warn(
                    "[구매 timeout 처리 생략] "
                            + "Purchase를 찾을 수 없습니다. "
                            + "purchaseId={}",
                    purchaseId
            );

            return false;
        }

        // 목록 조회 이후 다른 요청이 처리했을 수 있으므로 잠금을 획득한 뒤 최신 상태를 다시 확인
        if (lockedPurchase.getStatus()
                != PurchaseStatus.PENDING_PAYMENT) {

            log.info(
                    "[구매 timeout 처리 생략] "
                            + "이미 처리된 Purchase입니다. "
                            + "purchaseId={}, purchaseStatus={}",
                    lockedPurchase.getId(),
                    lockedPurchase.getStatus()
            );

            return false;
        }

        Payment payment = paymentRepository
                .findByPurchaseId(lockedPurchase.getId())
                .orElse(null);

        if (payment == null) {
            log.error(
                    "[구매 timeout 처리 실패] "
                            + "결제 정보를 찾을 수 없습니다. "
                            + "purchaseId={}",
                    lockedPurchase.getId()
            );

            return false;
        }

        // 아직 외부 결제가 시작되지 않은 READY Payment만 timeout으로 취소
        if (payment.getStatus() != PaymentStatus.READY) {
            log.info(
                    "[구매 timeout 처리 생략] "
                            + "결제가 진행 중이거나 이미 처리되었습니다. "
                            + "purchaseId={}, paymentStatus={}",
                    lockedPurchase.getId(),
                    payment.getStatus()
            );

            return false;
        }

        // 아래 처리는 한 트랜잭션에서 수행한다.
        // restoreStock에서 예외가 발생하면 Payment 변경도 롤백된다.
        payment.cancelReady();

        inventoryService.restoreStock(
                lockedPurchase.getSale().getId(),
                lockedPurchase.getUser().getId(),
                lockedPurchase.getQuantity()
        );

        lockedPurchase.cancel();

        return true;
    }
}