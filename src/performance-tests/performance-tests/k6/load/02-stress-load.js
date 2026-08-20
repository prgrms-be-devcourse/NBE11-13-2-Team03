import exec from "k6/execution";
import { readThresholds, runReadIteration, summaryTrendStats } from "../lib/workload.js";

const PEAK_RATE = Number(__ENV.PEAK_RATE || 500);

export const options = {
  scenarios: {
    stress_load: {
      executor: "ramping-arrival-rate",
      startRate: Math.max(1, Math.floor(PEAK_RATE / 20)),
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 150),
      maxVUs: Number(__ENV.MAX_VUS || 1200),
      stages: [
        { target: Math.floor(PEAK_RATE * 0.1), duration: __ENV.STAGE_1 || "1m" },
        { target: Math.floor(PEAK_RATE * 0.25), duration: __ENV.STAGE_2 || "2m" },
        { target: Math.floor(PEAK_RATE * 0.5), duration: __ENV.STAGE_3 || "2m" },
        { target: PEAK_RATE, duration: __ENV.STAGE_4 || "2m" },
        { target: 0, duration: __ENV.RECOVERY || "1m" }
      ]
    }
  },
  thresholds: readThresholds({
    errorRate: Number(__ENV.ERROR_RATE || 0.01),
    listP95: Number(__ENV.LIST_P95_MS || 2000),
    detailP95: Number(__ENV.DETAIL_P95_MS || 1000),
    purchasesP95: Number(__ENV.PURCHASE_LIST_P95_MS || 1500)
  }),
  summaryTrendStats,
  tags: { test_scenario: "stress-load" }
};

export default function () {
  runReadIteration(exec.scenario.iterationInTest);
}
