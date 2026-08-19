const goodsName = document.getElementById("payment-goods-name");
const quantity = document.getElementById("payment-quantity");
const productPrice = document.getElementById("payment-product-price");

const summaryPrice = document.getElementById("payment-summary-price");
const summaryQuantity = document.getElementById("payment-summary-quantity");

const totalPrice = document.getElementById("payment-total-price");
const paymentButton = document.getElementById("payment-button");
const paymentBackLink = document.getElementById("payment-back-link");

let currentPayment = null;
let payment = null;

document.addEventListener("DOMContentLoaded", () => {
    loadPayment();
});

function loadPayment() {
    const storedPayment = sessionStorage.getItem("payment");

    if (!storedPayment) {
        handleMissingPayment();
        return;
    }

    currentPayment = JSON.parse(storedPayment);

    const tossPayments = TossPayments(window.TOSS_CLIENT_KEY);

    payment = tossPayments.payment({
        customerKey: TossPayments.ANONYMOUS
    });

    renderPayment(currentPayment);

    paymentButton.addEventListener("click", requestPayment);
}

function renderPayment(payment) {
    goodsName.textContent =
        payment.goodsName || "-";

    quantity.textContent =
        `${payment.quantity}개`;

    productPrice.textContent =
        `${formatPrice(payment.amount)}원`;

    summaryPrice.textContent =
        `${formatPrice(payment.amount)}원`;

    summaryQuantity.textContent =
        `${payment.quantity}개`;

    totalPrice.textContent =
        `${formatPrice(payment.amount)}원`;

    paymentButton.textContent =
        `${formatPrice(payment.amount)}원 결제하기`;

    paymentBackLink.href =
        `/sales/${payment.saleId}`;
}

async function requestPayment() {
    if (!currentPayment) {
        return;
    }

    sessionStorage.removeItem("paymentError");

    if (!window.TOSS_CLIENT_KEY) {
        alert("결제 설정 정보를 불러오지 못했습니다.");
        return;
    }

    paymentButton.disabled = true;
    paymentButton.textContent = "결제창 여는 중...";

    try {
        await payment.requestPayment({
            method: "CARD",
            amount: {
                currency: "KRW",
                value: currentPayment.amount
            },
            orderId: currentPayment.orderId,
            orderName: currentPayment.goodsName,
            successUrl: `${window.location.origin}/payments/success`,
            failUrl: `${window.location.origin}/payments/fail`
        });
    } catch (error) {
        const isUserCanceled =
            error.code === "PAY_PROCESS_CANCELED"
            || error.code === "USER_CANCEL"
            || error.message?.includes("취소")
            || error.message?.toLowerCase().includes("cancel");

        if (isUserCanceled) {
            try {
                await cancelPurchase(currentPayment.purchaseId);
            } catch (cancelError) {
                console.error("구매 취소 처리 실패:", cancelError);
            }
        }

        sessionStorage.setItem(
            "paymentError",
            JSON.stringify({
                code: error.code,
                message: error.message
            })
        );

        window.location.href = "/payments/fail";
    }
}

async function cancelPurchase(purchaseId) {
    const response = await fetch(`/api/purchases/${purchaseId}/cancel`, {
        method: "POST",
        credentials: "include"
    });

    if (!response.ok) {
        throw new Error("구매 취소 처리에 실패했습니다.");
    }
}

function handleMissingPayment() {
    goodsName.textContent =
        "결제 정보가 없습니다.";

    quantity.textContent = "-";
    productPrice.textContent = "-";
    summaryPrice.textContent = "-";
    summaryQuantity.textContent = "-";
    totalPrice.textContent = "-";

    paymentButton.disabled = true;
    paymentButton.textContent =
        "결제 정보 없음";
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}