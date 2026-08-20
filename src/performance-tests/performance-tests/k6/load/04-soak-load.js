import exec from "k6/execution";
import { readThresholds, runReadIteration, summaryTrendStats } from "../lib/workload.js";

export const options = {
  scenarios: {
    soak_load: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 100),
      timeUnit: "1s",
      duration: __ENV.DURATION || "30m",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 500)
    }
  },
  thresholds: readThresholds({
    errorRate: Number(__ENV.ERROR_RATE || 0.01),
    listP95: Number(__ENV.LIST_P95_MS || 1500),
    detailP95: Number(__ENV.DETAIL_P95_MS || 750),
    purchasesP95: Number(__ENV.PURCHASE_LIST_P95_MS || 1000)
  }),
  summaryTrendStats,
  tags: { test_scenario: "soak-load" }
};

export default function () {
  runReadIteration(exec.scenario.iterationInTest);
}
