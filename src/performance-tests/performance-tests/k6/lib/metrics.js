import { Counter, Trend } from "k6/metrics";

export const successes = new Counter("business_successes");
export const expectedRejections = new Counter("business_expected_rejections");
export const unexpectedResponses = new Counter("business_unexpected_responses");
export const businessDuration = new Trend("business_request_duration", true);

export function record(response, expectedRejectCodes = []) {
  // Emit zero samples so exact-count thresholds also exist when a category has no events.
  successes.add(0);
  expectedRejections.add(0);
  unexpectedResponses.add(0);
  businessDuration.add(response.timings.duration);

  if (response.status === 200) {
    successes.add(1);
    return "success";
  }

  let code = null;
  try {
    code = response.json("code");
  } catch (_) {
    // Non-JSON errors are classified as unexpected below.
  }

  if (expectedRejectCodes.includes(code)) {
    expectedRejections.add(1);
    return "expected-rejection";
  }

  unexpectedResponses.add(1);
  console.error(`Unexpected response: status=${response.status}, code=${code}, body=${response.body}`);
  return "unexpected";
}

export function commonThresholds(successCount, rejectionCount, p95Milliseconds) {
  return {
    business_successes: [`count==${successCount}`],
    business_expected_rejections: [`count==${rejectionCount}`],
    business_unexpected_responses: ["count==0"],
    business_request_duration: [`p(95)<${p95Milliseconds}`]
  };
}
