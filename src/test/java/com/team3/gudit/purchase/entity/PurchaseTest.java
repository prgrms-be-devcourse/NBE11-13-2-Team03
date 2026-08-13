package com.team3.gudit.purchase.entity;

import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.user.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PurchaseTest {

    @Test
    @DisplayName("구매 정보를 생성한다")
    void createPurchase() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);

        // when
        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                15000
        );

        // then
        assertThat(purchase.getUser()).isEqualTo(user);
        assertThat(purchase.getSale()).isEqualTo(sale);
        assertThat(purchase.getQuantity()).isEqualTo(1);
        assertThat(purchase.getPurchasePrice()).isEqualTo(15000);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PURCHASED);
        assertThat(purchase.getPurchasedAt()).isNotNull();
        assertThat(purchase.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("구매를 취소하면 상태와 취소 시간이 변경된다")
    void cancelPurchase() {
        // given
        User user = mock(User.class);
        Sale sale = mock(Sale.class);

        Purchase purchase = Purchase.create(
                user,
                sale,
                1,
                15000
        );

        // when
        purchase.cancel();

        // then
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELED);
        assertThat(purchase.getCanceledAt()).isNotNull();
    }
}