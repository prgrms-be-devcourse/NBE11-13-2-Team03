const saleGrid = document.getElementById("sale-grid");
const filterButtons = document.querySelectorAll(".filter-button");

let sales = [];
let selectedStatus = "ALL";

document.addEventListener("DOMContentLoaded", () => {
    loadSales();
    bindFilterButtons();
});

async function loadSales() {
    try {
        const response = await fetch("/api/sales");

        if (!response.ok) {
            throw new Error("판매 목록 조회에 실패했습니다.");
        }

        sales = await response.json();

        renderSales();
    } catch (error) {
        console.error(error);

        saleGrid.innerHTML = `
            <p class="sale-empty-message">
                판매 목록을 불러오지 못했습니다.
            </p>
        `;
    }
}

function bindFilterButtons() {
    filterButtons.forEach(button => {
        button.addEventListener("click", () => {
            selectedStatus = button.dataset.status;

            filterButtons.forEach(item => {
                item.classList.remove("active");
            });

            button.classList.add("active");

            renderSales();
        });
    });
}

function renderSales() {
    const filteredSales =
        selectedStatus === "ALL"
            ? sales
            : sales.filter(sale => sale.status === selectedStatus);

    if (filteredSales.length === 0) {
        saleGrid.innerHTML = `
            <p class="sale-empty-message">
                해당 상태의 판매 상품이 없습니다.
            </p>
        `;

        return;
    }

    saleGrid.innerHTML = filteredSales
        .map(createSaleCard)
        .join("");
}

function createSaleCard(sale) {
    const statusInfo = getStatusInfo(sale.status);

    return `
        <a href="/sales/${sale.saleId}" class="sale-card">

            <div class="sale-image-wrap">
                <span class="status-badge ${statusInfo.className}">
                    ${statusInfo.label}
                </span>

                ${
                    sale.imageUrl
                        ? `<img
                            src="${escapeHtml(sale.imageUrl)}"
                            alt="${escapeHtml(sale.goodsName)}"
                            class="sale-image"
                       >`
                        : `
                        <div class="mock-product mock-keyring">
                            GUDIT
                        </div>
                    `
                }
            </div>

            <div class="sale-card-body">

                <div class="sale-card-top">
                    <h2>${escapeHtml(sale.goodsName)}</h2>

                    <span class="sale-price">
                        ${formatPrice(sale.price)}원
                    </span>
                </div>

                <p class="sale-description">
                    ${escapeHtml(sale.description || "Gudit 한정 굿즈입니다.")}
                </p>

                ${createSaleMeta(sale)}

            </div>
        </a>
    `;
}

function createSaleMeta(sale) {
    switch (sale.status) {
        case "READY":
            return `
                <div class="sale-meta">
                    <span>판매 시작</span>
                    <strong class="ready-emphasis">
                        ${formatDateTime(sale.startAt)}
                    </strong>
                </div>
            `;

        case "ON_SALE":
            return `
                <!--
                    TODO:
                    remainingStock / initialStock 응답 추가 후
                    기존 재고 수량 + progress bar로 교체
                -->
                <div class="sale-meta">
                    <span>판매 종료</span>
                    <strong class="stock-emphasis">
                        ${formatDateTime(sale.endAt)}
                    </strong>
                </div>
            `;

        case "SOLD_OUT":
            return `
                <div class="sale-meta sold-out-text">
                    <span>남은 수량</span>
                    <strong>0개</strong>
                </div>
            `;

        case "CLOSED":
            return `
                <div class="sale-meta">
                    <span>판매 상태</span>
                    <strong>종료</strong>
                </div>
            `;

        default:
            return "";
    }
}

function getStatusInfo(status) {
    switch (status) {
        case "READY":
            return {
                label: "판매 예정",
                className: "status-ready"
            };

        case "ON_SALE":
            return {
                label: "판매 중",
                className: "status-on-sale"
            };

        case "SOLD_OUT":
            return {
                label: "품절",
                className: "status-sold-out"
            };

        case "CLOSED":
            return {
                label: "판매 종료",
                className: "status-closed"
            };

        default:
            return {
                label: status,
                className: "status-closed"
            };
    }
}

function formatPrice(price) {
    return Number(price).toLocaleString("ko-KR");
}

function formatDateTime(dateTime) {
    if (!dateTime) {
        return "-";
    }

    const date = new Date(dateTime);

    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");

    return `${month}.${day} ${hour}:${minute}`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}