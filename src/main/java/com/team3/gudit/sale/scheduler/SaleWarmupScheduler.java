package com.team3.gudit.sale.scheduler;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.sale.service.SaleService;
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
public class SaleWarmupScheduler {

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;
    private final SaleService saleService;

    // 5분마다 실행 (300,000 ms)
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void autoWarmupSales() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = now.plusMinutes(10); // 지금부터 10분 뒤 이내 시작 건 탐색

        // 1. 시작 10분 전 이내이고, 상태가 READY인 타임세일 조회
        List<Sale> upcomingSales = saleRepository.findByStatusAndStartAtBetween(
                SaleStatus.READY,
                now,
                targetTime
        );

        for (Sale sale : upcomingSales) {
            try {
                // 2. Redis 웜업 수행 (RedisInventoryServiceImpl.warmupSaleInfo 호출)
                saleService.warmupSaleInfo(sale.getId());

                // 3. DB 상태 업데이트 (READY -> ON_SALE)
                // Sale 엔티티의 도메인 메서드 사용
                sale.updateSaleStatus(SaleStatus.ON_SALE);

                log.info("[자동 Warm-up 완료] saleId: {}, startAt: {}", sale.getId(), sale.getStartAt());
            } catch (Exception e) {
                log.error("[자동 Warm-up 실패] saleId: {}", sale.getId(), e);
            }
        }
    }
}