package com.team3.gudit.payment.repository;

import com.team3.gudit.goods.config.JpaConfig;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.purchase.entity.Purchase;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("주문 ID로 결제 정보를 조회한다")
    void findByOrderId() {
        // Given
        Payment payment = savePayment();

        entityManager.flush();
        entityManager.clear();

        // When
        Payment result = paymentRepository.findByOrderId(
                        payment.getOrderId()
                )
                .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(payment.getId());
        assertThat(result.getOrderId())
                .isEqualTo(payment.getOrderId());
    }

    @Test
    @DisplayName("결제 키로 결제 정보를 조회한다")
    void findByPaymentKey() {
        // Given
        Payment payment = savePayment();
        payment.start("test-payment-key");

        entityManager.flush();
        entityManager.clear();

        // When
        Payment result = paymentRepository.findByPaymentKey(
                        "test-payment-key"
                )
                .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(payment.getId());
        assertThat(result.getPaymentKey())
                .isEqualTo("test-payment-key");
    }

    @Test
    @DisplayName("구매 ID로 연결된 결제 정보를 조회한다")
    void findByPurchaseId() {
        // Given
        Payment payment = savePayment();
        Long purchaseId = payment.getPurchase().getId();

        entityManager.flush();
        entityManager.clear();

        // When
        Payment result = paymentRepository.findByPurchaseId(
                        purchaseId
                )
                .orElseThrow();

        // Then
        assertThat(result.getId()).isEqualTo(payment.getId());
        assertThat(result.getPurchase().getId())
                .isEqualTo(purchaseId);
    }

    private Payment savePayment() {
        User user = User.builder()
                .kakaoId(System.nanoTime())
                .nickname("테스트 사용자")
                .email("payment-test@example.com")
                .role(Role.USER)
                .build();
        entityManager.persist(user);

        Goods goods = Goods.of(
                "결제 테스트 상품",
                "Payment Repository 테스트 상품",
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

        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                10_000
        );
        entityManager.persist(purchase);

        Payment payment = Payment.create(
                purchase,
                10_000
        );
        entityManager.persist(payment);

        return payment;
    }
}