package com.team3.gudit.domain.goods.domain.entity;

import com.team3.gudit.domain.goods.dto.GoodsUpdateRequest;
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
@Table(name = "GOODS")
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Goods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 100)
    private String description;

    private Integer price;
    private String imageUrl;
    private Boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Goods of(String name, String description, Integer price, String imageUrl) {
        return Goods.builder()
                .name(name)
                .description(description)
                .price(price)
                .imageUrl(imageUrl)
                .active(true)
                .build();
    }


    public static Goods from(Goods goods) {
        return Goods.builder()
                .name(goods.getName())
                .description(goods.getDescription())
                .price(goods.getPrice())
                .imageUrl(goods.getImageUrl())
                .active(true)
                .build();
    }


    public void updateGoodsInfo(
            String name,
            String description,
            Integer price,
            String imageUrl
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }
}