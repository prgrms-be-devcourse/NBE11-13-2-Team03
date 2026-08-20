const failGoodsName =
    document.getElementById("payment-fail-goods-name");

const failQuantity =
    document.getElementById("payment-fail-quantity");

const failAmount =
    document.getElementById("payment-fail-amount");

const failDescription =
    document.getElementById("payment-fail-description");

const failBackLink =
    document.getElementById("payment-fail-back-link");

const retryLink =
    document.getElementById("payment-retry-link");

const failTitle =
    document.querySelector(".payment-result-card h1");

const failEyebrow =
    document.querySelector(".payment-fail-eyebrow");

document.addEventListener(
    "DOMContentLoaded",
    loadPaymentFail
);

function loadPaymentFail() {
    const params =
        new URLSearchParams(
            window.location.search
        );

    const tossCode =
        params.get("code");

    const tossMessage =
        params.get("message");

    const storedError =
        getStoredError();

    const payment =
        getStoredPayment();

    renderPayment(payment);

    const errorCode =
        storedError?.code
        || tossCode
        || "";

    const errorMessage =
        storedError?.message
        || tossMessage
        || "결제 처리 중 문제가 발생했습니다.";

    renderFailureState(
        errorCode,
        errorMessage
    );

    sessionStorage.removeItem(
        "paymentError"
    );
}

function renderFailureState(
    code,
    message
) {
    if (isCanceledPayment(code, message)) {
        failTitle.textContent =
            "결제가 취소되었습니다";

        failEyebrow.textContent =
            "PAYMENT CANCELED";

        failDescription.textContent =
            "결제를 취소했습니다. 다시 결제할 수 있습니다.";

        return;
    }

    failTitle.textContent =
        "결제에 실패했습니다";

    failEyebrow.textContent =
        "PAYMENT FAILED";

    failDescription.textContent =
        message;
}

function isCanceledPayment(
    code,
    message
) {
    return (
        code === "PAY_PROCESS_CANCELED"
        || code === "USER_CANCEL"
        || message.includes("취소")
        || message
            .toLowerCase()
            .includes("cancel")
    );
}

function renderPayment(payment) {
    if (!payment) {
        retryLink.href =
            "/sales";

        return;
    }

    failGoodsName.textContent =
        payment.goodsName || "-";

    failQuantity.textContent =
        `${payment.quantity}개`;

    failAmount.textContent =
        `${formatPrice(payment.amount)}원`;

    failBackLink.href =
        `/sales/${payment.saleId}`;

    retryLink.href =
        "/payments";
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

function getStoredError() {
    const storedError =
        sessionStorage.getItem(
            "paymentError"
        );

    if (!storedError) {
        return null;
    }

    try {
        return JSON.parse(
            storedError
        );
    } catch (error) {
        return {
            code: "",
            message: storedError
        };
    }
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}