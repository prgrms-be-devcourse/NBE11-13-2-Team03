const saleId = getSaleId();

const saleStatusBadge = document.getElementById("sale-status-badge");
const saleName = document.getElementById("sale-name");
const salePrice = document.getElementById("sale-price");

const saleStatusText = document.getElementById("sale-status-text");
const saleStock = document.getElementById("sale-stock");
const saleStockBar = document.getElementById("sale-stock-bar");
const salePeriod = document.getElementById("sale-period");
const saleMaxPurchase = document.getElementById("sale-max-purchase");

const countdownCard = document.getElementById("countdown-card");
const countdownLabel = document.getElementById("countdown-label");
const countdownHours = document.getElementById("countdown-hours");
const countdownMinutes = document.getElementById("countdown-minutes");
const countdownSeconds = document.getElementById("countdown-seconds");

const purchaseButton = document.getElementById("purchase-button");
const purchaseNotice = document.getElementById("purchase-notice");

let countdownTimer = null;
let currentSale = null;

document.addEventListener("DOMContentLoaded", () => {
    loadSaleDetail();

    purchaseButton.addEventListener("click", purchase);
});

async function loadSaleDetail() {
    try {
        const response = await fetch(`/api/sales/${saleId}`, {
            credentials: "include"
        });

        if (!response.ok) {
            throw new Error("판매 상세 조회에 실패했습니다.");
        }

        currentSale = await response.json();

        renderSaleDetail(currentSale);
    } catch (error) {
        console.error(error);

        document.getElementById("sale-detail").innerHTML = `
            <p class="sale-empty-message">
                판매 정보를 불러오지 못했습니다.
            </p>
        `;
    }
}

function renderSaleDetail(sale) {
    const statusInfo = getStatusInfo(sale.status);

    document.title = `${sale.goodsName} | Gudit`;

    saleStatusBadge.textContent = statusInfo.label;
    saleStatusBadge.className =
        `status-badge detail-status ${statusInfo.className}`;

    saleName.textContent = sale.goodsName;
    salePrice.textContent = `${formatPrice(sale.price)}원`;

    saleStatusText.textContent = statusInfo.label;
    saleStatusText.className = statusInfo.textClassName;

    saleStock.textContent =
        `${sale.remainingStock} / ${sale.initialStock}`;

    renderStockBar(
        sale.remainingStock,
        sale.initialStock
    );

    salePeriod.textContent =
        `${formatFullDateTime(sale.startAt)} ~ ${formatFullDateTime(sale.endAt)}`;

    saleMaxPurchase.textContent =
        `${sale.maxPurchaseQuantity}개`;

    renderPurchaseState(sale);
    startCountdown(sale);
}

async function purchase() {
    if (!currentSale) {
        return;
    }

    purchaseButton.disabled = true;
    purchaseButton.textContent = "구매 처리 중...";
    purchaseNotice.textContent = "";

    try {
        const response = await fetch(
            `/api/sales/${saleId}/purchases`,
            {
                method: "POST",
                credentials: "include"
            }
        );

        if (!response.ok) {
            const errorBody = await response.json().catch(() => null);

            throw new Error(
                errorBody?.message || "구매에 실패했습니다."
            );
        }

        const purchase = await response.json();

        sessionStorage.setItem(
            "payment",
            JSON.stringify({
                purchaseId: purchase.purchaseId,
                saleId: purchase.saleId,
                quantity: purchase.quantity,
                amount: purchase.purchasePrice,
                orderId: purchase.orderId,
                status: purchase.status,
                goodsName: currentSale.goodsName
            })
        );

        window.location.href = "/payments";

    } catch (error) {
        console.error(error);

        purchaseNotice.textContent = error.message;

        purchaseButton.disabled = false;
        purchaseButton.textContent = "구매하기";
    }
}

function renderStockBar(remainingStock, initialStock) {
    if (!initialStock || initialStock <= 0) {
        saleStockBar.style.width = "0%";
        return;
    }

    const percentage = Math.max(
        0,
        Math.min(
            100,
            (remainingStock / initialStock) * 100
        )
    );

    saleStockBar.style.width = `${percentage}%`;
}

function renderPurchaseState(sale) {
    switch (sale.status) {
        case "ON_SALE":
            purchaseButton.disabled = false;
            purchaseButton.textContent = "구매하기";

            purchaseNotice.textContent =
                `현재 구매 가능합니다. 한 계정당 최대 ${sale.maxPurchaseQuantity}개까지 구매할 수 있습니다.`;
            break;

        case "READY":
            purchaseButton.disabled = true;
            purchaseButton.textContent = "판매 예정";

            purchaseNotice.textContent =
                `${formatFullDateTime(sale.startAt)}부터 구매할 수 있습니다.`;
            break;

        case "SOLD_OUT":
            purchaseButton.disabled = true;
            purchaseButton.textContent = "품절";

            purchaseNotice.textContent =
                "준비된 수량이 모두 판매되었습니다.";
            break;

        case "CLOSED":
            purchaseButton.disabled = true;
            purchaseButton.textContent = "판매 종료";

            purchaseNotice.textContent =
                "판매가 종료된 상품입니다.";
            break;

        default:
            purchaseButton.disabled = true;
            purchaseButton.textContent = "구매 불가";

            purchaseNotice.textContent =
                "현재 구매할 수 없는 상품입니다.";
    }
}

function startCountdown(sale) {
    clearInterval(countdownTimer);
    countdownCard.hidden = false;

    if (sale.status === "ON_SALE") {
        countdownLabel.textContent =
            "판매 종료까지 남은 시간";

        updateCountdown(sale.endAt);

        countdownTimer = setInterval(() => {
            updateCountdown(sale.endAt);
        }, 1000);

        return;
    }

    if (sale.status === "READY") {
        countdownLabel.textContent =
            "판매 시작까지 남은 시간";

        updateCountdown(sale.startAt);

        countdownTimer = setInterval(() => {
            updateCountdown(sale.startAt);
        }, 1000);

        return;
    }

    countdownCard.hidden = true;
}

function updateCountdown(targetDateTime) {
    const target = new Date(targetDateTime).getTime();
    const now = Date.now();

    const difference = target - now;

    if (difference <= 0) {
        clearInterval(countdownTimer);

        countdownHours.textContent = "00";
        countdownMinutes.textContent = "00";
        countdownSeconds.textContent = "00";

        return;
    }

    const hours =
        Math.floor(difference / (1000 * 60 * 60));

    const minutes =
        Math.floor(
            (difference % (1000 * 60 * 60))
            / (1000 * 60)
        );

    const seconds =
        Math.floor(
            (difference % (1000 * 60))
            / 1000
        );

    countdownHours.textContent =
        String(hours).padStart(2, "0");

    countdownMinutes.textContent =
        String(minutes).padStart(2, "0");

    countdownSeconds.textContent =
        String(seconds).padStart(2, "0");
}

function getStatusInfo(status) {
    switch (status) {
        case "READY":
            return {
                label: "판매 예정",
                className: "status-ready",
                textClassName: ""
            };

        case "ON_SALE":
            return {
                label: "판매 중",
                className: "status-on-sale",
                textClassName: "on-sale-text"
            };

        case "SOLD_OUT":
            return {
                label: "품절",
                className: "status-sold-out",
                textClassName: "sold-out-text"
            };

        case "CLOSED":
            return {
                label: "판매 종료",
                className: "status-closed",
                textClassName: ""
            };

        default:
            return {
                label: status,
                className: "status-closed",
                textClassName: ""
            };
    }
}

function getSaleId() {
    const segments = window.location.pathname
        .split("/")
        .filter(Boolean);

    return segments[segments.length - 1];
}

function formatPrice(price) {
    return Number(price).toLocaleString("ko-KR");
}

function formatFullDateTime(dateTime) {
    if (!dateTime) {
        return "-";
    }

    const date = new Date(dateTime);

    const year = date.getFullYear();
    const month =
        String(date.getMonth() + 1).padStart(2, "0");
    const day =
        String(date.getDate()).padStart(2, "0");
    const hour =
        String(date.getHours()).padStart(2, "0");
    const minute =
        String(date.getMinutes()).padStart(2, "0");

    return `${year}.${month}.${day} ${hour}:${minute}`;
}