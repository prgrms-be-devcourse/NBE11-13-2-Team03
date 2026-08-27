import { actorForUser } from "../lib/data.js";
import { confirmPayment } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifyPurchaseStatus, verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 2000);

export const options = {
    scenarios: {
        payment_confirm_race: {
            executor: "per-vu-iterations",
            vus: 50,
            iterations: 1,
            maxDuration: __ENV.MAX_DURATION || "1m"
        }
    },
    thresholds: {
        ...commonThresholds(1, 49, P95_MS),
        checks: ["rate==1"]
    },
    summaryTrendStats: ["min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
    tags: { test_scenario: "payment-confirm-race" }
};

export default function () {
    const actor = actorForUser(1003);

    record(
        confirmPayment(
            "PERF_PAYMENT_KEY_0002",
            "GUDIT_PERF_PAYMENT_CONFIRM_RACE_0002",
            10105,
            actor
        ),
        ["PAYMENT_006", "PURCHASE_004"]
    );
}

export function teardown() {
    const actor = actorForUser(1003);

    verifyPurchaseStatus(actor, 2, "PURCHASED");
    verifySale(actor, 105, 99, "ON_SALE");
}