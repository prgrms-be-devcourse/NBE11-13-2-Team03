const goodsName =
    document.getElementById("payment-goods-name");

const quantity =
    document.getElementById("payment-quantity");

const productPrice =
    document.getElementById("payment-product-price");

const summaryPrice =
    document.getElementById("payment-summary-price");

const summaryQuantity =
    document.getElementById("payment-summary-quantity");

const totalPrice =
    document.getElementById("payment-total-price");

const paymentButton =
    document.getElementById("payment-button");

const paymentBackLink =
    document.getElementById("payment-back-link");

const paymentProductImage =
    document.getElementById("payment-product-image");

const paymentTimeLeft =
    document.getElementById("payment-time-left");

const paymentTimeout =
    document.getElementById("payment-timeout");

let currentPayment = null;
let payment = null;
let paymentTimeoutTimer = null;

document.addEventListener("DOMContentLoaded", () => {
    loadPayment();
});

function loadPayment() {
    const storedPayment =
        sessionStorage.getItem("payment");

    if (!storedPayment) {
        handleMissingPayment();
        return;
    }

    currentPayment =
        JSON.parse(storedPayment);

    const tossPayments =
        TossPayments(window.TOSS_CLIENT_KEY);

    payment = tossPayments.payment({
        customerKey: TossPayments.ANONYMOUS
    });

    renderPayment(currentPayment);
    startPaymentTimeout(currentPayment.createdAt);

    paymentButton.addEventListener(
        "click",
        requestPayment
    );
}

function renderPayment(paymentData) {
    renderPaymentImage(paymentData);

    goodsName.textContent =
        paymentData.goodsName || "-";

    quantity.textContent =
        `${paymentData.quantity}개`;

    productPrice.textContent =
        `${formatPrice(paymentData.amount)}원`;

    summaryPrice.textContent =
        `${formatPrice(paymentData.amount)}원`;

    summaryQuantity.textContent =
        `${paymentData.quantity}개`;

    totalPrice.textContent =
        `${formatPrice(paymentData.amount)}원`;

    paymentButton.textContent =
        `${formatPrice(paymentData.amount)}원 결제하기`;

    paymentBackLink.href =
        `/sales/${paymentData.saleId}`;
}

function startPaymentTimeout(createdAt) {
    if (!createdAt) {
        paymentTimeLeft.textContent = "-";
        return;
    }

    const expiresAt =
        new Date(createdAt).getTime()
        + 10 * 60 * 1000;

    updatePaymentTimeout(expiresAt);

    paymentTimeoutTimer = setInterval(
        () => {
            updatePaymentTimeout(expiresAt);
        },
        1000
    );
}

function updatePaymentTimeout(expiresAt) {
    const remaining =
        expiresAt - Date.now();

    if (remaining <= 0) {
        clearInterval(paymentTimeoutTimer);

        paymentTimeLeft.textContent =
            "00:00";

        paymentTimeout.classList.add(
            "is-urgent"
        );

        paymentButton.disabled = true;
        paymentButton.textContent =
            "결제 시간 만료";

        return;
    }

    if (remaining <= 60 * 1000) {
        paymentTimeout.classList.add(
            "is-urgent"
        );
    } else {
        paymentTimeout.classList.remove(
            "is-urgent"
        );
    }

    const minutes =
        Math.floor(
            remaining / (1000 * 60)
        );

    const seconds =
        Math.floor(
            (remaining % (1000 * 60))
            / 1000
        );

    paymentTimeLeft.textContent =
        `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function renderPaymentImage(paymentData) {
    if (!paymentData.imageUrl) {
        paymentProductImage.innerHTML = `
            <div class="detail-mock-keyring">
                GUDIT
            </div>
        `;

        return;
    }

    paymentProductImage.innerHTML = `
        <img
            src="${escapeHtml(paymentData.imageUrl)}"
            alt="${escapeHtml(paymentData.goodsName)}"
            class="payment-real-image"
        >
    `;
}

async function requestPayment() {
    if (!currentPayment) {
        return;
    }

    if (!currentPayment.createdAt) {
        alert("결제 시간 정보를 확인할 수 없습니다.");
        return;
    }

    const expiresAt =
        new Date(currentPayment.createdAt).getTime()
        + 10 * 60 * 1000;

    if (Date.now() >= expiresAt) {
        paymentButton.disabled = true;
        paymentButton.textContent =
            "결제 시간 만료";

        paymentTimeLeft.textContent =
            "00:00";

        return;
    }

    sessionStorage.removeItem("paymentError");

    if (!window.TOSS_CLIENT_KEY) {
        alert("결제 설정 정보를 불러오지 못했습니다.");
        return;
    }

    paymentButton.disabled = true;
    paymentButton.textContent =
        "결제창 여는 중...";

    try {
        await payment.requestPayment({
            method: "CARD",
            amount: {
                currency: "KRW",
                value: currentPayment.amount
            },
            orderId: currentPayment.orderId,
            orderName: currentPayment.goodsName,
            successUrl:
                `${window.location.origin}/payments/success`,
            failUrl:
                `${window.location.origin}/payments/fail`
        });

    } catch (error) {
        const isUserCanceled =
            error.code === "PAY_PROCESS_CANCELED"
            || error.code === "USER_CANCEL";

        if (isUserCanceled) {
            try {
                await cancelPurchase(
                    currentPayment.purchaseId
                );
            } catch (cancelError) {
                console.error(
                    "구매 취소 처리 실패:",
                    cancelError
                );
            }
        }

        sessionStorage.setItem(
            "paymentError",
            JSON.stringify({
                code: error.code,
                message: error.message
            })
        );

        window.location.href =
            "/payments/fail";
    }
}

async function cancelPurchase(purchaseId) {
    const response = await fetch(
        `/api/purchases/${purchaseId}/cancel`,
        {
            method: "POST",
            credentials: "include"
        }
    );

    if (!response.ok) {
        throw new Error(
            "구매 취소 처리에 실패했습니다."
        );
    }
}

function handleMissingPayment() {
    if (paymentTimeoutTimer) {
        clearInterval(paymentTimeoutTimer);
    }

    paymentProductImage.innerHTML = `
        <div class="detail-mock-keyring">
            GUDIT
        </div>
    `;

    goodsName.textContent =
        "결제 정보가 없습니다.";

    quantity.textContent = "-";
    productPrice.textContent = "-";
    summaryPrice.textContent = "-";
    summaryQuantity.textContent = "-";
    totalPrice.textContent = "-";
    paymentTimeLeft.textContent = "-";

    paymentButton.disabled = true;
    paymentButton.textContent =
        "결제 정보 없음";
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}