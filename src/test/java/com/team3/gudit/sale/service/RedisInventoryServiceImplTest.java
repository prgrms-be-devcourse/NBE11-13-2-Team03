package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.global.exception.ErrorCode;
import com.team3.gudit.global.exception.GlobalErrorCode;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.exception.SaleErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RedisInventoryServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DefaultRedisScript<Long> stockDecrementScript;

    @Mock
    private DefaultRedisScript<Long> stockRestoreScript;

    @Mock
    private SaleRepository saleRepository;

    private RedisInventoryServiceImpl inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new RedisInventoryServiceImpl(
                redisTemplate,
                stockDecrementScript,
                stockRestoreScript,
                saleRepository
        );
    }

    @Test
    @DisplayName("Lua 재고 차감 결과가 1이면 정상 처리한다")
    void decreaseStockSuccess() {
        // given
        Long saleId = 1L;
        Long userId = 10L;
        int quantity = 2;

        List<String> expectedKeys = List.of(
                "sale:1:stock",
                "sale:1:info",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockDecrementScript),
                eq(expectedKeys),
                any(Object[].class)
        )).willReturn(1L);

        // when & then
        assertThatCode(() ->
                inventoryService.decreaseStock(
                        saleId,
                        userId,
                        quantity
                )
        ).doesNotThrowAnyException();

        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        verify(redisTemplate).execute(
                eq(stockDecrementScript),
                eq(expectedKeys),
                argumentsCaptor.capture()
        );

        Object[] scriptArguments =
                argumentsCaptor.getValue();

        assertThat(scriptArguments).hasSize(2);
        assertThat(scriptArguments[0])
                .isEqualTo(String.valueOf(quantity));

        // 두 번째 인자는 현재 시각의 epoch millisecond다.
        assertThat(scriptArguments[1])
                .isInstanceOf(String.class);
        assertThat(Long.parseLong(
                (String) scriptArguments[1]
        )).isPositive();
    }

    @ParameterizedTest(name = "Lua 결과 {0}이면 {1} 예외가 발생한다")
    @MethodSource("decrementErrorCodes")
    @DisplayName("Lua 재고 차감 오류 코드를 비즈니스 예외로 변환한다")
    void decreaseStockScriptError(
            Long scriptResult,
            SaleErrorCode expectedErrorCode
    ) {
        // given
        Long saleId = 1L;
        Long userId = 10L;
        int quantity = 1;

        List<String> keys = List.of(
                "sale:1:stock",
                "sale:1:info",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockDecrementScript),
                eq(keys),
                any(Object[].class)
        )).willReturn(scriptResult);

        // when & then
        assertBusinessError(
                () -> inventoryService.decreaseStock(
                        saleId,
                        userId,
                        quantity
                ),
                expectedErrorCode
        );
    }

    static Stream<Arguments> decrementErrorCodes() {
        return Stream.of(
                Arguments.of(
                        -1L,
                        SaleErrorCode.NOT_ENOUGH_STOCK
                ),
                Arguments.of(
                        -2L,
                        SaleErrorCode.INVALID_SALE_PERIOD
                ),
                Arguments.of(
                        -3L,
                        SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY
                ),
                Arguments.of(
                        -4L,
                        SaleErrorCode.SALE_CLOSED
                )
        );
    }

    @Test
    @DisplayName("Lua 재고 차감 결과가 null이면 내부 서버 오류가 발생한다")
    void decreaseStockNullResult() {
        // given
        List<String> keys = List.of(
                "sale:1:stock",
                "sale:1:info",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockDecrementScript),
                eq(keys),
                any(Object[].class)
        )).willReturn(null);

        // when & then
        assertBusinessError(
                () -> inventoryService.decreaseStock(
                        1L,
                        10L,
                        1
                ),
                GlobalErrorCode.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    @DisplayName("재고 차감 수량이 0이면 Redis를 호출하지 않고 예외가 발생한다")
    void decreaseStockWithZeroQuantity() {
        // when & then
        assertBusinessError(
                () -> inventoryService.decreaseStock(
                        1L,
                        10L,
                        0
                ),
                SaleErrorCode.INVALID_PURCHASE_QUANTITY
        );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("재고 차감 수량이 음수이면 Redis를 호출하지 않고 예외가 발생한다")
    void decreaseStockWithNegativeQuantity() {
        // when & then
        assertBusinessError(
                () -> inventoryService.decreaseStock(
                        1L,
                        10L,
                        -1
                ),
                SaleErrorCode.INVALID_PURCHASE_QUANTITY
        );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Lua 재고 복구 결과가 양수이면 정상 처리한다")
    void restoreStockSuccess() {
        // given
        Long saleId = 1L;
        Long userId = 10L;
        int quantity = 2;

        List<String> expectedKeys = List.of(
                "sale:1:stock",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockRestoreScript),
                eq(expectedKeys),
                any(Object[].class)
        )).willReturn(2L);

        // when & then
        assertThatCode(() ->
                inventoryService.restoreStock(
                        saleId,
                        userId,
                        quantity
                )
        ).doesNotThrowAnyException();

        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);

        verify(redisTemplate).execute(
                eq(stockRestoreScript),
                eq(expectedKeys),
                argumentsCaptor.capture()
        );

        Object[] scriptArguments =
                argumentsCaptor.getValue();

        assertThat(scriptArguments).containsExactly(
                String.valueOf(quantity)
        );
    }

    @Test
    @DisplayName("이미 복구되어 Lua 결과가 0이면 중복 복구 없이 정상 종료한다")
    void restoreStockAlreadyRestored() {
        // given
        List<String> keys = List.of(
                "sale:1:stock",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockRestoreScript),
                eq(keys),
                any(Object[].class)
        )).willReturn(0L);

        // when & then
        assertThatCode(() ->
                inventoryService.restoreStock(
                        1L,
                        10L,
                        1
                )
        ).doesNotThrowAnyException();

        verify(redisTemplate).execute(
                eq(stockRestoreScript),
                eq(keys),
                any(Object[].class)
        );
    }

    @Test
    @DisplayName("Lua 재고 복구 결과가 null이면 내부 서버 오류가 발생한다")
    void restoreStockNullResult() {
        // given
        List<String> keys = List.of(
                "sale:1:stock",
                "sale:1:user:10"
        );

        given(redisTemplate.execute(
                eq(stockRestoreScript),
                eq(keys),
                any(Object[].class)
        )).willReturn(null);

        // when & then
        assertBusinessError(
                () -> inventoryService.restoreStock(
                        1L,
                        10L,
                        1
                ),
                GlobalErrorCode.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    @DisplayName("재고 복구 수량이 0이면 Redis를 호출하지 않고 예외가 발생한다")
    void restoreStockWithZeroQuantity() {
        // when & then
        assertBusinessError(
                () -> inventoryService.restoreStock(
                        1L,
                        10L,
                        0
                ),
                SaleErrorCode.INVALID_PURCHASE_QUANTITY
        );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("재고 복구 수량이 음수이면 Redis를 호출하지 않고 예외가 발생한다")
    void restoreStockWithNegativeQuantity() {
        // when & then
        assertBusinessError(
                () -> inventoryService.restoreStock(
                        1L,
                        10L,
                        -1
                ),
                SaleErrorCode.INVALID_PURCHASE_QUANTITY
        );

        verifyNoInteractions(redisTemplate);
    }

    private void assertBusinessError(
            Runnable action,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(expectedErrorCode)
                );
    }
}