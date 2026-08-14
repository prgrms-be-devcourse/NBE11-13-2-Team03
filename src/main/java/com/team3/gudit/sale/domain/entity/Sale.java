package com.team3.gudit.sale.domain.entity;

import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.global.exception.*;
import com.team3.gudit.sale.exception.SaleErrorCode;
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

    public void decreaseStock(int count) {
        if (this.remainingStock - count < 0) {
            throw new BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK);
        }
        this.remainingStock -= count;

        if (this.remainingStock == 0) {
            this.status = SaleStatus.SOLD_OUT;
        }
    }

    public void restoreStock(int count) {
        this.remainingStock += count;

        if (this.status == SaleStatus.CLOSED || this.status == SaleStatus.DELETED) {
           return;
        }

        if (this.status == SaleStatus.SOLD_OUT && isWithinSalePeriod() && remainingStock > 0) {
            this.status = SaleStatus.ON_SALE;
        }
    }

    public void validateSalePeriod() {
        LocalDateTime now = LocalDateTime.now();

        if (!isWithinSalePeriod()) {
            throw new BusinessException(SaleErrorCode.INVALID_SALE_PERIOD);
        }

        if (this.status == SaleStatus.CLOSED) {
            throw new BusinessException(SaleErrorCode.SALE_CLOSED);
        }

        if (this.status != SaleStatus.ON_SALE) {
            throw new BusinessException(SaleErrorCode.SALE_CLOSED);
        }
    }

    public void validatePurchaseQuantity(int purchaseQuantity) {
        if (this.maxPurchaseQuantity < purchaseQuantity) {
            throw new BusinessException(SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY);
        }
    }

    public void updateSaleInfo(
            Integer initialStock,
            Integer maxPurchaseQuantity,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        validateModifiable();

        this.initialStock = initialStock;
        this.remainingStock = initialStock;
        this.maxPurchaseQuantity = maxPurchaseQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void updateSaleStatus(SaleStatus status) {
        if (this.status == status) {
            return;
        }
        validateStatusTransition(status);

        this.status = status;
    }

    public void validateDeletable() {
        if (this.status == SaleStatus.ON_SALE) {
            throw new BusinessException(SaleErrorCode.CANNOT_DELETE_ONGOING_SALE);
        }
    }

    public void deleteSale() {
        validateDeletable();
        this.status = SaleStatus.DELETED;
    }

    private boolean isWithinSalePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return (!now.isBefore(this.startAt)) && now.isBefore(this.endAt);
    }

    private void validateModifiable() {
        if (this.status != SaleStatus.READY) {
            throw new BusinessException(SaleErrorCode.CANNOT_UPDATE_ONGOING_SALE);
        }
    }

    private void validateStatusTransition(SaleStatus status) {
        if (this.status == SaleStatus.DELETED || this.status == SaleStatus.CLOSED) {
            throw new BusinessException(SaleErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

}