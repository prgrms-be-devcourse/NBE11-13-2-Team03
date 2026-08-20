import exec from "k6/execution";
import { actorForUser } from "../lib/data.js";
import { purchase } from "../lib/client.js";
import { commonThresholds, record } from "../lib/metrics.js";
import { verifySale } from "../lib/verify.js";
import { readThresholds, runReadIteration, summaryTrendStats } from "../lib/workload.js";

export const options = {
  scenarios: {
    background_reads: {
      executor: "constant-arrival-rate",
      exec: "backgroundReads",
      rate: Number(__ENV.READ_RATE || 50),
      timeUnit: "1s",
      duration: __ENV.READ_DURATION || "2m",
      preAllocatedVUs: Number(__ENV.READ_PRE_ALLOCATED_VUS || 50),
      maxVUs: Number(__ENV.READ_MAX_VUS || 300)
    },
    purchase_peak: {
      executor: "per-vu-iterations",
      exec: "purchasePeak",
      startTime: __ENV.PURCHASE_START || "30s",
      vus: 1000,
      iterations: 1,
      maxDuration: __ENV.PURCHASE_MAX_DURATION || "3m"
    }
  },
  thresholds: {
    ...readThresholds({
      errorRate: Number(__ENV.ERROR_RATE || 0.01),
      listP95: Number(__ENV.LIST_P95_MS || 1500),
      detailP95: Number(__ENV.DETAIL_P95_MS || 750),
      purchasesP95: Number(__ENV.PURCHASE_LIST_P95_MS || 1000)
    }),
    ...commonThresholds(1000, 0, Number(__ENV.PURCHASE_P95_MS || 5000))
  },
  summaryTrendStats,
  tags: { test_scenario: "mixed-purchase-peak" }
};

export function backgroundReads() {
  runReadIteration(exec.scenario.iterationInTest);
}

export function purchasePeak() {
  const userId = 1 + Number(exec.scenario.iterationInTest);
  record(purchase(2, actorForUser(userId)));
}

export function teardown() {
  verifySale(actorForUser(1), 2, 0, "SOLD_OUT");
}
