package com.team3.gudit.goods.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.goods.dto.response.*;
import com.team3.gudit.goods.exception.GoodsErrorCode;
import com.team3.gudit.goods.mapper.GoodsMapper;
import com.team3.gudit.goods.service.component.ImageStorageManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {
    private final GoodsMapper goodsMapper;
    private final GoodsRepository goodsRepository;
    private final ImageStorageManager imageStorageManager;


    @Transactional
    public GoodsCreateResponse create (
            GoodsCreateRequest request,
            MultipartFile fileImage
    ) {
        log.info(
                "[메뉴 생성 요청] name={}, price={}, description={}, hasImage={}",
                request.name(),
                request.price(),
                request.description(),
                fileImage != null && !fileImage.isEmpty()
        );

        String imageUrl = imageStorageManager.store(fileImage);
        Goods goods = goodsMapper.toEntity(request, imageUrl);
        Goods saved = goodsRepository.save(goods);

        return goodsMapper.toCreateResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GoodsListResponse> goodsList() {

        return goodsRepository
                .findAllByStatus(GoodsStatus.ACTIVE)
                .stream()
                .map(goodsMapper::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoodsListResponse> adminGoodsList() {
        return goodsRepository
                .findAll()
                .stream()
                .map(goodsMapper::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoodsDetailResponse goodsDetail(Long id) {
        Goods goods = goodsRepository.findByIdAndStatus(id, GoodsStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(GoodsErrorCode.GOODS_NOT_FOUND));

        return  goodsMapper.toDetailResponse(goods);
    }

    @Transactional(readOnly = true)
    public GoodsDetailResponse adminGoodsDetail(Long id) {
        Goods goods = goodsRepository.findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                GoodsErrorCode.GOODS_NOT_FOUND
                        )
                );

        return goodsMapper.toDetailResponse(goods);
    }

    @Transactional
    public GoodsUpdateResponse updateGoods(
            Long id,
            GoodsUpdateRequest request,
            MultipartFile fileImage) {
        Goods goods = findByIdOrThrow(id);

        String imageUrl = goods.getImageUrl();

        if (fileImage != null && !fileImage.isEmpty()) {
            imageUrl = imageStorageManager.store(fileImage);

            log.debug(
                    "[굿즈 이미지 변경] id={}, imageUrl={}",
                    id,
                    imageUrl
            );
        }

        goodsMapper.updateEntity(goods, request, imageUrl);

        log.info(
                "[굿즈 수정 완료] id={}, name={}, ActiveStatus={}, price={}, description={}",
                goods.getId(),
                goods.getName(),
                goods.getStatus(),
                goods.getPrice(),
                goods.getDescription()
        );

        return goodsMapper.toUpdateResponse(goods);
    }

    @Transactional
    public GoodsStatusUpdateResponse updateGoodsStatus(
            Long id,
            GoodsStatusUpdateRequest request) {
        Goods goods = findByIdOrThrow(id);

        goodsMapper.updateStatus(goods, request);

        ;

        return goodsMapper.toStatusUpdateResponse(goods);
    }

    @Transactional
    public void deleteGoods(Long id) {
        Goods goods = findByIdOrThrow(id);

        goods.deactivate();

        log.info("[굿즈 삭제 완료] goodsId={}", id);
    }


    private Goods findByIdOrThrow(Long id) {
        log.debug("[메뉴 조회] menuId={}", id);

        return goodsRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GoodsErrorCode.GOODS_NOT_FOUND));
    }

}
