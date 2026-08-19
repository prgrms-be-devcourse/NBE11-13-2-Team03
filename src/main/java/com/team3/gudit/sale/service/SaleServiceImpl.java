package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.global.exception.GlobalErrorCode;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.goods.exception.GoodsErrorCode;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.dto.SaleRedisDto;
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto;
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto;
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto;
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto;
import com.team3.gudit.sale.dto.response.SaleListResponseDto;
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto;
import com.team3.gudit.sale.exception.SaleErrorCode;
import lombok.RequiredArgsConstructor;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {
    private final SaleRepository saleRepository;
    private final GoodsRepository goodsRepository;
    private final StringRedisTemplate redisTemplate;
    private final PurchaseRepository purchaseRepository;

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

        // 판매 중일 때만 Redis 실시간 재고 사용
        if (sale.getStatus() == SaleStatus.ON_SALE) {

            Integer redisStock = getRedisStock(id);

            if (redisStock != null) {
                SaleStatus displayStatus = resolveDisplayStatus(sale, redisStock);

                return SaleDetailResponseDto.from(
                        sale,
                        redisStock,
                        displayStatus
                );
            }
        }

        // READY / CLOSED 또는 Redis Key가 없는 경우 RDB 값 사용
        return SaleDetailResponseDto.from(
                sale,
                sale.getRemainingStock(),
                resolveDisplayStatus(
                        sale,
                        sale.getRemainingStock()
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleListResponseDto> saleList() {
        return saleRepository.findAllByGoods_Status(GoodsStatus.ACTIVE)
                .stream()
                .map(sale -> {

                    if (sale.getStatus() == SaleStatus.ON_SALE) {

                        Integer redisStock = getRedisStock(sale.getId());

                        if (redisStock != null) {
                            return SaleListResponseDto.from(
                                    sale,
                                    redisStock,
                                    resolveDisplayStatus(sale, redisStock)
                            );
                        }
                    }

                    return SaleListResponseDto.from(
                            sale,
                            sale.getRemainingStock(),
                            resolveDisplayStatus(
                                    sale,
                                    sale.getRemainingStock()
                            )
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public SaleDetailResponseDto updateSale(Long saleId, SaleUpdateRequestDto request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // READY(판매 대기) 상태에서만 수정 가능 (엔티티 내 validateModifiable 수행)
        sale.updateSaleInfo(
                request.initialStock(),
                request.maxPurchaseQuantity(),
                request.startAt(),
                request.endAt()
        );

        // 변경된 stock/info를 다시 적재하고 TTL도 다시 설정
        warmupSaleInfo(saleId);

        return SaleDetailResponseDto.from(sale);
    }


    @Override
    @Transactional
    public SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // 이미 삭제(DELETED)되었거나 종료(CLOSED)된 상품은 상태를 변경할 수 없음
        sale.updateSaleStatus(request.status());

        return SaleStatusUpdateResponseDto.from(sale);
    }

    // 비상 시 판매 중지로 상태 값 변경하는 메서드
    @Transactional
    public void closeSale(Long saleId) {
        // 1. DB 조회 및 상태 검증
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // 2. DB 상태를 CLOSED로 변경
        sale.updateSaleStatus(SaleStatus.CLOSED);

        // 3. Redis status 즉시 동기화 (Fast-Fail 차단용)
        String infoKey = "sale:" + saleId + ":info";
        redisTemplate.opsForHash().put(infoKey, "status", SaleStatus.CLOSED.name());

    }

    @Override
    @Transactional
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // 진행 중인 판매 상품은 삭제 불가 (엔티티 내부에서 ON_SALE 상태 검증)
        // 삭제 후 상태 값 변경 불가
        // RDB: 데이터 이력 보존 및 어드민 조회를 위해 Soft Delete 처리
        sale.deleteSale();

        // Redis: 삭제된 상품의 재고 Key가 메모리를 차지하지 않도록 즉시 삭제
        String stockKey = "sale:" + id + ":stock";
        String infoKey = "sale:" + id + ":info";

        redisTemplate.delete(
                List.of(stockKey, infoKey)
        );
    }

    @Override
    @Transactional
    public void warmupSaleInfo(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // Warm-up은 판매 시작 전 Redis 데이터를 준비하는 용도이므로
        // READY 상태에서만 허용한다.
        if (sale.getStatus() != SaleStatus.READY) {
            throw new BusinessException(
                    SaleErrorCode.CANNOT_WARMUP_NON_READY_SALE
            );
        }

        String stockKey = "sale:" + id + ":stock";
        String infoKey = "sale:" + id + ":info";

        // 재고 캐싱
        redisTemplate.opsForValue().set(stockKey, String.valueOf(sale.getRemainingStock()));

        // 판매 정보 캐싱
        SaleRedisDto dto = SaleRedisDto.from(sale);
        redisTemplate.opsForHash().putAll(infoKey, dto.toHashFields());

        // 판매 종료 후 2일까지 유지
        LocalDateTime expireAt = sale.getEndAt().plusDays(2);

        Duration ttl = Duration.between(
                LocalDateTime.now(),
                expireAt
        );

        if (ttl.isNegative() || ttl.isZero()) {
            redisTemplate.delete(
                    List.of(stockKey, infoKey)
            );
            return;
        }

        redisTemplate.expire(stockKey, ttl);
        redisTemplate.expire(infoKey, ttl);
    }

    @Override
    @Transactional
    public void startSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(SaleErrorCode.SALE_NOT_FOUND)
                );

        // DB: READY -> ON_SALE
        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // Redis에서도 구매 가능 상태로 전환
        String infoKey = "sale:" + id + ":info";
        redisTemplate.opsForHash().put(
                infoKey,
                "status",
                SaleStatus.ON_SALE.name()
        );
    }

    @Override
    @Transactional
    public void endSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                SaleErrorCode.SALE_NOT_FOUND
                        )
                );

        String stockKey = "sale:" + id + ":stock";
        String infoKey = "sale:" + id + ":info";

        // 1. Redis에서 먼저 신규 구매 차단
        // Redis 연결 자체가 실패하면 예외를 유지해 스케줄러가 재시도한다.
        redisTemplate.opsForHash().put(
                infoKey,
                "status",
                SaleStatus.CLOSED.name()
        );

        // 2. 구매 차단 후 Redis 최종 재고 조회
        // 연결 오류는 여기서 그대로 전파하고, Key 누락만 구분해서 처리한다.
        String redisStock =
                redisTemplate.opsForValue().get(stockKey);

        if (redisStock == null) {
            // Redis 재고를 알 수 없으므로 기존 RDB 재고를 덮어쓰지 않는다.
            // finalStockSyncedAt도 null로 유지해 동기화 미완료 상태로 남긴다.
            log.error(
                    "[판매 종료 재고 동기화 보류] "
                            + "Redis stock Key가 없습니다. "
                            + "saleId={}, stockKey={}, RemainingStock={}",
                    id,
                    stockKey,
                    sale.getRemainingStock()
            );
        } else {
            try {
                int finalRemainingStock =
                        Integer.parseInt(redisStock);

                // Redis 재고가 정상인 경우에만 RDB 1차 동기화
                sale.syncRemainingStock(finalRemainingStock);

                log.info(
                        "[판매 종료 재고 1차 동기화 완료] "
                                + "saleId={}, remainingStock={}",
                        id,
                        finalRemainingStock
                );
            } catch (NumberFormatException e) {
                // 잘못된 Redis 값으로 RDB 재고를 덮어쓰지 않는다.
                // 판매 종료는 계속 진행하고 관리자 확인 대상으로 남긴다.
                log.error(
                        "[판매 종료 재고 동기화 보류] "
                                + "Redis stock 값이 올바르지 않습니다. "
                                + "saleId={}, stockKey={}, redisStock={}",
                        id,
                        stockKey,
                        redisStock,
                        e
                );
            }
        }

        // 재고 동기화 성공 여부와 관계없이 DB 판매 종료
        // 신규 구매는 Redis에서 이미 차단했으며,
        // finalStockSyncedAt은 최종 동기화 스케줄러가 성공할 때 기록한다.
        sale.updateSaleStatus(SaleStatus.CLOSED);
    }

    @Override
    @Transactional
    public boolean syncFinalRemainingStock(Long saleId) {
        Sale sale = saleRepository.findByIdWithLock(saleId)
                .orElseThrow(() ->
                        new BusinessException(
                                SaleErrorCode.SALE_NOT_FOUND
                        )
                );

        // 종료된 판매만 처리
        if (sale.getStatus() != SaleStatus.CLOSED) {
            return false;
        }

        // 이미 최종 동기화한 판매는 처리하지 않음
        if (sale.getFinalStockSyncedAt() != null) {
            return false;
        }

        LocalDateTime cancellationDeadline =
                sale.getEndAt().plusDays(1);

        // Redis user key가 살아 있고 취소 가능한 기간이면 보류
        if (LocalDateTime.now().isBefore(
                cancellationDeadline
        )) {
            return false;
        }

        // 결제 실패나 timeout으로 복구될 구매가 남아 있으면 보류
        boolean hasPendingPurchase =
                purchaseRepository.existsBySaleIdAndStatus(
                        saleId,
                        PurchaseStatus.PENDING_PAYMENT
                );

        if (hasPendingPurchase) {
            return false;
        }

        String stockKey = "sale:" + saleId + ":stock";
        String redisStock =
                redisTemplate.opsForValue().get(stockKey);

        // Redis stock은 endAt + 2일까지 존재해야 함
        if (redisStock == null) {
            return false;
        }

        int finalRemainingStock;

        try {
            finalRemainingStock =
                    Integer.parseInt(redisStock);
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        sale.syncRemainingStock(finalRemainingStock);
        sale.completeFinalStockSync();

        return true;
    }

    private Integer getRedisStock(Long saleId) {
        String stockKey = "sale:" + saleId + ":stock";
        String value = redisTemplate.opsForValue().get(stockKey);

        if (value == null) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //응답용 status
    private SaleStatus resolveDisplayStatus(
            Sale sale,
            Integer currentStock
    ) {
        // 조회 응답에서는 즉시 CLOSED로 표시
        if (sale.getStatus() == SaleStatus.ON_SALE
                && !LocalDateTime.now().isBefore(sale.getEndAt())) {
            return SaleStatus.CLOSED;
        }

        // SOLD_OUT은 RDB 상태값이 아니라 Redis 실시간 재고 기반으로 계산
        if (sale.getStatus() == SaleStatus.ON_SALE
                && currentStock != null
                && currentStock <= 0) {
            return SaleStatus.SOLD_OUT;
        }

        return sale.getStatus();
    }
}
