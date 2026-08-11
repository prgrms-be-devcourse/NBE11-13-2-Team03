package com.team3.gudit.domain.goodsSalses.domain.entity;

import com.team3.gudit.domain.goods.domain.entity.Goods;
import com.team3.gudit.domain.goodsSalses.domain.enums.SaleStatus;
import com.team3.gudit.global.exception.NotEnoughStockException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "GOODS_SALES")
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "initial_stock", nullable = false)
    private Integer initialStock;

    @Column(name = "remaining_stock", nullable = false)
    private Integer remainingStock;

    @Column(name = "max_purchase_quantity")
    private Integer maxPurchaseQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SaleStatus status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Sale(Long id, Goods goods, Long createdBy, Integer initialStock, Integer remainingStock,
                 Integer maxPurchaseQuantity, SaleStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        this.id = id;
        this.goods = goods;
        this.createdBy = createdBy;
        this.initialStock = initialStock;
        this.remainingStock = remainingStock != null ? remainingStock : initialStock;
        this.maxPurchaseQuantity = maxPurchaseQuantity;
        this.status = status != null ? status : SaleStatus.READY;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /**
     * 판매 생성 정적 팩토리 메서드
     */
    public static Sale createSale(Goods goods, Long createdBy, Integer initialStock,
                                  Integer maxPurchaseQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        return Sale.builder()
                .goods(goods)
                .createdBy(createdBy)
                .initialStock(initialStock)
                .remainingStock(initialStock) // 초기 잔여 재고 = 초기 재고
                .maxPurchaseQuantity(maxPurchaseQuantity)
                .status(SaleStatus.READY)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }

    public void decreaseStock(int count) {
        if (this.remainingStock - count < 0) {
            throw new NotEnoughStockException("재고가 부족합니다. (현재 재고: " + this.remainingStock + ")");
        }
        this.remainingStock -= count;

        if (this.remainingStock == 0) {
            this.status = SaleStatus.SOLD_OUT;
        }
    }

    public void restoreStock(int count) {
        this.remainingStock += count;

        if (this.status == SaleStatus.SOLD_OUT && this.remainingStock > 0) {
            this.status = SaleStatus.ON_SALE;
        }
    }

    public void updateSaleStatus(SaleStatus status) {
        this.status = status;
    }
}
