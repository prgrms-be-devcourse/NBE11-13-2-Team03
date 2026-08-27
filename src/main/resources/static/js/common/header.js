const loginLink =
    document.getElementById("login-link");

const adminLink =
    document.getElementById("admin-link");

const userArea =
    document.getElementById("user-area");

const userName =
    document.getElementById("user-name");

const logoutButton =
    document.getElementById("logout-button");

document.addEventListener(
    "DOMContentLoaded",
    loadCurrentUser
);

logoutButton.addEventListener(
    "click",
    logout
);

async function loadCurrentUser() {
    try {
        const response = await fetch(
            "/api/users/me",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            renderLoggedOut();
            return;
        }

        const user =
            await response.json();

        renderLoggedIn(user);

    } catch (error) {
        console.error(error);

        renderLoggedOut();
    }
}

function renderLoggedIn(user) {
    loginLink.hidden = true;
    userArea.hidden = false;

    userName.textContent =
        `${user.nickname}님`;

    adminLink.hidden =
        user.role !== "ADMIN";
}

function renderLoggedOut() {
    loginLink.hidden = false;
    userArea.hidden = true;
    adminLink.hidden = true;
}

async function logout() {
    logoutButton.disabled = true;
    logoutButton.textContent =
        "로그아웃 중...";

    try {
        const response = await fetch(
            "/api/auth/logout",
            {
                method: "POST",
                credentials: "include"
            }
        );

        if (!response.ok) {
            throw new Error(
                "로그아웃에 실패했습니다."
            );
        }

        renderLoggedOut();

        window.location.href = "/";

    } catch (error) {
        console.error(error);

        logoutButton.disabled = false;
        logoutButton.textContent =
            "로그아웃";

        alert(error.message);
    }
}