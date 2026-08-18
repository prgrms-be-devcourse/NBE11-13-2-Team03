package com.team3.gudit.domain.goods.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.goods.dto.request.GoodsCreateRequest;
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest;
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest;
import com.team3.gudit.goods.dto.response.*;
import com.team3.gudit.goods.mapper.GoodsMapper;
import com.team3.gudit.goods.service.GoodsService;
import com.team3.gudit.goods.service.component.ImageStorageManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoodsServiceTest {

    @InjectMocks
    private GoodsService goodsService;

    @Mock
    private GoodsMapper goodsMapper;

    @Mock
    private GoodsRepository goodsRepository;

    @Mock
    private ImageStorageManager imageStorageManager;

    @Nested
    @DisplayName("굿즈 생성 (create)")
    class Create {

        @Test
        @DisplayName("이미지와 함께 굿즈 생성 요청 시 정상적으로 생성된다.")
        void create_success() {
            // given
            GoodsCreateRequest request = new GoodsCreateRequest("굿즈A", "설명A",10000, null);
            MockMultipartFile file = new MockMultipartFile(
                    "fileImage",
                    "test.png",
                    "image/png",
                    "test image contents".getBytes()
            );

            String storedImageUrl = "/thumbnails/stored-test.png";

            Goods goods = Goods.builder()
                    .name("굿즈A")
                    .price(10000)
                    .description("설명A")
                    .imageUrl(storedImageUrl)
                    .build();

            Goods savedGoods = Goods.builder()
                    .id(1L)
                    .name("굿즈A")
                    .price(10000)
                    .description("설명A")
                    .imageUrl(storedImageUrl)
                    .build();

            GoodsCreateResponse response = GoodsCreateResponse.builder()
                    .id(1L)
                    .name("굿즈A")
                    .price(10000)
                    .description("설명A")
                    .imageUrl(storedImageUrl)
                    .build();

            given(imageStorageManager.store(file)).willReturn(storedImageUrl);
            given(goodsMapper.toEntity(request, storedImageUrl)).willReturn(goods);
            given(goodsRepository.save(goods)).willReturn(savedGoods);
            given(goodsMapper.toCreateResponse(savedGoods)).willReturn(response);

            // when
            GoodsCreateResponse result = goodsService.create(request, file);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("굿즈A");

            verify(imageStorageManager).store(file);
            verify(goodsRepository).save(goods);
        }
    }

    @Nested
    @DisplayName("굿즈 목록 조회 (goodsList)")
    class GoodsList {

        @Test
        @DisplayName("상태가 ACTIVE인 굿즈 목록을 조회한다.")
        void goodsList_success() {
            // given
            Goods goods1 = Goods.builder().id(1L).name("굿즈A").status(GoodsStatus.ACTIVE).build();
            Goods goods2 = Goods.builder().id(2L).name("굿즈B").status(GoodsStatus.ACTIVE).build();
            List<Goods> goodsList = List.of(goods1, goods2);

            GoodsListResponse response1 = GoodsListResponse.builder().id(1L).name("굿즈A").build();
            GoodsListResponse response2 = GoodsListResponse.builder().id(2L).name("굿즈B").build();

            given(goodsRepository.findAllByStatus(GoodsStatus.ACTIVE)).willReturn(goodsList);
            given(goodsMapper.toListResponse(goods1)).willReturn(response1);
            given(goodsMapper.toListResponse(goods2)).willReturn(response2);

            // when
            List<GoodsListResponse> result = goodsService.goodsList();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("굿즈A");
            assertThat(result.get(1).name()).isEqualTo("굿즈B");
        }
    }

    @Nested
    @DisplayName("굿즈 상세 조회 (goodsDetail)")
    class GoodsDetail {

        @Test
        @DisplayName("ACTIVE 상태의 굿즈가 존재하면 상세 정보를 반환한다.")
        void goodsDetail_success() {
            // given
            Long goodsId = 1L;
            Goods goods = Goods.builder().id(goodsId).name("굿즈A").status(GoodsStatus.ACTIVE).build();
            GoodsDetailResponse response = GoodsDetailResponse.builder().goodsId(goodsId).name("굿즈A").build();

            given(goodsRepository.findByIdAndStatus(goodsId, GoodsStatus.ACTIVE)).willReturn(Optional.of(goods));
            given(goodsMapper.toDetailResponse(goods)).willReturn(response);

            // when
            GoodsDetailResponse result = goodsService.goodsDetail(goodsId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.goodsId()).isEqualTo(goodsId);
        }

        @Test
        @DisplayName("존재하지 않거나 INACTIVE인 굿즈 조회 시 GoodsNotFoundException이 발생한다.")
        void goodsDetail_notFound_throwsException() {
            // given
            Long goodsId = 999L;
            given(goodsRepository.findByIdAndStatus(goodsId, GoodsStatus.ACTIVE)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> goodsService.goodsDetail(goodsId))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("굿즈 수정 (updateGoods)")
    class UpdateGoods {

        @Test
        @DisplayName("새로운 이미지와 함께 수정 요청 시 이미지 저장 후 굿즈 정보를 수정한다.")
        void updateGoods_withNewImage_success() {
            // given
            Long goodsId = 1L;
            GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 굿즈", "수정된 설명", 15000, null);
            MockMultipartFile newFile = new MockMultipartFile(
                    "fileImage",
                    "new.png",
                    "image/png",
                    "new image".getBytes()
            );

            Goods goods = Goods.builder().id(goodsId).name("기존 굿즈").imageUrl("/thumbnails/old.png").build();
            String newImageUrl = "/thumbnails/new.png";
            GoodsUpdateResponse response = GoodsUpdateResponse.builder().id(goodsId).name("수정된 굿즈").build();

            given(goodsRepository.findById(goodsId)).willReturn(Optional.of(goods));
            given(imageStorageManager.store(newFile)).willReturn(newImageUrl);
            given(goodsMapper.toUpdateResponse(goods)).willReturn(response);

            // when
            GoodsUpdateResponse result = goodsService.updateGoods(goodsId, request, newFile);

            // then
            assertThat(result).isNotNull();
            verify(imageStorageManager).store(newFile);
            verify(goodsMapper).updateEntity(goods, request, newImageUrl);
        }

        @Test
        @DisplayName("이미지 없이 수정 요청 시 기존 이미지를 유지하며 수정한다.")
        void updateGoods_withoutImage_success() {
            // given
            Long goodsId = 1L;
            GoodsUpdateRequest request = new GoodsUpdateRequest("수정된 굿즈", "수정된 설명", 15000, null);

            Goods goods = Goods.builder().id(goodsId).name("기존 굿즈").imageUrl("/thumbnails/old.png").build();
            GoodsUpdateResponse response = GoodsUpdateResponse.builder().id(goodsId).name("수정된 굿즈").build();

            given(goodsRepository.findById(goodsId)).willReturn(Optional.of(goods));
            given(goodsMapper.toUpdateResponse(goods)).willReturn(response);

            // when
            GoodsUpdateResponse result = goodsService.updateGoods(goodsId, request, null);

            // then
            assertThat(result).isNotNull();
            verify(imageStorageManager, never()).store(any());
            verify(goodsMapper).updateEntity(goods, request, "/thumbnails/old.png");
        }
    }

    @Nested
    @DisplayName("굿즈 상태 수정 (updateGoodsStatus)")
    class UpdateGoodsStatus {

        @Test
        @DisplayName("굿즈 상태를 정상적으로 변경한다.")
        void updateGoodsStatus_success() {
            // given
            Long goodsId = 1L;
            GoodsStatusUpdateRequest request = new GoodsStatusUpdateRequest(GoodsStatus.INACTIVE);
            Goods goods = Goods.builder().id(goodsId).status(GoodsStatus.ACTIVE).build();
            GoodsStatusUpdateResponse response = GoodsStatusUpdateResponse.builder().id(goodsId).status(GoodsStatus.INACTIVE).build();

            given(goodsRepository.findById(goodsId)).willReturn(Optional.of(goods));
            given(goodsMapper.toStatusUpdateResponse(goods)).willReturn(response);

            // when
            GoodsStatusUpdateResponse result = goodsService.updateGoodsStatus(goodsId, request);

            // then
            assertThat(result).isNotNull();
            verify(goodsMapper).updateStatus(goods, request);
        }
    }
}