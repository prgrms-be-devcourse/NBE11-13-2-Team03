package com.team3.gudit.purchase.repository;

import com.team3.gudit.goods.config.JpaConfig;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class PurchaseRepositoryTest {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("사용자와 판매에 대해 취소되지 않은 구매가 존재하는지 조회한다")
    void existsByUserIdAndSaleIdAndStatusNot() {
        // Given
        TestData data = saveTestData();

        Purchase purchase = Purchase.create(
                data.user(),
                data.sale(),
                2,
                20_000
        );
        purchaseRepository.save(purchase);

        entityManager.flush();
        entityManager.clear();

        // When
        boolean exists =
                purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(
                        data.user().getId(),
                        data.sale().getId(),
                        PurchaseStatus.CANCELED
                );

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("사용자 ID로 구매 목록을 최신 생성순으로 조회한다")
    void findAllByUserIdOrderByCreatedAtDesc() {
        // Given
        TestData data = saveTestData();

        Purchase firstPurchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(firstPurchase);

        entityManager.flush();

        Purchase secondPurchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                20_000
        );
        purchaseRepository.save(secondPurchase);

        entityManager.flush();
        entityManager.clear();

        // When
        List<Purchase> result =
                purchaseRepository
                        .findAllByUserIdOrderByCreatedAtDesc(
                                data.user().getId()
                        );

        // Then
        assertThat(result)
                .extracting(Purchase::getId)
                .containsExactly(
                        secondPurchase.getId(),
                        firstPurchase.getId()
                );
    }

    @Test
    @DisplayName("구매 ID와 사용자 ID가 모두 일치하는 구매를 조회한다")
    void findByIdAndUserId() {
        // Given
        TestData data = saveTestData();

        Purchase purchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(purchase);

        entityManager.flush();
        entityManager.clear();

        // When
        Purchase result = purchaseRepository.findByIdAndUserId(
                        purchase.getId(),
                        data.user().getId()
                )
                .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(purchase.getId());
        assertThat(result.getUser().getId())
                .isEqualTo(data.user().getId());
    }

    @Test
    @DisplayName("기준 시각 이전에 생성된 PENDING_PAYMENT 구매를 조회한다")
    void findAllByStatusAndCreatedAtBefore() {
        // Given
        TestData data = saveTestData();

        Purchase pendingPurchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(pendingPurchase);

        Purchase completedPurchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        completedPurchase.complete();
        purchaseRepository.save(completedPurchase);

        entityManager.flush();
        entityManager.clear();

        LocalDateTime threshold = LocalDateTime.now().plusMinutes(1);

        // When
        List<Purchase> result =
                purchaseRepository.findAllByStatusAndCreatedAtBefore(
                        PurchaseStatus.PENDING_PAYMENT,
                        threshold
                );

        // Then
        assertThat(result)
                .extracting(Purchase::getId)
                .contains(pendingPurchase.getId())
                .doesNotContain(completedPurchase.getId());
    }

    @Test
    @DisplayName("구매 ID로 비관적 쓰기 잠금을 적용해 조회한다")
    void findByIdWithLock() {
        // Given
        TestData data = saveTestData();

        Purchase purchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(purchase);

        entityManager.flush();
        entityManager.clear();

        // When
        Purchase result = purchaseRepository.findByIdWithLock(
                        purchase.getId()
                )
                .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(purchase.getId());
        assertThat(result.getStatus())
                .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("구매 ID와 사용자 ID로 비관적 쓰기 잠금을 적용해 조회한다")
    void findByIdAndUserIdWithLock() {
        // Given
        TestData data = saveTestData();

        Purchase purchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(purchase);

        entityManager.flush();
        entityManager.clear();

        // When
        Purchase result =
                purchaseRepository.findByIdAndUserIdWithLock(
                                purchase.getId(),
                                data.user().getId()
                        )
                        .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(purchase.getId());
        assertThat(result.getUser().getId())
                .isEqualTo(data.user().getId());
    }

    @Test
    @DisplayName("판매에 PENDING_PAYMENT 구매가 존재하는지 조회한다")
    void existsBySaleIdAndStatus() {
        // Given
        TestData data = saveTestData();

        Purchase purchase = Purchase.create(
                data.user(),
                data.sale(),
                1,
                10_000
        );
        purchaseRepository.save(purchase);

        entityManager.flush();
        entityManager.clear();

        // When
        boolean exists = purchaseRepository.existsBySaleIdAndStatus(
                data.sale().getId(),
                PurchaseStatus.PENDING_PAYMENT
        );

        // Then
        assertThat(exists).isTrue();
    }

    private TestData saveTestData() {
        User user = User.builder()
                .kakaoId(System.nanoTime())
                .nickname("테스트 사용자")
                .email("test@example.com")
                .role(Role.USER)
                .build();
        entityManager.persist(user);

        Goods goods = Goods.of(
                "테스트 상품",
                "Repository 테스트 상품",
                10_000,
                null
        );
        entityManager.persist(goods);

        Sale sale = Sale.builder()
                .goods(goods)
                .createdBy(user.getId())
                .initialStock(100)
                .maxPurchaseQuantity(2)
                .status(SaleStatus.READY)
                .startAt(LocalDateTime.now().plusHours(1))
                .endAt(LocalDateTime.now().plusHours(2))
                .build();
        entityManager.persist(sale);

        return new TestData(user, sale);
    }

    private record TestData(
            User user,
            Sale sale
    ) {
    }
}