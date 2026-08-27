import { check } from "k6";
import { actorForUser } from "../lib/data.js";
import {
    cancelPurchase,
    confirmPayment
} from "../lib/client.js";

export const options = {
    scenarios: {
        payment_confirm: {
            executor: "per-vu-iterations",
            exec: "confirm",
            vus: 1,
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || "1m"
        },

        purchase_cancel: {
            executor: "per-vu-iterations",
            exec: "cancel",
            vus: 1,
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || "1m"
        }
    },

    thresholds: {
        checks: ["rate==1"]
    },

    summaryTrendStats: [
        "min",
        "avg",
        "med",
        "p(90)",
        "p(95)",
        "p(99)",
        "max"
    ],

    tags: {
        test_scenario: "payment-confirm-cancel-race"
    }
};

function responseCode(response) {
    try {
        return response.json("code");
    } catch (_) {
        return null;
    }
}

export function confirm() {
    const actor = actorForUser(1004);

    const response = confirmPayment(
        "PERF_PAYMENT_KEY_0003",
        "GUDIT_PERF_PAYMENT_CONFIRM_CANCEL_RACE_0003",
        10106,
        actor
    );

    const code = responseCode(response);

    check(response, {
        "payment confirm returns expected result": (r) =>
            r.status === 200 ||
            (
                r.status === 409 &&
                [
                    "PAYMENT_006",
                    "PURCHASE_004"
                ].includes(code)
            )
    });

    if (
        response.status !== 200 &&
        response.status !== 409
    ) {
        console.error(
            `Unexpected payment confirm response: status=${response.status}, code=${code}, body=${response.body}`
        );
    }
}

export function cancel() {
    const actor = actorForUser(1004);

    const response = cancelPurchase(
        3,
        actor
    );

    const code = responseCode(response);

    console.log(
        `Cancel response: status=${response.status}, code=${code}, body=${response.body}`
    );

    check(response, {
        "purchase cancel returns expected result": (r) =>
            r.status === 200 ||
            (
                r.status === 409 &&
                [
                    "PAYMENT_006",
                    "PURCHASE_004"
                ].includes(code)
            )
    });

    if (
        response.status !== 200 &&
        response.status !== 409
    ) {
        console.error(
            `Unexpected purchase cancel response: status=${response.status}, code=${code}, body=${response.body}`
        );
    }
}