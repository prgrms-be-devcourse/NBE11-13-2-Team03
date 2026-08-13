package com.team3.gudit.purchase.repository;

import com.team3.gudit.purchase.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByUserIdAndSaleId(Long userId, Long saleId);

    List<Purchase> findAllByUserId(Long userId);

    Optional<Purchase> findByIdAndUserId(Long purchaseId, Long userId);
}