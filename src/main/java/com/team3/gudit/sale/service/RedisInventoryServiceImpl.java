package com.team3.gudit.sale.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.global.exception.GlobalErrorCode;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.dto.SaleRedisDto;
import com.team3.gudit.sale.exception.SaleErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Primary
@Service
@RequiredArgsConstructor
public class RedisInventoryServiceImpl implements InventoryService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> stockDecrementScript;
    private final DefaultRedisScript<Long> stockRestoreScript;
    private final SaleRepository saleRepository;

    @Override
    public void decreaseStock(Long saleId, Long userId, int quantity) {
        validateQuantity(quantity);

        String stockKey = "sale:" + saleId + ":stock";
        String infoKey = "sale:" + saleId + ":info";
        String userKey = "sale:" + saleId + ":user:" + userId;

        long nowMilli = Instant.now().toEpochMilli();

        Long result = redisTemplate.execute(
                stockDecrementScript,
                List.of(stockKey, infoKey, userKey),
                String.valueOf(quantity),
                String.valueOf(nowMilli)
        );

        if (result == null) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (result < 0) {
            handleScriptError(result);
        }
    }

    @Override
    public void restoreStock(Long saleId, Long userId, int quantity) {
        validateQuantity(quantity);

        String stockKey = "sale:" + saleId + ":stock";
        String userKey = "sale:" + saleId + ":user:" + userId;

        Long result = redisTemplate.execute(
                stockRestoreScript,
                List.of(stockKey, userKey),
                String.valueOf(quantity)
        );

        if (result == null) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        if (result < 0) {
            handleRestoreScriptError(result);
        }
    }

    // 비즈니스 에러 처리 (-1: 재고부족, -2: 기간아님, -3: 수량초과, -4: 종료)
    private void handleScriptError(long errorCode) {
        if (errorCode == -1) {
            throw new BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK);
        } else if (errorCode == -2) {
            throw new BusinessException(SaleErrorCode.INVALID_SALE_PERIOD);
        } else if (errorCode == -3) {
            throw new BusinessException(SaleErrorCode.EXCEEDED_PURCHASE_QUANTITY);
        } else if (errorCode == -4) {
            throw new BusinessException(SaleErrorCode.SALE_CLOSED);
        } else {
            throw new BusinessException(SaleErrorCode.NOT_ENOUGH_STOCK);
        }
    }

    private void handleRestoreScriptError(
            long errorCode
    ) {
        if (errorCode == -1) {
            throw new BusinessException(
                    SaleErrorCode.REDIS_STOCK_NOT_FOUND
            );
        }

        throw new BusinessException(
                GlobalErrorCode.INTERNAL_SERVER_ERROR
        );
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(
                    SaleErrorCode.INVALID_PURCHASE_QUANTITY
            );
        }
    }
}