package com.team3.gudit.sale.scheduler;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaleStockSyncScheduler {

    private final SaleRepository saleRepository;
    private final SaleService saleService;

    @Scheduled(fixedDelay = 300000)
    public void syncFinalRemainingStocks() {
        LocalDateTime finalSyncThreshold =
                LocalDateTime.now().minusDays(1);

        List<Sale> targets =
                saleRepository
                        .findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
                                SaleStatus.CLOSED,
                                finalSyncThreshold
                        );

        for (Sale sale : targets) {
            try {
                boolean synced =
                        saleService.syncFinalRemainingStock(
                                sale.getId()
                        );

                if (synced) {
                    log.info(
                            "[판매 최종 재고 동기화 완료] saleId={}",
                            sale.getId()
                    );
                }
            } catch (Exception e) {
                log.error(
                        "[판매 최종 재고 동기화 실패] saleId={}",
                        sale.getId(),
                        e
                );
            }
        }
    }
}