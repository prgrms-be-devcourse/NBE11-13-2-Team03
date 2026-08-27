package com.team3.gudit.payment.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.payment.client.TossPaymentClient;
import com.team3.gudit.payment.dto.PaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentCancelRequest;
import com.team3.gudit.payment.dto.TossPaymentConfirmRequest;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.exception.PaymentErrorCode;
import com.team3.gudit.payment.exception.TossPaymentException;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.entity.Purchase;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("구매 정보로 READY 상태의 결제를 생성한다")
    void createPayment() {
        // given
        Purchase purchase = mock(Purchase.class);

        given(purchase.getPurchasePrice())
                .willReturn(15000);

        given(paymentRepository.save(any(Payment.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Payment payment = paymentService.createPayment(purchase);

        // then
        assertThat(payment.getPurchase())
                .isEqualTo(purchase);

        assertThat(payment.getAmount())
                .isEqualTo(15000);

        assertThat(payment.getOrderId())
                .startsWith("GUDIT_");

        verify(paymentRepository)
                .save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 승인에 성공하면 결제 시작 후 Toss 승인과 완료 처리를 수행한다")
    void confirmSuccess() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response);

        // when
        TossPaymentResponse result =
                paymentService.confirm(request);

        // then
        assertThat(result)
                .isSameAs(response);

        verify(paymentTransactionService)
                .startPayment(
                        orderId,
                        paymentKey,
                        amount
                );

        verify(tossPaymentClient)
                .confirm(
                        any(TossPaymentConfirmRequest.class),
                        eq("GUDIT_CONFIRM_" + orderId)
                );

        verify(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                );
    }

    @Test
    @DisplayName("카드사 거절처럼 명확한 승인 실패는 결제 실패 처리 후 예외를 발생시킨다")
    void confirmDefiniteFailure() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        new TossPaymentException(
                                "REJECT_CARD_COMPANY",
                                "카드사에서 결제를 거절했습니다."
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_CONFIRM_FAILED
                            );
                });

        verify(paymentTransactionService)
                .startPayment(
                        orderId,
                        paymentKey,
                        amount
                );

        verify(paymentTransactionService)
                .failPayment(orderId);

        verify(tossPaymentClient, never())
                .getPayment(paymentKey);
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하지만 재조회 결과가 DONE이면 결제를 완료한다")
    void confirmUncertainFailureReconcileDone() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        new TossPaymentException(
                                "NETWORK_ERROR",
                                "네트워크 오류"
                        )
                );

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response);

        given(response.status())
                .willReturn("DONE");

        // when
        TossPaymentResponse result =
                paymentService.confirm(request);

        // then
        assertThat(result)
                .isSameAs(response);

        verify(tossPaymentClient)
                .getPayment(paymentKey);

        verify(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                );

        verify(paymentTransactionService, never())
                .failPayment(orderId);
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하고 재조회 결과도 DONE이 아니면 처리 중 예외가 발생한다")
    void confirmUncertainFailureReconcileNotDone() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        new TossPaymentException(
                                "NETWORK_ERROR",
                                "네트워크 오류"
                        )
                );

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response);

        given(response.status())
                .willReturn("IN_PROGRESS");

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR
                            );
                });

        verify(tossPaymentClient)
                .getPayment(paymentKey);

        verify(paymentTransactionService, never())
                .failPayment(orderId);

        verify(paymentTransactionService, never())
                .completePayment(
                        eq(orderId),
                        any(TossPaymentResponse.class)
                );
    }

    @Test
    @DisplayName("결제 승인 결과가 불확실하고 Toss 재조회도 실패하면 처리 중 예외가 발생한다")
    void confirmUncertainFailureReconcileFailure() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        new TossPaymentException(
                                "NETWORK_ERROR",
                                "승인 요청 네트워크 오류"
                        )
                );

        given(tossPaymentClient.getPayment(paymentKey))
                .willThrow(
                        new TossPaymentException(
                                "NETWORK_ERROR",
                                "조회 요청 네트워크 오류"
                        )
                );

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_PROCESSING_ERROR
                            );
                });

        verify(tossPaymentClient)
                .getPayment(paymentKey);

        verify(paymentTransactionService, never())
                .failPayment(orderId);
    }

    @Test
    @DisplayName("Toss 승인은 성공했지만 DB 완료 처리에 실패하면 승인 취소 보상을 수행한다")
    void confirmFinalizationFailureCompensates() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response);

        willThrow(new RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                );

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_FINALIZATION_FAILED
                            );
                });

        verify(tossPaymentClient)
                .cancel(
                        eq(paymentKey),
                        any(TossPaymentCancelRequest.class),
                        eq("GUDIT_CANCEL_" + paymentKey)
                );

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);
    }

    @Test
    @DisplayName("승인 후 DB 실패에 대한 보상 취소까지 실패하면 보상 실패 예외가 발생한다")
    void confirmCompensationFailure() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willReturn(response);

        willThrow(new RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                );

        given(tossPaymentClient.cancel(
                eq(paymentKey),
                any(TossPaymentCancelRequest.class),
                eq("GUDIT_CANCEL_" + paymentKey)
        ))
                .willThrow(
                        new RuntimeException("Toss 보상 취소 실패")
                );

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_COMPENSATION_FAILED
                            );
                });

        verify(paymentTransactionService, never())
                .compensateApprovalFailure(paymentKey);
    }

    @Test
    @DisplayName("재조회 결과 DONE 이후 DB 완료 처리에 실패해도 승인 취소 보상을 수행한다")
    void reconcileDoneFinalizationFailureCompensates() {
        // given
        String paymentKey = "payment-key";
        String orderId = "GUDIT_test-order-id";
        int amount = 15000;

        PaymentConfirmRequest request =
                new PaymentConfirmRequest(
                        paymentKey,
                        orderId,
                        amount
                );

        TossPaymentResponse response =
                mock(TossPaymentResponse.class);

        given(tossPaymentClient.confirm(
                any(TossPaymentConfirmRequest.class),
                eq("GUDIT_CONFIRM_" + orderId)
        ))
                .willThrow(
                        new TossPaymentException(
                                "NETWORK_ERROR",
                                "승인 요청 네트워크 오류"
                        )
                );

        given(tossPaymentClient.getPayment(paymentKey))
                .willReturn(response);

        given(response.status())
                .willReturn("DONE");

        willThrow(new RuntimeException("DB 처리 실패"))
                .given(paymentTransactionService)
                .completePayment(
                        orderId,
                        response
                );

        // when & then
        assertThatThrownBy(
                () -> paymentService.confirm(request)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_FINALIZATION_FAILED
                            );
                });

        verify(tossPaymentClient)
                .getPayment(paymentKey);

        verify(tossPaymentClient)
                .cancel(
                        eq(paymentKey),
                        any(TossPaymentCancelRequest.class),
                        eq("GUDIT_CANCEL_" + paymentKey)
                );

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);
    }

    @Test
    @DisplayName("결제 완료 후 취소하면 Toss 결제를 취소하고 DB 취소 상태를 반영한다")
    void cancelCompletedPayment() {
        // given
        String paymentKey = "payment-key";

        // when
        paymentService.cancelCompletedPayment(paymentKey);

        // then
        verify(tossPaymentClient)
                .cancel(
                        eq(paymentKey),
                        any(TossPaymentCancelRequest.class),
                        eq("GUDIT_CANCEL_" + paymentKey)
                );

        verify(paymentTransactionService)
                .completeCancel(paymentKey);
    }

    @Test
    @DisplayName("구매 ID로 결제 정보를 조회한다")
    void getPaymentByPurchaseId() {
        // given
        Long purchaseId = 100L;
        Payment payment = mock(Payment.class);

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.of(payment));

        // when
        Payment result =
                paymentService.getPaymentByPurchaseId(purchaseId);

        // then
        assertThat(result)
                .isSameAs(payment);
    }

    @Test
    @DisplayName("구매 ID에 해당하는 결제가 없으면 예외가 발생한다")
    void getPaymentByPurchaseIdNotFound() {
        // given
        Long purchaseId = 100L;

        given(paymentRepository.findByPurchaseId(purchaseId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> paymentService.getPaymentByPurchaseId(
                        purchaseId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    PaymentErrorCode.PAYMENT_NOT_FOUND
                            );
                });
    }
}