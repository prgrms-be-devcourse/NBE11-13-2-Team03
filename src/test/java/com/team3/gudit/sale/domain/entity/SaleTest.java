package com.team3.gudit.sale.domain.entity;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.exception.SaleErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class SaleTest {
    @Test
    @DisplayName("판매 생성 시 남은 재고는 초기 재고로 설정되고 상태는 READY로 초기화된다")
    void createSale() {
        // given
        Goods goods = Goods.of(
                "테스트 상품",
                "테스트 상품 설명",
                10_000,
                null
        );

        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when
        Sale sale = Sale.builder()
                .goods(goods)
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(startAt)
                .endAt(endAt)
                .build();

        // then
        assertThat(sale.getGoods()).isEqualTo(goods);
        assertThat(sale.getInitialStock()).isEqualTo(100);
        //판매 생성 시 남은 재고는 초기 재고와 동일하게 시작
        assertThat(sale.getRemainingStock()).isEqualTo(100);
        assertThat(sale.getMaxPurchaseQuantity()).isEqualTo(2);
        assertThat(sale.getStartAt()).isEqualTo(startAt);
        assertThat(sale.getEndAt()).isEqualTo(endAt);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.READY);
    }

    @Test
    @DisplayName("초기 재고가 null이면 판매를 생성할 수 없다")
    void createSaleWithNullInitialStock() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(null)
                        .maxPurchaseQuantity(2)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_INITIAL_STOCK
                        )
                );
    }

    @Test
    @DisplayName("초기 재고가 0이면 판매를 생성할 수 없다")
    void createSaleWithZeroInitialStock() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(0)
                        .maxPurchaseQuantity(2)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_INITIAL_STOCK
                        )
                );
    }

    @Test
    @DisplayName("초기 재고가 음수이면 판매를 생성할 수 없다")
    void createSaleWithNegativeInitialStock() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(-1)
                        .maxPurchaseQuantity(2)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_INITIAL_STOCK
                        )
                );
    }

    @Test
    @DisplayName("최대 구매 수량이 null이면 판매를 생성할 수 없다")
    void createSaleWithNullMaxPurchaseQuantity() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(null)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY
                        )
                );
    }

    @Test
    @DisplayName("최대 구매 수량이 0이면 판매를 생성할 수 없다")
    void createSaleWithZeroMaxPurchaseQuantity() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(0)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY
                        )
                );
    }

    @Test
    @DisplayName("최대 구매 수량이 음수이면 판매를 생성할 수 없다")
    void createSaleWithNegativeMaxPurchaseQuantity() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(-1)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_MAX_PURCHASE_QUANTITY
                        )
                );
    }

    @Test
    @DisplayName("판매 시작 시간이 null이면 판매를 생성할 수 없다")
    void createSaleWithNullStartAt() {
        // given
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(2)
                        .startAt(null)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("판매 종료 시간이 null이면 판매를 생성할 수 없다")
    void createSaleWithNullEndAt() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(2)
                        .startAt(startAt)
                        .endAt(null)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("판매 시작 시간과 종료 시간이 같으면 판매를 생성할 수 없다")
    void createSaleWithSameStartAtAndEndAt() {
        // given
        LocalDateTime saleAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(2)
                        .startAt(saleAt)
                        .endAt(saleAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("판매 시작 시간이 종료 시간보다 늦으면 판매를 생성할 수 없다")
    void createSaleWithStartAtAfterEndAt() {
        // given
        LocalDateTime startAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);
        LocalDateTime endAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);

        // when & then
        assertThatThrownBy(() ->
                Sale.builder()
                        .goods(Goods.of(
                                "테스트 상품",
                                "테스트 상품 설명",
                                10_000,
                                null
                        ))
                        .initialStock(100)
                        .maxPurchaseQuantity(2)
                        .startAt(startAt)
                        .endAt(endAt)
                        .build()
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("READY 상태에서는 판매 정보를 전체 수정할 수 있다")
    void updateSaleInfoWhenReady() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int updatedInitialStock = 200;
        int updatedMaxPurchaseQuantity = 5;

        LocalDateTime updatedStartAt =
                LocalDateTime.of(2026, 8, 21, 10, 0);
        LocalDateTime updatedEndAt =
                LocalDateTime.of(2026, 8, 21, 14, 0);

        // when
        sale.updateSaleInfo(
                updatedInitialStock,
                updatedMaxPurchaseQuantity,
                updatedStartAt,
                updatedEndAt
        );

        // then
        assertThat(sale.getInitialStock()).isEqualTo(updatedInitialStock);
        assertThat(sale.getRemainingStock()).isEqualTo(updatedInitialStock);
        assertThat(sale.getMaxPurchaseQuantity()).isEqualTo(updatedMaxPurchaseQuantity);
        assertThat(sale.getStartAt()).isEqualTo(updatedStartAt);
        assertThat(sale.getEndAt()).isEqualTo(updatedEndAt);
    }

    @Test
    @DisplayName("판매 수정 시 초기 재고가 null이면 기존 정보를 변경하지 않고 예외가 발생한다")
    void updateSaleInfoWithNullInitialStock() {
        // given
        LocalDateTime originalStartAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime originalEndAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(originalStartAt)
                .endAt(originalEndAt)
                .build();

        LocalDateTime updatedStartAt =
                LocalDateTime.of(2026, 8, 21, 10, 0);
        LocalDateTime updatedEndAt =
                LocalDateTime.of(2026, 8, 21, 14, 0);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleInfo(
                        null,
                        5,
                        updatedStartAt,
                        updatedEndAt
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_INITIAL_STOCK
                        )
                );

        // 잘못된 전체 수정 요청이므로 기존 정보가 유지된다.
        assertThat(sale.getInitialStock()).isEqualTo(100);
        assertThat(sale.getRemainingStock()).isEqualTo(100);
        assertThat(sale.getMaxPurchaseQuantity()).isEqualTo(2);
        assertThat(sale.getStartAt()).isEqualTo(originalStartAt);
        assertThat(sale.getEndAt()).isEqualTo(originalEndAt);
    }

    @Test
    @DisplayName("ON_SALE 상태에서는 판매 정보를 수정할 수 없다")
    void updateSaleInfoWhenOnSale() {
        // given
        LocalDateTime originalStartAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime originalEndAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(originalStartAt)
                .endAt(originalEndAt)
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleInfo(
                        200,
                        5,
                        LocalDateTime.of(2026, 8, 21, 10, 0),
                        LocalDateTime.of(2026, 8, 21, 14, 0)
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.CANNOT_UPDATE_ONGOING_SALE
                        )
                );

        // 수정 실패 후에도 기존 정보가 유지되는지 검증
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.ON_SALE);
        assertThat(sale.getInitialStock()).isEqualTo(100);
        assertThat(sale.getRemainingStock()).isEqualTo(100);
        assertThat(sale.getMaxPurchaseQuantity()).isEqualTo(2);
        assertThat(sale.getStartAt()).isEqualTo(originalStartAt);
        assertThat(sale.getEndAt()).isEqualTo(originalEndAt);
    }

    @Test
    @DisplayName("CLOSED 상태에서는 판매 정보를 수정할 수 없다")
    void updateSaleInfoWhenClosed() {
        // given
        LocalDateTime originalStartAt =
                LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime originalEndAt =
                LocalDateTime.of(2026, 8, 20, 12, 0);

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(originalStartAt)
                .endAt(originalEndAt)
                .build();

        // READY → ON_SALE → CLOSED
        sale.updateSaleStatus(SaleStatus.ON_SALE);
        sale.updateSaleStatus(SaleStatus.CLOSED);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleInfo(
                        200,
                        5,
                        LocalDateTime.of(2026, 8, 21, 10, 0),
                        LocalDateTime.of(2026, 8, 21, 14, 0)
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.CANNOT_UPDATE_ONGOING_SALE
                        )
                );

        // 수정 실패 후에도 CLOSED 상태와 기존 정보가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.CLOSED);
        assertThat(sale.getInitialStock())
                .isEqualTo(100);
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
        assertThat(sale.getMaxPurchaseQuantity())
                .isEqualTo(2);
        assertThat(sale.getStartAt())
                .isEqualTo(originalStartAt);
        assertThat(sale.getEndAt())
                .isEqualTo(originalEndAt);
    }

    @Test
    @DisplayName("READY 상태의 판매를 ON_SALE 상태로 변경할 수 있다")
    void updateSaleStatusFromReadyToOnSale() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.READY);

        // when
        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("ON_SALE 상태의 판매를 CLOSED 상태로 변경할 수 있다")
    void updateSaleStatusFromOnSaleToClosed() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);

        // when
        sale.updateSaleStatus(SaleStatus.CLOSED);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.CLOSED);
    }

    @Test
    @DisplayName("ON_SALE 상태의 판매를 READY 상태로 되돌릴 수 없다")
    void updateSaleStatusFromOnSaleToReady() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleStatus(SaleStatus.READY)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_STATUS_TRANSITION
                        )
                );

        // 상태 전환 실패 후에도 기존 ON_SALE 상태가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매 상태를 RDB의 SOLD_OUT 상태로 변경할 수 없다")
    void updateSaleStatusToSoldOut() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleStatus(SaleStatus.SOLD_OUT)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_STATUS_TRANSITION
                        )
                );

        // SOLD_OUT 전환 실패 후에도 RDB 기준 상태는 ON_SALE로 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("CLOSED 상태의 판매를 다시 ON_SALE 상태로 변경할 수 없다")
    void updateSaleStatusFromClosedToOnSale() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        // READY → ON_SALE → CLOSED
        sale.updateSaleStatus(SaleStatus.ON_SALE);
        sale.updateSaleStatus(SaleStatus.CLOSED);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleStatus(SaleStatus.ON_SALE)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_STATUS_TRANSITION
                        )
                );

        // 전환 실패 후에도 CLOSED 상태가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.CLOSED);
    }

    @Test
    @DisplayName("ON_SALE 상태의 판매를 DELETED 상태로 직접 변경할 수 없다")
    void updateSaleStatusFromOnSaleToDeleted() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleStatus(SaleStatus.DELETED)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_STATUS_TRANSITION
                        )
                );

        // 상태 전환 실패 후에도 ON_SALE 상태가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("CLOSED 상태의 판매를 삭제하면 DELETED 상태가 된다")
    void deleteSaleWhenClosed() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        // READY → ON_SALE → CLOSED
        sale.updateSaleStatus(SaleStatus.ON_SALE);
        sale.updateSaleStatus(SaleStatus.CLOSED);

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.CLOSED);

        // when
        sale.deleteSale();

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.DELETED);
    }

    @Test
    @DisplayName("ON_SALE 상태의 판매는 삭제할 수 없다")
    void deleteSaleWhenOnSale() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(sale::deleteSale)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.CANNOT_DELETE_ONGOING_SALE
                        )
                );

        // 삭제 실패 후에도 ON_SALE 상태가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("DELETED 상태의 판매를 다시 ON_SALE 상태로 변경할 수 없다")
    void updateSaleStatusFromDeletedToOnSale() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        // READY → DELETED
        sale.deleteSale();

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.DELETED);

        // when & then
        assertThatThrownBy(() ->
                sale.updateSaleStatus(SaleStatus.ON_SALE)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_STATUS_TRANSITION
                        )
                );

        // 전환 실패 후에도 DELETED 상태가 유지된다.
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.DELETED);
    }

    @Test
    @DisplayName("현재와 동일한 판매 상태를 요청하면 예외 없이 기존 상태를 유지한다")
    void updateSaleStatusToSameStatus() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.READY);

        // when
        sale.updateSaleStatus(SaleStatus.READY);

        // then
        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.READY);
    }

    @Test
    @DisplayName("현재 시간이 판매 기간 안이고 상태가 ON_SALE이면 구매 가능 검증을 통과한다")
    void validateSalePeriodWhenOnSale() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(now.minusMinutes(10))
                .endAt(now.plusMinutes(10))
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatCode(sale::validateSalePeriod)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("현재 시간이 판매 시작 전이면 구매할 수 없다")
    void validateSalePeriodBeforeStartAt() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(now.plusMinutes(10))
                .endAt(now.plusMinutes(20))
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(sale::validateSalePeriod)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("현재 시간이 판매 종료 후이면 구매할 수 없다")
    void validateSalePeriodAfterEndAt() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(now.minusMinutes(20))
                .endAt(now.minusMinutes(10))
                .build();

        sale.updateSaleStatus(SaleStatus.ON_SALE);

        // when & then
        assertThatThrownBy(sale::validateSalePeriod)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_SALE_PERIOD
                        )
                );
    }

    @Test
    @DisplayName("현재 시간이 판매 기간 안이어도 READY 상태이면 구매할 수 없다")
    void validateSalePeriodWhenReady() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(now.minusMinutes(10))
                .endAt(now.plusMinutes(10))
                .build();

        assertThat(sale.getStatus())
                .isEqualTo(SaleStatus.READY);

        // when & then
        assertThatThrownBy(sale::validateSalePeriod)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.SALE_CLOSED
                        )
                );
    }

    @Test
    @DisplayName("현재 시간이 판매 기간 안이어도 CLOSED 상태이면 구매할 수 없다")
    void validateSalePeriodWhenClosed() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(now.minusMinutes(10))
                .endAt(now.plusMinutes(10))
                .build();

        // READY → ON_SALE → CLOSED
        sale.updateSaleStatus(SaleStatus.ON_SALE);
        sale.updateSaleStatus(SaleStatus.CLOSED);

        // when & then
        assertThatThrownBy(sale::validateSalePeriod)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.SALE_CLOSED
                        )
                );
    }

    @Test
    @DisplayName("구매 수량이 최대 구매 가능 수량과 같으면 구매할 수 있다")
    void validatePurchaseQuantityAtMaximum() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int purchaseQuantity = 2;

        // when & then
        assertThatCode(() ->
                sale.validatePurchaseQuantity(purchaseQuantity)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("구매 수량이 최대 구매 가능 수량을 초과하면 구매할 수 없다")
    void validatePurchaseQuantityOverMaximum() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int purchaseQuantity = 3;

        // when & then
        assertThatThrownBy(() ->
                sale.validatePurchaseQuantity(purchaseQuantity)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY
                        )
                );
    }

    @Test
    @DisplayName("재고를 차감하면 남은 재고가 차감 수량만큼 감소한다")
    void decreaseStock() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int decreaseQuantity = 10;

        // when
        sale.decreaseStock(decreaseQuantity);

        // then
        assertThat(sale.getRemainingStock())
                .isEqualTo(90);
    }

    @Test
    @DisplayName("남은 재고보다 많은 수량을 차감하면 재고가 유지되고 예외가 발생한다")
    void decreaseStockOverRemainingStock() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int decreaseQuantity = 101;

        // when & then
        assertThatThrownBy(() ->
                sale.decreaseStock(decreaseQuantity)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.NOT_ENOUGH_STOCK
                        )
                );

        // 차감 실패 후에도 기존 재고가 유지된다.
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("재고를 복구하면 남은 재고가 복구 수량만큼 증가한다")
    void restoreStock() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        sale.decreaseStock(10);

        assertThat(sale.getRemainingStock())
                .isEqualTo(90);

        // when
        sale.restoreStock(10);

        // then
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("남은 재고를 동기화하면 입력한 재고 값으로 변경된다(판매 종료 후 동기화 시 사용)")
    void syncRemainingStock() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int redisRemainingStock = 35;

        // when
        sale.syncRemainingStock(redisRemainingStock);

        // then
        assertThat(sale.getRemainingStock())
                .isEqualTo(redisRemainingStock);

        // 초기 재고는 변경되지 않는다.
        assertThat(sale.getInitialStock())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("음수 재고는 남은 재고로 동기화할 수 없다")
    void syncNegativeRemainingStock() {
        // given
        Sale sale = Sale.builder()
                .goods(Goods.of(
                        "테스트 상품",
                        "테스트 상품 설명",
                        10_000,
                        null
                ))
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .startAt(
                        LocalDateTime.of(2026, 8, 20, 10, 0)
                )
                .endAt(
                        LocalDateTime.of(2026, 8, 20, 12, 0)
                )
                .build();

        int invalidRemainingStock = -1;

        // when & then
        assertThatThrownBy(() ->
                sale.syncRemainingStock(invalidRemainingStock)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                SaleErrorCode.INVALID_REMAINING_STOCK
                        )
                );

        // 동기화 실패 후에도 기존 재고가 유지된다.
        assertThat(sale.getRemainingStock())
                .isEqualTo(100);
    }

}