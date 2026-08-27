import { actorForUser } from "../lib/data.js";
import { cancelPurchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifyPurchaseStatus, verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 2000);

export const options = {
  scenarios: {
    cancel_race: {
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
  tags: { test_scenario: "cancel-race" }
};

export default function () {
  const actor = actorForUser(1002);
  record(cancelPurchase(1, actor), ["PURCHASE_003"]);
}

export function teardown() {
  const actor = actorForUser(1002);
  verifyPurchaseStatus(actor, 1, "CANCELED");
  verifySale(actor, 104, 100, "ON_SALE");
}
