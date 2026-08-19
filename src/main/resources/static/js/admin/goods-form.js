const goodsForm =
    document.getElementById("goods-form");

const goodsFormTitle =
    document.getElementById("goods-form-title");

const goodsFormDescription =
    document.getElementById("goods-form-description");

const goodsName =
    document.getElementById("goods-name");

const goodsPrice =
    document.getElementById("goods-price");

const goodsDescription =
    document.getElementById("goods-description");

const goodsImage =
    document.getElementById("goods-image");

const goodsImageHelp =
    document.getElementById("goods-image-help");

const goodsStatusGroup =
    document.getElementById("goods-status-group");

const goodsStatus =
    document.getElementById("goods-status");

const goodsSubmitButton =
    document.getElementById("goods-submit-button");

const goodsFormMessage =
    document.getElementById("goods-form-message");

const goodsId = getGoodsId();

const editMode =
    goodsId !== null;

let currentGoods = null;

document.addEventListener(
    "DOMContentLoaded",
    initializeForm
);

goodsForm.addEventListener(
    "submit",
    submitGoods
);

async function initializeForm() {
    if (!editMode) {
        return;
    }

    goodsFormTitle.textContent =
        "상품 수정";

    goodsFormDescription.textContent =
        "등록된 굿즈의 기본 정보와 상태를 수정합니다.";

    goodsSubmitButton.textContent =
        "수정 완료";

    goodsStatusGroup.hidden = false;

    goodsImageHelp.textContent =
        "새 이미지를 선택하지 않으면 기존 이미지가 유지됩니다.";

    await loadGoods();
}

async function loadGoods() {
    try {
        const response = await fetch(
            `/api/goods/${goodsId}`,
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "상품 정보를 불러오지 못했습니다."
            );
        }

        currentGoods =
            await response.json();

        renderGoods(currentGoods);

    } catch (error) {
        console.error(error);

        goodsFormMessage.textContent =
            error.message;

        goodsSubmitButton.disabled = true;
    }
}

function renderGoods(goods) {
    goodsName.value =
        goods.name ?? "";

    goodsPrice.value =
        goods.price ?? "";

    goodsDescription.value =
        goods.description ?? "";

    goodsStatus.value =
        goods.status;
}

async function submitGoods(event) {
    event.preventDefault();

    goodsFormMessage.textContent = "";

    goodsSubmitButton.disabled = true;

    goodsSubmitButton.textContent =
        editMode
            ? "수정 중..."
            : "등록 중...";

    try {
        if (editMode) {
            await updateGoods();
            await updateGoodsStatus();
        } else {
            await createGoods();
        }

        window.location.href =
            "/admin/goods";

    } catch (error) {
        console.error(error);

        goodsFormMessage.textContent =
            error.message;

        goodsSubmitButton.disabled = false;

        goodsSubmitButton.textContent =
            editMode
                ? "수정 완료"
                : "상품 등록";
    }
}

async function createGoods() {
    const formData =
        createGoodsFormData();

    const response = await fetch(
        "/api/goods",
        {
            method: "POST",
            credentials: "include",
            body: formData
        }
    );

    await validateResponse(
        response,
        "상품 등록에 실패했습니다."
    );
}

async function updateGoods() {
    const formData =
        createGoodsFormData();

    const response = await fetch(
        `/api/goods/${goodsId}`,
        {
            method: "PUT",
            credentials: "include",
            body: formData
        }
    );

    await validateResponse(
        response,
        "상품 수정에 실패했습니다."
    );
}

async function updateGoodsStatus() {
    if (
        !currentGoods
        || currentGoods.status === goodsStatus.value
    ) {
        return;
    }

    const response = await fetch(
        `/api/goods/${goodsId}/status`,
        {
            method: "PATCH",

            headers: {
                "Content-Type":
                    "application/json"
            },

            credentials: "include",

            body: JSON.stringify({
                status: goodsStatus.value
            })
        }
    );

    await validateResponse(
        response,
        "상품 상태 변경에 실패했습니다."
    );
}

function createGoodsFormData() {
    const request = {
        name:
            goodsName.value.trim(),

        description:
            goodsDescription.value.trim()
            || null,

        price:
            Number(goodsPrice.value),

        imageUrl:
            currentGoods?.imageUrl
            || null
    };

    const formData =
        new FormData();

    formData.append(
        "request",
        new Blob(
            [JSON.stringify(request)],
            {
                type: "application/json"
            }
        )
    );

    if (
        goodsImage.files.length > 0
    ) {
        formData.append(
            "fileImage",
            goodsImage.files[0]
        );
    }

    return formData;
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

function getGoodsId() {
    const segments =
        window.location.pathname
            .split("/")
            .filter(Boolean);

    if (
        segments.length === 3
        && segments[0] === "admin"
        && segments[1] === "goods"
        && segments[2] === "new"
    ) {
        return null;
    }

    if (
        segments.length === 4
        && segments[0] === "admin"
        && segments[1] === "goods"
        && segments[3] === "edit"
    ) {
        return segments[2];
    }

    return null;
}