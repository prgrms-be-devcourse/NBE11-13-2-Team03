package com.team3.gudit.purchase.repository;

import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByUserIdAndSaleIdAndStatusNot(
            Long userId,
            Long saleId,
            PurchaseStatus status
    );

    List<Purchase> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Purchase> findByIdAndUserId(Long purchaseId, Long userId);

    List<Purchase> findAllByStatusAndCreatedAtBefore(
            PurchaseStatus status,
            LocalDateTime createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Purchase p
        where p.id = :purchaseId
        """)
    Optional<Purchase> findByIdWithLock(
            @Param("purchaseId") Long purchaseId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Purchase p
        where p.id = :purchaseId
          and p.user.id = :userId
        """)
    Optional<Purchase> findByIdAndUserIdWithLock(
            @Param("purchaseId") Long purchaseId,
            @Param("userId") Long userId
    );

    boolean existsBySaleIdAndStatus(
            Long saleId,
            PurchaseStatus status
    );
}