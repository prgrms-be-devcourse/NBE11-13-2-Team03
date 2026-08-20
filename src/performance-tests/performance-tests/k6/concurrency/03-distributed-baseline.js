import exec from "k6/execution";
import { actorForUser } from "../lib/data.js";
import { purchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifySale } from "../lib/verify.js";

const P95_MS = Number(__ENV.P95_MS || 1500);

export const options = {
  scenarios: {
    distributed_baseline: {
      executor: "per-vu-iterations",
      vus: 1000,
      iterations: 1,
      maxDuration: __ENV.MAX_DURATION || "2m"
    }
  },
  thresholds: {
    ...commonThresholds(1000, 0, P95_MS),
    checks: ["rate==1"]
  },
  summaryTrendStats: ["min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  tags: { test_scenario: "distributed-baseline" }
};

export default function () {
  const userId = Number(exec.vu.idInTest);
  const saleId = 3 + ((userId - 1) % 100);
  record(purchase(saleId, actorForUser(userId)));
}

export function teardown() {
  const verifier = actorForUser(1);
  for (let saleId = 3; saleId <= 102; saleId += 1) {
    verifySale(verifier, saleId, 0, "SOLD_OUT");
  }
}
