const goodsList =
    document.getElementById("admin-goods-list");

const goodsTotalCount =
    document.getElementById("goods-total-count");

const goodsEnabledCount =
    document.getElementById("goods-enabled-count");

const goodsDisabledCount =
    document.getElementById("goods-disabled-count");

const goodsTableCount =
    document.getElementById("goods-table-count");

document.addEventListener("DOMContentLoaded", () => {
    loadGoods();
});

async function loadGoods() {
    try {
        const response = await fetch(
            "/api/goods/admin",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "상품 목록 조회에 실패했습니다."
            );
        }

        const goods = await response.json();

        renderSummary(goods);
        renderGoods(goods);

    } catch (error) {
        console.error(error);

        goodsList.innerHTML = `
            <tr>
                <td colspan="5">
                    <p class="admin-empty-message">
                        상품 목록을 불러오지 못했습니다.
                    </p>
                </td>
            </tr>
        `;
    }
}

function renderSummary(goods) {
    const total = goods.length;

    const enabled =
        goods.filter(
            item => isEnabledStatus(item.status)
        ).length;

    const disabled =
        goods.filter(
            item => !isEnabledStatus(item.status)
        ).length;

    goodsTotalCount.textContent = total;
    goodsEnabledCount.textContent = enabled;
    goodsDisabledCount.textContent = disabled;

    goodsTableCount.textContent =
        `총 ${total}개`;
}

function renderGoods(goods) {
    if (!goods || goods.length === 0) {
        goodsList.innerHTML = `
            <tr>
                <td colspan="5">
                    <p class="admin-empty-message">
                        등록된 상품이 없습니다.
                    </p>
                </td>
            </tr>
        `;

        return;
    }

    goodsList.innerHTML =
        goods
            .map(createGoodsRow)
            .join("");
}

function createGoodsRow(goods) {
    const statusInfo =
        getGoodsStatusInfo(goods.status);

    return `
        <tr>

            <td>
                <div class="admin-product">

                    <div class="admin-product-thumbnail">
                        ${createGoodsImage(goods)}
                    </div>

                    <div>
                        <strong>
                            ${escapeHtml(goods.name)}
                        </strong>
                    </div>

                </div>
            </td>

            <td>
                ${formatPrice(goods.price)}원
            </td>

            <td>
                <span class="admin-status ${statusInfo.className}">
                    ${statusInfo.label}
                </span>
            </td>

            <td>
                ${formatDate(goods.createdAt)}
            </td>

            <td class="admin-action-cell">
                <a
                        href="/admin/goods/${goods.id}/edit"
                        class="admin-edit-button">
                    수정
                </a>
            </td>

        </tr>
    `;
}

function createGoodsImage(goods) {
    if (goods.imageUrl) {
        return `
            <img
                    src="${escapeHtml(goods.imageUrl)}"
                    alt="${escapeHtml(goods.name)}"
                    class="admin-product-image">
        `;
    }

    return "G";
}

function getGoodsStatusInfo(status) {
    switch (status) {
        case "ACTIVE":
            return {
                label: "판매 가능",
                className: "status-enabled"
            };

        case "INACTIVE":
            return {
                label: "판매 중지",
                className: "status-disabled"
            };

        default:
            return {
                label: status,
                className: ""
            };
    }
}

function isEnabledStatus(status) {
    return status === "ACTIVE";
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}

function formatDate(dateTime) {
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

    return `${year}.${month}.${day}`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}