package com.team3.gudit.payment.repository;

import com.team3.gudit.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Payment p
            where p.orderId = :orderId
            """)
    Optional<Payment> findByOrderIdWithLock(
            @Param("orderId") String orderId
    );

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByPurchaseId(Long purchaseId);
}