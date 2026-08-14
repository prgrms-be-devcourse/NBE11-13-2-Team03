package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
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

        //redis에 초기 재고 세팅
        String stockKey = "sale:" + savedSale.getId() + ":stock";
        redisTemplate.opsForValue().set(stockKey, String.valueOf(savedSale.getInitialStock()));

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

    /**
     * 판매 정보 및 초기 재고를 수정합니다.
     * <p>
     * - 판매 시작 전인 <b>READY(판매 대기)</b> 상태에서만 정보 수정이 가능합니다.<br>
     * - RDB의 Entity 수정과 함께 Redis 메모리의 초기 재고 키(sale:{id}:stock)도 동기화합니다.
     * </p>

     * @param saleId  수정할 판매 상품의 PK
     * @param request 수정할 초기 재고, 1인당 최대 구매 수량, 판매 시작/종료 일시
     * @return 수정 처리된 판매 상세 응답 DTO
     * @throws BusinessException <ul>
     * <li>{@link SaleErrorCode#SALE_NOT_FOUND}: 해당 ID의 판매 상품이 없는 경우</li>
     * <li>{@link SaleErrorCode#CANNOT_UPDATE_ONGOING_SALE}: READY 상태가 아닌 경우</li>
     * </ul>
     */
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

        // 판매 시작 전 수량이 변경될 수 있으므로 Redis 초기 재고 키도 함께 동기화
        String stockKey = "sale:" + saleId + ":stock";
        redisTemplate.opsForValue().set(stockKey, String.valueOf(request.initialStock()));

        return SaleDetailResponseDto.from(sale);
    }

    /**
     * 판매 상품의 상태(SaleStatus)를 변경합니다.
     * <p>
     * - READY, ON_SALE, SOLD_OUT, CLOSED 등 상품의 진행 상태를 전환할 때 사용합니다.<br>
     * - 상태 변경에 따른 추가적인 비즈니스 검증은 {@link Sale#updateSaleStatus(SaleStatus)} 엔티티 내부에서 수행됩니다.
     * </p>
     *
     * @param saleId  상태를 변경할 판매 상품의 PK
     * @param request 변경하고자 하는 목표 판매 상태(status)
     * @return 변경 완료된 판매 상태 응답 DTO
     * @throws BusinessException <ul>
     * <li>{@link SaleErrorCode#SALE_NOT_FOUND}: 해당 ID의 판매 상품이 없는 경우</li>
     * </ul>
     */
    @Override
    @Transactional
    public SaleStatusUpdateResponseDto updateSaleStatus(Long saleId, SaleStatusUpdateRequestDto request) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_NOT_FOUND));

        // 이미 삭제(DELETED)되었거나 종료(CLOSED)된 상품은 상태를 변경할 수 없음
        sale.updateSaleStatus(request.status());

        return SaleStatusUpdateResponseDto.from(sale);
    }

    /**
     * 판매 상품을 삭제(Soft Delete) 처리하고 연동된 Redis 재고 키를 삭제합니다.
     * <p>
     * - 진행 중(<b>ON_SALE</b>) 상태인 판매 상품은 삭제할 수 없으며 예외가 발생합니다.<br>
     * - RDB 데이터는 감사/이력 관리를 위해 Soft Delete 처리하고, Redis 메모리의 재고 Key는 즉시 Cleanup 합니다.
     * </p>
     *
     * @param id 삭제할 판매 상품의 PK
     * @throws BusinessException <ul>
     * <li>{@link SaleErrorCode#SALE_NOT_FOUND}: 해당 ID의 판매 상품이 없는 경우</li>
     * <li>{@link SaleErrorCode#CANNOT_DELETE_ONGOING_SALE}: 진행 중(ON_SALE) 상태인 상품을 삭제하려는 경우</li>
     * </ul>
     */
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
        redisTemplate.delete(stockKey);
    }

    @Override
    @Transactional
    public void warmupSaleInfo(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(SaleErrorCode.SALE_CLOSED));

        String stockKey = "sale:" + id + ":stock";
        String infoKey = "sale:" + id + ":info";

        // 1. Redis에 실시간 재고 수량 캐싱 (Key: sale:{id}:stock)
        redisTemplate.opsForValue().set(stockKey, String.valueOf(sale.getRemainingStock()));

        // 2. Redis에 판매 정책 정보(시작/종료 시간, 1인당 최대 수량) Hash 세팅 (Key: sale:{id}:info)
        SaleRedisDto dto = SaleRedisDto.from(sale);
        redisTemplate.opsForHash().putAll(infoKey, dto.toHashFields());

        // 3. 판매 종료 후 메모리 자동 정리를 위한 TTL 설정 (예: endAt + 2일)
        // long ttlSeconds = calculateTtlSeconds(sale.getEndAt());
        // redisTemplate.expire(stockKey, Duration.ofSeconds(ttlSeconds));
        // redisTemplate.expire(infoKey, Duration.ofSeconds(ttlSeconds));

        // DB 상태 변경 (READY -> ON_SALE)
        sale.updateSaleStatus(SaleStatus.ON_SALE);
    }
}
