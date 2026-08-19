const successGoodsName =
    document.getElementById("payment-success-goods-name");

const successQuantity =
    document.getElementById("payment-success-quantity");

const successAmount =
    document.getElementById("payment-success-amount");

const successStatus =
    document.getElementById("payment-success-status");

const successDescription =
    document.getElementById("payment-success-description");

document.addEventListener(
    "DOMContentLoaded",
    confirmPayment
);

async function confirmPayment() {
    const params =
        new URLSearchParams(
            window.location.search
        );

    const paymentKey =
        params.get("paymentKey");

    const orderId =
        params.get("orderId");

    const amount =
        Number(params.get("amount"));

    const storedPayment =
        getStoredPayment();

    renderPaymentInfo(
        storedPayment,
        amount
    );

    if (
        !paymentKey
        || !orderId
        || !amount
    ) {
        moveToFail(
            "결제 승인 정보가 올바르지 않습니다."
        );

        return;
    }

    try {
        const response = await fetch(
            "/api/payments/confirm",
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/json"
                },

                credentials: "include",

                body: JSON.stringify({
                    paymentKey,
                    orderId,
                    amount
                })
            }
        );

        if (!response.ok) {
            const errorBody =
                await response
                    .json()
                    .catch(() => null);

            throw new Error(
                errorBody?.message
                || "결제 승인에 실패했습니다."
            );
        }

        const result =
            await response.json();

        renderSuccess(
            storedPayment,
            result
        );

        sessionStorage.removeItem(
            "payment"
        );

    } catch (error) {
        console.error(error);

        moveToFail(
            error.message
        );
    }
}

function renderPaymentInfo(
    payment,
    amount
) {
    if (!payment) {
        successGoodsName.textContent =
            "-";

        successQuantity.textContent =
            "-";

        successAmount.textContent =
            amount
                ? `${formatPrice(amount)}원`
                : "-";

        return;
    }

    successGoodsName.textContent =
        payment.goodsName || "-";

    successQuantity.textContent =
        `${payment.quantity}개`;

    successAmount.textContent =
        `${formatPrice(
            amount || payment.amount
        )}원`;

    successDescription.textContent =
        `${payment.goodsName} 구매가 정상적으로 완료되었습니다.`;
}

function renderSuccess(
    payment,
    result
) {
    if (payment) {
        successGoodsName.textContent =
            payment.goodsName || "-";

        successQuantity.textContent =
            `${payment.quantity}개`;

        successDescription.textContent =
            `${payment.goodsName} 구매가 정상적으로 완료되었습니다.`;
    }

    successAmount.textContent =
        `${formatPrice(
            result.totalAmount
        )}원`;

    successStatus.textContent =
        "결제 완료";
}

function getStoredPayment() {
    const storedPayment =
        sessionStorage.getItem(
            "payment"
        );

    if (!storedPayment) {
        return null;
    }

    try {
        return JSON.parse(
            storedPayment
        );
    } catch (error) {
        console.error(error);

        return null;
    }
}

function moveToFail(message) {
    sessionStorage.setItem(
        "paymentError",
        message
    );

    window.location.href =
        "/payments/fail";
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}