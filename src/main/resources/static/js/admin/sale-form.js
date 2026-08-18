const saleForm =
    document.getElementById("sale-form");

const saleFormTitle =
    document.getElementById("sale-form-title");

const saleFormDescription =
    document.getElementById("sale-form-description");

const goodsId =
    document.getElementById("goods-id");

const goodsPrice =
    document.getElementById("goods-price");

const goodsHelp =
    document.getElementById("goods-help");

const initialStock =
    document.getElementById("initial-stock");

const maxPurchaseQuantity =
    document.getElementById("max-purchase-quantity");

const startAt =
    document.getElementById("start-at");

const endAt =
    document.getElementById("end-at");

const saleSubmitButton =
    document.getElementById("sale-submit-button");

const saleFormMessage =
    document.getElementById("sale-form-message");

const saleId = getSaleId();

const editMode =
    saleId !== null;

let goodsList = [];

document.addEventListener(
    "DOMContentLoaded",
    initializeForm
);

saleForm.addEventListener(
    "submit",
    submitSale
);

goodsId.addEventListener(
    "change",
    renderSelectedGoodsPrice
);

async function initializeForm() {
    if (editMode) {
        configureEditMode();
    }

    await loadGoods();

    if (editMode) {
        await loadSale();
    }
}

function configureEditMode() {
    document.title =
        "판매 수정 | Gudit";

    saleFormTitle.textContent =
        "판매 수정";

    saleFormDescription.textContent =
        "등록된 판매의 수량과 판매 기간을 수정합니다.";

    saleSubmitButton.textContent =
        "수정 완료";

    goodsHelp.textContent =
        "판매 상품은 수정할 수 없습니다.";
}

async function loadGoods() {
    try {
        const response = await fetch(
            "/api/goods",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "상품 목록을 불러오지 못했습니다."
            );
        }

        goodsList =
            await response.json();

        renderGoodsOptions();

    } catch (error) {
        console.error(error);

        saleFormMessage.textContent =
            error.message;

        saleSubmitButton.disabled = true;
    }
}

function renderGoodsOptions() {
    const activeGoods =
        goodsList.filter(
            goods => goods.status === "ACTIVE"
        );

    goodsId.innerHTML = `
        <option value="" selected disabled>
            판매할 상품을 선택하세요
        </option>

        ${activeGoods
        .map(goods => `
                <option value="${goods.id}">
                    ${escapeHtml(goods.name)}
                </option>
            `)
        .join("")}
    `;
}

async function loadSale() {
    try {
        const response = await fetch(
            `/api/sales/${saleId}`,
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "판매 정보를 불러오지 못했습니다."
            );
        }

        const sale =
            await response.json();

        renderSale(sale);

    } catch (error) {
        console.error(error);

        saleFormMessage.textContent =
            error.message;

        saleSubmitButton.disabled = true;
    }
}

function renderSale(sale) {
    ensureGoodsOption(sale);

    goodsId.value =
        String(sale.goodsId);

    goodsId.disabled = true;

    goodsPrice.value =
        formatPrice(sale.price);

    initialStock.value =
        sale.initialStock;

    maxPurchaseQuantity.value =
        sale.maxPurchaseQuantity;

    startAt.value =
        toDateTimeLocal(sale.startAt);

    endAt.value =
        toDateTimeLocal(sale.endAt);
}

function ensureGoodsOption(sale) {
    const exists =
        [...goodsId.options].some(
            option =>
                option.value === String(sale.goodsId)
        );

    if (exists) {
        return;
    }

    const option =
        document.createElement("option");

    option.value =
        sale.goodsId;

    option.textContent =
        sale.goodsName;

    goodsId.appendChild(option);
}

function renderSelectedGoodsPrice() {
    const selectedGoods =
        goodsList.find(
            goods =>
                String(goods.id)
                === goodsId.value
        );

    if (!selectedGoods) {
        goodsPrice.value = "-";
        return;
    }

    goodsPrice.value =
        formatPrice(selectedGoods.price);
}

async function submitSale(event) {
    event.preventDefault();

    saleFormMessage.textContent = "";

    if (
        new Date(startAt.value)
        >= new Date(endAt.value)
    ) {
        saleFormMessage.textContent =
            "판매 종료 일시는 판매 시작 일시보다 이후여야 합니다.";

        return;
    }

    if (
        Number(maxPurchaseQuantity.value)
        > Number(initialStock.value)
    ) {
        saleFormMessage.textContent =
            "1인 최대 구매 수량은 초기 수량보다 클 수 없습니다.";

        return;
    }

    saleSubmitButton.disabled = true;

    saleSubmitButton.textContent =
        editMode
            ? "수정 중..."
            : "등록 중...";

    try {
        if (editMode) {
            await updateSale();
        } else {
            await createSale();
        }

        window.location.href =
            "/admin/sales";

    } catch (error) {
        console.error(error);

        saleFormMessage.textContent =
            error.message;

        saleSubmitButton.disabled = false;

        saleSubmitButton.textContent =
            editMode
                ? "수정 완료"
                : "판매 등록";
    }
}

async function createSale() {
    const request = {
        goodsId:
            Number(goodsId.value),

        initialStock:
            Number(initialStock.value),

        maxPurchaseQuantity:
            Number(maxPurchaseQuantity.value),

        startAt:
            formatRequestDateTime(startAt.value),

        endAt:
            formatRequestDateTime(endAt.value)
    };

    const response = await fetch(
        "/api/sales",
        {
            method: "POST",

            headers: {
                "Content-Type":
                    "application/json"
            },

            credentials: "include",

            body: JSON.stringify(request)
        }
    );

    await validateResponse(
        response,
        "판매 등록에 실패했습니다."
    );
}

async function updateSale() {
    const request = {
        initialStock:
            Number(initialStock.value),

        maxPurchaseQuantity:
            Number(maxPurchaseQuantity.value),

        startAt:
            formatRequestDateTime(startAt.value),

        endAt:
            formatRequestDateTime(endAt.value)
    };

    const response = await fetch(
        `/api/sales/${saleId}`,
        {
            method: "PATCH",

            headers: {
                "Content-Type":
                    "application/json"
            },

            credentials: "include",

            body: JSON.stringify(request)
        }
    );

    await validateResponse(
        response,
        "판매 수정에 실패했습니다."
    );
}

async function validateResponse(
    response,
    defaultMessage
) {
    if (response.ok) {
        return;
    }

    const errorBody =
        await response
            .json()
            .catch(() => null);

    throw new Error(
        errorBody?.message
        || defaultMessage
    );
}

function getSaleId() {
    const segments =
        window.location.pathname
            .split("/")
            .filter(Boolean);

    if (
        segments.length === 3
        && segments[0] === "admin"
        && segments[1] === "sales"
        && segments[2] === "new"
    ) {
        return null;
    }

    if (
        segments.length === 4
        && segments[0] === "admin"
        && segments[1] === "sales"
        && segments[3] === "edit"
    ) {
        return segments[2];
    }

    return null;
}

function formatRequestDateTime(value) {
    if (!value) {
        return null;
    }

    return `${value.replace("T", " ")}:00`;
}

function toDateTimeLocal(value) {
    if (!value) {
        return "";
    }

    return value
        .replace(" ", "T")
        .slice(0, 16);
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