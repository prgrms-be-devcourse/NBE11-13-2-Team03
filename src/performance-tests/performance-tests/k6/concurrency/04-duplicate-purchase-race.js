import { actorForUser } from "../lib/data.js";
import { purchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifyActivePurchaseCount, verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 2000);

export const options = {
  scenarios: {
    duplicate_purchase_race: {
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
  tags: { test_scenario: "duplicate-purchase-race" }
};

export default function () {
  const actor = actorForUser(1001);
  record(purchase(103, actor), ["PURCHASE_002"]);
}

export function teardown() {
  const actor = actorForUser(1001);
  verifySale(actor, 103, 99, "ON_SALE");
  verifyActivePurchaseCount(actor, 103, 1);
}
