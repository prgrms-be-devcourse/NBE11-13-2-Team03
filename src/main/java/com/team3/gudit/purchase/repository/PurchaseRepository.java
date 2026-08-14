package com.team3.gudit.purchase.repository;

import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByUserIdAndSaleIdAndStatusNot(
            Long userId,
            Long saleId,
            PurchaseStatus status
    );

    List<Purchase> findAllByUserId(Long userId);

    Optional<Purchase> findByIdAndUserId(Long purchaseId, Long userId);
}