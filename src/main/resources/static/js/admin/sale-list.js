const saleList =
    document.getElementById("admin-sale-list");

const saleTotalCount =
    document.getElementById("sale-total-count");

const saleOnCount =
    document.getElementById("sale-on-count");

const saleReadyCount =
    document.getElementById("sale-ready-count");

const saleFinishedCount =
    document.getElementById("sale-finished-count");

const saleTableCount =
    document.getElementById("sale-table-count");

document.addEventListener("DOMContentLoaded", () => {
    loadSales();
});

async function loadSales() {
    try {
        const response = await fetch(
            "/api/sales",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "판매 목록 조회에 실패했습니다."
            );
        }

        const sales = await response.json();

        renderSummary(sales);
        renderSales(sales);

    } catch (error) {
        console.error(error);

        saleList.innerHTML = `
            <tr>
                <td colspan="6">
                    <p class="admin-empty-message">
                        판매 목록을 불러오지 못했습니다.
                    </p>
                </td>
            </tr>
        `;
    }
}

function renderSummary(sales) {
    const total = sales.length;

    const onSale =
        sales.filter(
            sale => sale.status === "ON_SALE"
        ).length;

    const ready =
        sales.filter(
            sale => sale.status === "READY"
        ).length;

    const finished =
        sales.filter(
            sale =>
                sale.status === "SOLD_OUT"
                || sale.status === "CLOSED"
        ).length;

    saleTotalCount.textContent = total;
    saleOnCount.textContent = onSale;
    saleReadyCount.textContent = ready;
    saleFinishedCount.textContent = finished;

    saleTableCount.textContent =
        `총 ${total}건`;
}

function renderSales(sales) {
    if (!sales || sales.length === 0) {
        saleList.innerHTML = `
            <tr>
                <td colspan="6">
                    <p class="admin-empty-message">
                        등록된 판매가 없습니다.
                    </p>
                </td>
            </tr>
        `;

        return;
    }

    saleList.innerHTML =
        sales
            .map(createSaleRow)
            .join("");
}

function createSaleRow(sale) {
    const statusInfo =
        getSaleStatusInfo(sale.status);

    return `
        <tr>

            <td>
                <div class="admin-product">

                    <div class="admin-product-thumbnail">
                        G
                    </div>

                    <div>
                        <strong>
                            ${escapeHtml(sale.goodsName)}
                        </strong>

                        <span>
                            판매 ID ${sale.saleId}
                        </span>
                    </div>

                </div>
            </td>

            <td>
                ${formatPrice(sale.price)}원
            </td>

            <td>
                <div class="admin-stock ${sale.remainingStock === 0 ? "sold-out-stock" : ""}">

                    <strong>
                        ${sale.remainingStock ?? "-"}
                    </strong>

                    <span>
                        / ${sale.initialStock ?? "-"}개
                    </span>

                </div>
            </td>

            <td>
                <span class="admin-sale-status ${statusInfo.className}">
                    ${statusInfo.label}
                </span>
            </td>

            <td>
                <div class="admin-sale-period">
                    ${formatDateTime(sale.startAt)}
                    ~
                    ${formatDateTime(sale.endAt)}
                </div>
            </td>

            <td class="admin-action-cell">

                <a
                        href="/admin/sales/${sale.saleId}/edit"
                        class="admin-edit-button">
                    수정
                </a>

            </td>

        </tr>
    `;
}

function getSaleStatusInfo(status) {
    switch (status) {
        case "READY":
            return {
                label: "판매 예정",
                className: "admin-sale-ready"
            };

        case "ON_SALE":
            return {
                label: "판매 중",
                className: "admin-sale-on"
            };

        case "SOLD_OUT":
            return {
                label: "품절",
                className: "admin-sale-sold-out"
            };

        case "CLOSED":
            return {
                label: "판매 종료",
                className: "admin-sale-closed"
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

    const date = new Date(dateTime);

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