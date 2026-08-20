import exec from "k6/execution";
import { runReadIteration, summaryTrendStats } from "../lib/workload.js";

export const options = {
  scenarios: {
    warmup: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 10),
      timeUnit: "1s",
      duration: __ENV.DURATION || "1m",
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100)
    }
  },
  summaryTrendStats,
  tags: { test_scenario: "load-warmup" }
};

export default function () {
  runReadIteration(exec.scenario.iterationInTest);
}
