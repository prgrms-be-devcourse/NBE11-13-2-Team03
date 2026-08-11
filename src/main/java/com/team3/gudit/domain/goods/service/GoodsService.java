package com.team3.gudit.domain.goods.service;

import com.team3.gudit.domain.goods.domain.entity.Goods;
import com.team3.gudit.domain.goods.domain.enums.GoodsStatus;
import com.team3.gudit.domain.goods.domain.repository.GoodsRepository;
import com.team3.gudit.domain.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.domain.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.domain.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.domain.goods.dto.response.*;
import com.team3.gudit.domain.goods.mapper.GoodsMapper;
import com.team3.gudit.domain.goods.service.component.ImageStorageManager;
import com.team3.gudit.global.exception.GoodsNotFoundException;
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
    public GoodsDetailResponse goodsDetail(Long id) {
        Goods goods = goodsRepository.findByIdAndStatus(id,  GoodsStatus.ACTIVE)
                .orElseThrow(GoodsNotFoundException::new);

        return  goodsMapper.toDetailResponse(goods);
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

    private Goods findByIdOrThrow(Long id) {
        log.debug("[메뉴 조회] menuId={}", id);

        return goodsRepository.findById(id)
                .orElseThrow(() -> new GoodsNotFoundException(id));
    }

}
