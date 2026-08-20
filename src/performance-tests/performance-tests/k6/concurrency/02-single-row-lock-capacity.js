import exec from "k6/execution";
import { actorForUser } from "../lib/data.js";
import { purchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 5000);

export const options = {
  scenarios: {
    single_row_lock_capacity: {
      executor: "per-vu-iterations",
      vus: 1000,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || "3m"
    }
  },
  thresholds: {
    ...commonThresholds(1000, 0, P95_MS),
    checks: ["rate==1"]
  },
  summaryTrendStats: ["min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  tags: { test_scenario: "single-row-lock-capacity" }
};

export default function () {
  const userId = Number(exec.vu.idInTest);
  record(purchase(2, actorForUser(userId)));
}

export function teardown() {
  verifySale(actorForUser(1), 2, 0, "SOLD_OUT");
}
