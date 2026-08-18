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

    // 30초 마다 실행 (30,000 ms)
    @Scheduled(fixedDelay = 30000)
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
                saleService.warmupSaleInfo(sale.getId());

                log.info("[자동 Warm-up 완료] saleId: {}, startAt: {}", sale.getId(), sale.getStartAt());
            } catch (Exception e) {
                log.error("[자동 Warm-up 실패] saleId: {}", sale.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void startSales() {
        LocalDateTime now = LocalDateTime.now();

        List<Sale> sales = saleRepository
                .findByStatusAndStartAtLessThanEqual(
                        SaleStatus.READY,
                        now,
                        now
                );

        for (Sale sale : sales) {
            try {
                // 웜업 스케줄을 놓친 경우도 대비
                saleService.warmupSaleInfo(sale.getId());

                saleService.startSale(sale.getId());

                log.info(
                        "[자동 판매 시작 완료] saleId: {}, startAt: {}",
                        sale.getId(),
                        sale.getStartAt()
                );
            } catch (Exception e) {
                log.error(
                        "[자동 판매 시작 실패] saleId: {}",
                        sale.getId(),
                        e
                );
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void endSales() {
        LocalDateTime now = LocalDateTime.now();

        List<Sale> sales = saleRepository
                .findByStatusAndEndAtLessThanEqual(
                        SaleStatus.ON_SALE,
                        now
                );

        for (Sale sale : sales) {
            try {
                saleService.endSale(sale.getId());

                log.info(
                        "[자동 판매 종료 완료] saleId: {}, endAt: {}",
                        sale.getId(),
                        sale.getEndAt()
                );
            } catch (Exception e) {
                log.error(
                        "[자동 판매 종료 실패] saleId: {}",
                        sale.getId(),
                        e
                );
            }
        }
    }
}