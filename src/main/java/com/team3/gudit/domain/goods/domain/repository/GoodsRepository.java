package com.team3.gudit.domain.goods.domain.repository;

import com.team3.gudit.domain.goods.domain.entity.Goods;
import com.team3.gudit.domain.goods.domain.enums.GoodsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoodsRepository extends JpaRepository<Goods, Long> {
    List<Goods> findAllByStatus(GoodsStatus status);
    Optional<Goods> findByIdAndStatus(Long id, GoodsStatus status);

}
