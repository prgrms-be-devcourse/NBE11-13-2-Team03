const purchaseId = getPurchaseId();

const detail =
    document.getElementById("purchase-detail");

const purchaseDetailImage =
    document.getElementById("purchase-detail-image");

const purchaseStatusBadge =
    document.getElementById("purchase-status-badge");

const purchaseGoodsName =
    document.getElementById("purchase-goods-name");

const purchasePrice =
    document.getElementById("purchase-price");

const purchaseQuantity =
    document.getElementById("purchase-quantity");

const purchaseTotalPrice =
    document.getElementById("purchase-total-price");

const purchaseStatusText =
    document.getElementById("purchase-status-text");

const purchaseDate =
    document.getElementById("purchase-date");

const purchaseCancelButton =
    document.getElementById("purchase-cancel-button");

const purchaseDetailMessage =
    document.getElementById("purchase-detail-message");

document.addEventListener("DOMContentLoaded", () => {
    loadPurchaseDetail();
});

async function loadPurchaseDetail() {
    try {
        const response = await fetch(
            `/api/purchases/${purchaseId}`,
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "구매 상세 정보를 불러오지 못했습니다."
            );
        }

        const purchase = await response.json();

        renderPurchaseDetail(purchase);

    } catch (error) {
        console.error(error);

        detail.innerHTML = `
            <p class="purchase-empty-message">
                구매 상세 정보를 불러오지 못했습니다.
            </p>
        `;
    }
}

async function cancelPurchase() {
    const confirmed =
        window.confirm("구매를 취소하시겠습니까?");

    if (!confirmed) {
        return;
    }

    purchaseCancelButton.disabled = true;
    purchaseCancelButton.textContent = "취소 처리 중...";

    try {
        const response = await fetch(
            `/api/purchases/${purchaseId}/cancel`,
            {
                method: "POST",
                credentials: "include"
            }
        );

        if (!response.ok) {
            const errorBody =
                await response
                    .json()
                    .catch(() => null);

            throw new Error(
                errorBody?.message
                || "구매 취소에 실패했습니다."
            );
        }

        await response.json();

        await loadPurchaseDetail();

    } catch (error) {
        console.error(error);

        purchaseDetailMessage.textContent =
            error.message;

        purchaseCancelButton.disabled = false;
        purchaseCancelButton.textContent =
            "구매 취소";
    }
}

function renderPurchaseDetail(purchase) {
    const statusInfo =
        getStatusInfo(purchase.status);

    document.title =
        `${purchase.goodsName} | 구매 상세`;

    purchaseStatusBadge.textContent =
        statusInfo.label;

    purchaseStatusBadge.className =
        `purchase-status ${statusInfo.className}`;

    purchaseGoodsName.textContent =
        purchase.goodsName;

    purchasePrice.textContent =
        `${formatPrice(purchase.purchasePrice)}원`;

    purchaseQuantity.textContent =
        `${purchase.quantity}개`;

    purchaseTotalPrice.textContent =
        `${formatPrice(purchase.purchasePrice)}원`;

    purchaseStatusText.textContent =
        statusInfo.label;

    purchaseDate.textContent =
        formatDateTime(purchase.purchasedAt);

    renderImage(purchase);
    renderPurchaseMessage(purchase);
    renderCancelButton(purchase);
}

function renderImage(purchase) {
    if (!purchase.imageUrl) {
        return;
    }

    purchaseDetailImage.innerHTML = `
        <img
                src="${escapeHtml(purchase.imageUrl)}"
                alt="${escapeHtml(purchase.goodsName)}"
                class="purchase-image">
    `;
}

function renderPurchaseMessage(purchase) {
    switch (purchase.status) {
        case "PURCHASED":
            purchaseDetailMessage.innerHTML = `
                구매가 완료되었습니다.<br>
                구매 취소가 가능한 경우 아래 버튼을 통해 취소할 수 있습니다.
            `;
            break;

        case "PENDING_PAYMENT":
            purchaseDetailMessage.textContent =
                "결제가 아직 완료되지 않았습니다.";
            break;

        case "CANCELED":
            purchaseDetailMessage.textContent =
                "취소된 구매입니다.";
            break;

        default:
            purchaseDetailMessage.textContent = "";
    }
}

function renderCancelButton(purchase) {
    if (
        purchase.status !== "PURCHASED"
        && purchase.status !== "PENDING_PAYMENT"
    ) {
        purchaseCancelButton.hidden = true;
        return;
    }

    purchaseCancelButton.hidden = false;
    purchaseCancelButton.disabled = false;
    purchaseCancelButton.textContent = "구매 취소";

    purchaseCancelButton.onclick = cancelPurchase;
}

function getStatusInfo(status) {
    switch (status) {
        case "PENDING_PAYMENT":
            return {
                label: "결제 대기",
                className: "status-pending"
            };

        case "PURCHASED":
            return {
                label: "구매 완료",
                className: "status-purchased"
            };

        case "CANCELED":
            return {
                label: "취소",
                className: "status-canceled"
            };

        default:
            return {
                label: status,
                className: ""
            };
    }
}

function getPurchaseId() {
    const segments =
        window.location.pathname
            .split("/")
            .filter(Boolean);

    return segments[segments.length - 1];
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}

function formatDateTime(dateTime) {
    if (!dateTime) {
        return "-";
    }

    const date =
        new Date(dateTime);

    const year =
        date.getFullYear();

    const month =
        String(date.getMonth() + 1)
            .padStart(2, "0");

    const day =
        String(date.getDate())
            .padStart(2, "0");

    const hour =
        String(date.getHours())
            .padStart(2, "0");

    const minute =
        String(date.getMinutes())
            .padStart(2, "0");

    return `${year}.${month}.${day} ${hour}:${minute}`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}