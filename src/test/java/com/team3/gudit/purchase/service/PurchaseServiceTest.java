package com.team3.gudit.purchase.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
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

import java.time.LocalDateTime;
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
        assertThatThrownBy(() ->
                purchaseService.purchase(
                        userId,
                        saleId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PurchaseErrorCode.DUPLICATE_PURCHASE
                            );
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
                            .isEqualTo(
                                    PurchaseErrorCode.PURCHASE_NOT_FOUND
                            );
                });
    }

    @Test
    @DisplayName("이미 취소된 구매를 다시 취소하면 예외가 발생한다")
    void cancelAlreadyCanceledPurchase() {
        // given
        Purchase lockedPurchase = mock(Purchase.class);
        Payment payment = mock(Payment.class);

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        ))
                .willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(lockedPurchase));

        given(lockedPurchase.getStatus())
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
                                    PurchaseErrorCode
                                            .PURCHASE_ALREADY_CANCELED
                            );
                });

        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(purchaseRepository)
                .findByIdAndUserIdWithLock(
                        purchaseId,
                        userId
                );
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
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

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
        assertThat(response.saleId())
                .isEqualTo(saleId);

        assertThat(response.quantity())
                .isEqualTo(1);

        assertThat(response.purchasePrice())
                .isEqualTo(15000);

        assertThat(response.status())
                .isEqualTo(PurchaseStatus.PENDING_PAYMENT);

        assertThat(response.purchasedAt())
                .isNull();

        assertThat(response.orderId())
                .isEqualTo("GUDIT_test-order-id");

        verify(inventoryService)
                .decreaseStock(
                        saleId,
                        userId,
                        1
                );

        verify(purchaseRepository)
                .save(any(Purchase.class));

        verify(paymentService)
                .createPayment(any(Purchase.class));
    }

    @Test
    @DisplayName("결제 대기 중인 구매를 취소하면 READY 결제를 취소하고 재고를 복구한다")
    void cancelPendingPaymentSuccess() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);

        given(sale.getId())
                .willReturn(saleId);

        given(sale.getEndAt())
                .willReturn(
                        LocalDateTime.now()
                                .plusHours(1)
                );

        Purchase lockedPurchase = Purchase.create(
                user,
                sale,
                1,
                15_000
        );

        Payment payment = Payment.create(
                lockedPurchase,
                15_000
        );

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        ))
                .willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(lockedPurchase));

        // when
        PurchaseCancelResponse response =
                purchaseService.cancel(
                        userId,
                        purchaseId
                );

        // then
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        assertThat(payment.getCanceledAt())
                .isNotNull();

        assertThat(response.status())
                .isEqualTo(PurchaseStatus.CANCELED);

        assertThat(response.canceledAt())
                .isNotNull();

        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(inventoryService)
                .restoreStock(
                        saleId,
                        userId,
                        1
                );
    }

    @Test
    @DisplayName("결제 완료된 구매를 취소하면 결제를 취소하고 재고를 복구한다")
    void cancelCompletedPurchaseSuccess() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);
        Payment payment = mock(Payment.class);

        given(sale.getId())
                .willReturn(saleId);

        given(sale.getEndAt())
                .willReturn(
                        LocalDateTime.now()
                                .plusHours(1)
                );

        Purchase lockedPurchase = Purchase.create(
                user,
                sale,
                1,
                15_000
        );

        lockedPurchase.complete();

        given(paymentService.getPaymentByPurchaseIdWithLock(
                purchaseId
        ))
                .willReturn(payment);

        given(purchaseRepository.findByIdAndUserIdWithLock(
                purchaseId,
                userId
        ))
                .willReturn(Optional.of(lockedPurchase));

        given(payment.getPaymentKey())
                .willReturn("payment-key");

        // when
        PurchaseCancelResponse response =
                purchaseService.cancel(
                        userId,
                        purchaseId
                );

        // then
        verify(paymentService)
                .getPaymentByPurchaseIdWithLock(
                        purchaseId
                );

        verify(paymentService)
                .cancelCompletedPayment(
                        "payment-key"
                );

        verify(inventoryService)
                .restoreStock(
                        saleId,
                        userId,
                        1
                );

        assertThat(lockedPurchase.getStatus())
                .isEqualTo(PurchaseStatus.CANCELED);

        assertThat(response.status())
                .isEqualTo(PurchaseStatus.CANCELED);

        assertThat(response.canceledAt())
                .isNotNull();
    }
}