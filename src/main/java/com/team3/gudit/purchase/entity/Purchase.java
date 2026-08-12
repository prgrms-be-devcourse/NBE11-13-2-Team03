package com.team3.gudit.purchase.entity;

import com.team3.gudit.domain.goodsSales.domain.entity.Sale;
import com.team3.gudit.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "purchases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_purchase_user_sale",
                        columnNames = {"user_id", "sale_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "purchase_price", nullable = false)
    private int purchasePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Purchase(
            User user,
            Sale sale,
            int quantity,
            int purchasePrice
    ) {
        LocalDateTime now = LocalDateTime.now();

        this.user = user;
        this.sale = sale;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.status = PurchaseStatus.PURCHASED;
        this.purchasedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Purchase create(
            User user,
            Sale sale,
            int quantity,
            int purchasePrice
    ) {
        return new Purchase(user, sale, quantity, purchasePrice);
    }

    public void cancel() {
        this.status = PurchaseStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}