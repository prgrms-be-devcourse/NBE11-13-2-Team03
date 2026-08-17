package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.global.exception.GlobalErrorCode;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.goods.exception.GoodsErrorCode;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {
    private final SaleRepository saleRepository;
    private final GoodsRepository goodsRepository;
    private final StringRedisTemplate redisTemplate;

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
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_CLOSED));

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
                        new BusinessException(SaleErrorCode.SALE_NOT_FOUND)
                );

        String stockKey = "sale:" + id + ":stock";
        String infoKey = "sale:" + id + ":info";

        // 1. Redis에서 먼저 신규 구매 차단
        redisTemplate.opsForHash().put(
                infoKey,
                "status",
                SaleStatus.CLOSED.name()
        );

        // 2. 구매 차단 후 최종 재고 조회
        String redisStock = redisTemplate.opsForValue().get(stockKey);

        if (redisStock == null) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        int finalRemainingStock;

        try {
            finalRemainingStock = Integer.parseInt(redisStock);
        } catch (NumberFormatException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        // 3. 최종 재고 RDB 동기화
        sale.syncRemainingStock(finalRemainingStock);

        // 4. DB 판매 종료
        sale.updateSaleStatus(SaleStatus.CLOSED);
    }
}
