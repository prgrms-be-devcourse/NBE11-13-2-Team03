package com.team3.gudit.purchase.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.purchase.dto.PurchaseCancelResponse;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.exception.PurchaseErrorCode;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.InventoryService;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PurchaseService purchaseService;

    private Long userId;
    private Long saleId;
    private Long purchaseId;

    @BeforeEach
    void setUp() {
        userId = 1L;
        saleId = 10L;
        purchaseId = 100L;
    }

    @Test
    @DisplayName("이미 구매한 판매 상품을 다시 구매하면 예외가 발생한다")
    void purchaseDuplicate() {
        // given
        given(purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(
                userId,
                saleId,
                PurchaseStatus.CANCELED
        ))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> purchaseService.purchase(userId, saleId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(PurchaseErrorCode.DUPLICATE_PURCHASE);
                });

        verify(purchaseRepository)
                .existsByUserIdAndSaleIdAndStatusNot(
                        userId,
                        saleId,
                        PurchaseStatus.CANCELED
                );
    }

    @Test
    @DisplayName("존재하지 않는 구매 내역을 조회하면 예외가 발생한다")
    void getPurchaseNotFound() {
        // given
        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> purchaseService.getPurchase(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(PurchaseErrorCode.PURCHASE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("이미 취소된 구매를 다시 취소하면 예외가 발생한다")
    void cancelAlreadyCanceledPurchase() {
        // given
        Purchase purchase = mock(Purchase.class);

        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(purchase));

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.CANCELED);

        // when & then
        assertThatThrownBy(
                () -> purchaseService.cancel(
                        userId,
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PurchaseErrorCode.PURCHASE_ALREADY_CANCELED
                            );
                });
    }

    @Test
    @DisplayName("판매 상품 구매를 요청하면 재고를 차감하고 결제 대기 상태의 구매와 결제를 생성한다")
    void purchaseSuccess() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);
        Goods goods = mock(Goods.class);
        Payment payment = mock(Payment.class);

        given(purchaseRepository.existsByUserIdAndSaleIdAndStatusNot(
                userId,
                saleId,
                PurchaseStatus.CANCELED
        ))
                .willReturn(false);

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(saleRepository.findById(saleId))
                .willReturn(Optional.of(sale));

        given(sale.getId())
                .willReturn(saleId);

        given(sale.getGoods())
                .willReturn(goods);

        given(goods.getPrice())
                .willReturn(15000);

        given(purchaseRepository.save(any(Purchase.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(paymentService.createPayment(any(Purchase.class)))
                .willReturn(payment);

        given(payment.getOrderId())
                .willReturn("GUDIT_test-order-id");

        // when
        PurchaseCreateResponse response =
                purchaseService.purchase(
                        userId,
                        saleId
                );

        // then
        assertThat(response.saleId()).isEqualTo(saleId);
        assertThat(response.quantity()).isEqualTo(1);
        assertThat(response.purchasePrice()).isEqualTo(15000);
        assertThat(response.status())
                .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        assertThat(response.purchasedAt()).isNull();
        assertThat(response.orderId())
                .isEqualTo("GUDIT_test-order-id");

        verify(inventoryService)
                .decreaseStock(saleId, 1);

        verify(purchaseRepository)
                .save(any(Purchase.class));

        verify(paymentService)
                .createPayment(any(Purchase.class));
    }

    @Test
    @DisplayName("결제 대기 중인 구매를 취소하면 재고를 복구하고 구매를 취소한다")
    void cancelPendingPaymentSuccess() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);

        given(sale.getId())
                .willReturn(saleId);

        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                15000
        );

        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(purchase));

        // when
        PurchaseCancelResponse response =
                purchaseService.cancel(
                        userId,
                        purchaseId
                );

        // then
        assertThat(response.status())
                .isEqualTo(PurchaseStatus.CANCELED);

        assertThat(response.canceledAt())
                .isNotNull();

        verify(inventoryService)
                .restoreStock(saleId, 1);
    }

    @Test
    @DisplayName("결제 완료된 구매를 취소하면 결제를 취소하고 재고를 복구한다")
    void cancelCompletedPurchaseSuccess() {
        // given
        Purchase purchase = mock(Purchase.class);
        Payment payment = mock(Payment.class);
        Sale sale = mock(Sale.class);

        given(purchaseRepository.findByIdAndUserId(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(purchase));

        given(purchase.getId())
                .willReturn(purchaseId);

        given(purchase.getStatus())
                .willReturn(PurchaseStatus.PURCHASED);

        given(purchase.getSale())
                .willReturn(sale);

        given(purchase.getQuantity())
                .willReturn(1);

        given(sale.getId())
                .willReturn(saleId);

        given(paymentService.getPaymentByPurchaseId(purchaseId))
                .willReturn(payment);

        given(payment.getPaymentKey())
                .willReturn("payment-key");

        // when
        purchaseService.cancel(
                userId,
                purchaseId
        );

        // then
        verify(paymentService)
                .getPaymentByPurchaseId(purchaseId);

        verify(paymentService)
                .cancelCompletedPayment("payment-key");

        verify(inventoryService)
                .restoreStock(saleId, 1);

        verify(purchase)
                .cancel();
    }
}