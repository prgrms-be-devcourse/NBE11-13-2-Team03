package com.team3.gudit.purchase.scheduler;

import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseTimeoutScheduler {

    private final PurchaseRepository purchaseRepository;
    private final InventoryService inventoryService;
    private final PaymentRepository paymentRepository;

    // 1분마다 실행
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void cancelExpiredPurchases() {
        // 기준 시간: 현재 시간으로부터 10분 전
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        // 10분이 지났는데 여전히 PENDING_PAYMENT인 주문 탐색
        List<Purchase> expiredPurchases = purchaseRepository
                .findAllByStatusAndCreatedAtBefore(PurchaseStatus.PENDING_PAYMENT, threshold);

        if (expiredPurchases.isEmpty()) {
            return;
        }

        log.info("미결제 타임아웃 처리 대상: {}건", expiredPurchases.size());

        for (Purchase purchase : expiredPurchases) {
            try {
                //목록 조회 시점과 실제 처리 시점 사이에 사용자가 취소할 수 있으므로, 반복문 안에서 Purchase를 잠금 조회
                Purchase lockedPurchase = purchaseRepository
                        .findByIdWithLock(purchase.getId())
                        .orElse(null);

                if (lockedPurchase == null) {
                    continue;
                }

                // 잠금을 얻기 전에 다른 요청이 처리했을 수 있으므로 재확인
                if (lockedPurchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
                    continue;
                }

                Payment payment = paymentRepository
                                .findByPurchaseId(lockedPurchase.getId())
                                .orElse(null);

                if (payment == null) {
                    log.error(
                            "타임아웃 대상의 결제 정보가 없습니다: purchaseId={}",
                            lockedPurchase.getId()
                    );
                    continue;
                }

                // 아직 결제가 시작되지 않은 READY 상태만 timeout 처리
                if (payment.getStatus() != PaymentStatus.READY) {

                    log.info(
                            "결제 진행 중이거나 처리된 구매는 timeout에서 제외: "
                                    + "purchaseId={}, paymentStatus={}",
                            lockedPurchase.getId(),
                            payment.getStatus()
                    );

                    continue;
                }

                // 결제 시작 전 READY 상태의 Payment를 취소 처리
                payment.cancelReady();

                // Redis/DB 재고 및 중복 구매 제한 복원
                inventoryService.restoreStock(
                        purchase.getSale().getId(),
                        purchase.getUser().getId(),
                        purchase.getQuantity()
                );

                // Purchase 상태 변경 (PENDING_PAYMENT -> CANCELED)
                purchase.cancel();

                log.info("미결제 타임아웃 처리 완료: purchaseId={}", purchase.getId());
            } catch (Exception e) {
                log.error("미결제 타임아웃 처리 중 예외 발생: purchaseId={}", purchase.getId(), e);
            }
        }
    }
}