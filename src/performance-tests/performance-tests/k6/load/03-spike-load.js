import exec from "k6/execution";
import { readThresholds, runReadIteration, summaryTrendStats } from "../lib/workload.js";

const NORMAL_RATE = Number(__ENV.NORMAL_RATE || 50);
const SPIKE_RATE = Number(__ENV.SPIKE_RATE || 500);

export const options = {
  scenarios: {
    spike_load: {
      executor: "ramping-arrival-rate",
      startRate: NORMAL_RATE,
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 150),
      maxVUs: Number(__ENV.MAX_VUS || 1200),
      stages: [
        { target: NORMAL_RATE, duration: __ENV.NORMAL_DURATION || "1m" },
        { target: SPIKE_RATE, duration: __ENV.SPIKE_RAMP || "10s" },
        { target: SPIKE_RATE, duration: __ENV.SPIKE_HOLD || "1m" },
        { target: NORMAL_RATE, duration: __ENV.RECOVERY_RAMP || "10s" },
        { target: NORMAL_RATE, duration: __ENV.RECOVERY_HOLD || "2m" }
      ]
    }
  },
  thresholds: readThresholds({
    errorRate: Number(__ENV.ERROR_RATE || 0.05),
    listP95: Number(__ENV.LIST_P95_MS || 3000),
    detailP95: Number(__ENV.DETAIL_P95_MS || 2000),
    purchasesP95: Number(__ENV.PURCHASE_LIST_P95_MS || 2500)
  }),
  summaryTrendStats,
  tags: { test_scenario: "spike-load" }
};

export default function () {
  runReadIteration(exec.scenario.iterationInTest);
}
