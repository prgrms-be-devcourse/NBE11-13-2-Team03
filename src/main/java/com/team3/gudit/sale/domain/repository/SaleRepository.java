package com.team3.gudit.sale.domain.repository;

import com.team3.gudit.goods.domain.enums.GoodsStatus;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdWithLock(@Param("id") Long id);

    List<Sale> findByStatusAndStartAtBetween(
            SaleStatus status,
            LocalDateTime startAtAfter,
            LocalDateTime startAtBefore
    );

    List<Sale> findByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
            SaleStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    List<Sale> findByStatusAndEndAtLessThanEqual(
            SaleStatus status,
            LocalDateTime endAt
    );

    List<Sale>
    findByStatusAndFinalStockSyncedAtIsNullAndEndAtLessThanEqual(
            SaleStatus status,
            LocalDateTime endAt
    );

    @EntityGraph(attributePaths = "goods")
    List<Sale> findAllByGoods_Status(
            GoodsStatus goodsStatus
    );
}
