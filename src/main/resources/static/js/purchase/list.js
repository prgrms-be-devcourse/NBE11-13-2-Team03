const list =
    document.getElementById("purchase-list");

const filterButtons =
    document.querySelectorAll(".purchase-filter .filter-button");

let purchases = [];
let selectedStatus = "ALL";

document.addEventListener("DOMContentLoaded", () => {
    loadPurchases();
    bindFilterButtons();
});

async function loadPurchases() {
    try {
        const response = await fetch(
            "/api/purchases",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "구매 내역 조회에 실패했습니다."
            );
        }

        const result = await response.json();

        purchases = result.purchases || [];

        renderPurchases();

    } catch (error) {
        console.error(error);

        list.innerHTML = `
            <p class="purchase-empty-message">
                구매 내역을 불러오지 못했습니다.
            </p>
        `;
    }
}

function bindFilterButtons() {
    filterButtons.forEach(button => {
        button.addEventListener("click", () => {
            selectedStatus =
                button.dataset.status;

            filterButtons.forEach(item => {
                item.classList.remove("active");
            });

            button.classList.add("active");

            renderPurchases();
        });
    });
}

function renderPurchases() {
    const filteredPurchases =
        selectedStatus === "ALL"
            ? purchases
            : purchases.filter(
                purchase =>
                    purchase.status === selectedStatus
            );

    if (filteredPurchases.length === 0) {
        list.innerHTML = `
            <p class="purchase-empty-message">
                해당 상태의 구매 내역이 없습니다.
            </p>
        `;

        return;
    }

    list.innerHTML =
        filteredPurchases
            .map(createPurchaseItem)
            .join("");
}

function createPurchaseItem(purchase) {
    const statusInfo =
        getStatusInfo(purchase.status);

    return `
        <article class="purchase-item">

            <div class="purchase-thumbnail">
                ${createPurchaseImage(purchase)}
            </div>

            <div class="purchase-item-content">

                <div class="purchase-item-main">

                    <div>

                        <span class="purchase-status ${statusInfo.className}">
                            ${statusInfo.label}
                        </span>

                        <h2>
                            ${escapeHtml(purchase.goodsName)}
                        </h2>

                        <p class="purchase-item-price">
                            ${formatPrice(purchase.purchasePrice)}원
                        </p>

                    </div>

                    <a
                            href="/mypage/purchases/${purchase.purchaseId}"
                            class="detail-link-button">
                        상세 보기
                    </a>

                </div>

                <div class="purchase-item-meta">

                    <div>
                        <span>수량</span>
                        <strong>
                            ${purchase.quantity}개
                        </strong>
                    </div>

                    <div>
                        <span>구매일</span>
                        <strong>
                            ${formatDateTime(purchase.purchasedAt)}
                        </strong>
                    </div>

                </div>

            </div>

        </article>
    `;
}

function createPurchaseImage(purchase) {
    if (purchase.imageUrl) {
        return `
            <img
                    src="${escapeHtml(purchase.imageUrl)}"
                    alt="${escapeHtml(purchase.goodsName)}"
                    class="purchase-image">
        `;
    }

    return `
        <div class="purchase-mock purchase-mock-keyring">
            GUDIT
        </div>
    `;
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