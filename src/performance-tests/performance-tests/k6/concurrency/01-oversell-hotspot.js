import exec from "k6/execution";
import { actorForUser } from "../lib/data.js";
import { purchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 3000);

export const options = {
  scenarios: {
    oversell_hotspot: {
      executor: "per-vu-iterations",
      vus: 1000,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || "2m"
    }
  },
  thresholds: {
    ...commonThresholds(100, 900, P95_MS),
    checks: ["rate==1"]
  },
  summaryTrendStats: ["min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  tags: { test_scenario: "oversell-hotspot" }
};

export default function () {
  const userId = Number(exec.vu.idInTest);
  record(purchase(1, actorForUser(userId)), ["SALE_002", "SALE_004"]);
}

export function teardown() {
  verifySale(actorForUser(1), 1, 0, "SOLD_OUT");
}
