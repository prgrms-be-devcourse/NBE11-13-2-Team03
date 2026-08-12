package com.team3.gudit.payment.entity;

import com.team3.gudit.purchase.entity.Purchase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_purchase",
                        columnNames = "purchase_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_order_id",
                        columnNames = "order_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_payment_key",
                        columnNames = "payment_key"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Payment(Purchase purchase, int amount) {
        this.purchase = purchase;
        this.orderId = "GUDIT_" + UUID.randomUUID();
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public static Payment create(Purchase purchase, int amount) {
        return new Payment(purchase, amount);
    }
}
