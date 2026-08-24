import { check, sleep } from "k6";
import exec from "k6/execution";
import http from "k6/http";
import { Counter, Trend } from "k6/metrics";
import { actorForUser } from "../lib/data.js";
import { BASE_URL } from "../lib/client.js";

const VUS = Number(__ENV.VUS || 100);
const LOAD_MODEL = __ENV.LOAD_MODEL || "burst";
const REQUEST_MODE = __ENV.REQUEST_MODE || "read";
const RUN_ID = __ENV.RUN_ID || `bottleneck-${Date.now()}`;
const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || "30s";
const MAX_DURATION = __ENV.MAX_DURATION || "3m";
const SPREAD_DURATION_SECONDS = Number(__ENV.SPREAD_DURATION_SECONDS || 10);
const RAMP_UP = __ENV.RAMP_UP || "10s";
const HOLD = __ENV.HOLD || "20s";
const RAMP_DOWN = __ENV.RAMP_DOWN || "10s";
const START_RATE = Number(__ENV.START_RATE || 10);
const TARGET_RATE = Number(__ENV.TARGET_RATE || 500);
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.min(VUS, 100));
const MAX_VUS = Number(__ENV.MAX_VUS || VUS);
const SERVER_SETTLE_SECONDS = Number(__ENV.SERVER_SETTLE_SECONDS || 10);
const NO_CONNECTION_REUSE = String(__ENV.NO_CONNECTION_REUSE || "true").toLowerCase() === "true";

const LOAD_MODELS = ["burst", "linear-spread", "ramping-vus", "ramping-arrival-rate"];
const REQUEST_MODES = ["read", "single-row-purchase", "distributed-purchase"];

if (!Number.isInteger(VUS) || VUS < 1 || VUS > 1000) {
  throw new Error(`VUS must be an integer from 1 to 1000. actual=${VUS}`);
}
if (!LOAD_MODELS.includes(LOAD_MODEL)) {
  throw new Error(`LOAD_MODEL must be one of ${LOAD_MODELS.join(", ")}. actual=${LOAD_MODEL}`);
}
if (!REQUEST_MODES.includes(REQUEST_MODE)) {
  throw new Error(`REQUEST_MODE must be one of ${REQUEST_MODES.join(", ")}. actual=${REQUEST_MODE}`);
}
if (REQUEST_MODE !== "read" && ["ramping-vus", "ramping-arrival-rate"].includes(LOAD_MODEL)) {
  throw new Error(`${LOAD_MODEL} can only use REQUEST_MODE=read because purchase actors must remain unique.`);
}
if (SPREAD_DURATION_SECONDS < 0 || START_RATE < 1 || TARGET_RATE < 1) {
  throw new Error("Spread duration and arrival rates must be positive.");
}

function scenario() {
  if (LOAD_MODEL === "ramping-vus") {
    return {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { target: VUS, duration: RAMP_UP },
        { target: VUS, duration: HOLD },
        { target: 0, duration: RAMP_DOWN }
      ],
      gracefulRampDown: "5s"
    };
  }

  if (LOAD_MODEL === "ramping-arrival-rate") {
    return {
      executor: "ramping-arrival-rate",
      startRate: START_RATE,
      timeUnit: "1s",
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: [
        { target: TARGET_RATE, duration: RAMP_UP },
        { target: TARGET_RATE, duration: HOLD },
        { target: 0, duration: RAMP_DOWN }
      ],
      gracefulStop: "5s"
    };
  }

  return {
    executor: "per-vu-iterations",
    vus: VUS,
    iterations: 1,
    maxDuration: MAX_DURATION,
    gracefulStop: "5s"
  };
}

export const probeRequests = new Counter("experiment_requests");
export const probeSuccesses = new Counter("experiment_successes");
export const probeFailures = new Counter("experiment_failures");
export const probeStatusZero = new Counter("experiment_status_zero");
export const connectionRefused = new Counter("experiment_connection_refused");
export const dialTimeouts = new Counter("experiment_dial_timeouts");
export const connectionResets = new Counter("experiment_connection_resets");
export const otherTransportErrors = new Counter("experiment_other_transport_errors");
export const serverArrivals = new Counter("experiment_server_arrivals");
export const serverCompletions = new Counter("experiment_server_completions");
export const serverActiveAtSnapshot = new Counter("experiment_server_active_at_snapshot");
export const requestDuration = new Trend("experiment_request_duration", true);
export const requestBlocked = new Trend("experiment_request_blocked", true);
export const requestConnecting = new Trend("experiment_request_connecting", true);
export const requestWaiting = new Trend("experiment_request_waiting", true);

export const options = {
  noConnectionReuse: NO_CONNECTION_REUSE,
  scenarios: { bottleneck_probe: scenario() },
  summaryTrendStats: ["count", "min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  tags: {
    test_scenario: "bottleneck-experiment",
    load_model: LOAD_MODEL,
    request_mode: REQUEST_MODE
  }
};

function controlParams(actor) {
  return {
    headers: {
      Accept: "application/json",
      Cookie: `access_token=${actor.accessToken}`
    },
    timeout: HTTP_TIMEOUT,
    tags: { name: "performance experiment control" }
  };
}

function classifyTransportError(response) {
  const errorCode = Number(response.error_code || 0);
  const message = String(response.error || "").toLowerCase();

  // k6: 1212=dial connection refused, 1211=dial timeout, 1220=reset by peer.
  if (errorCode === 1212 || message.includes("connection refused") || message.includes("actively refused")) {
    connectionRefused.add(1);
  } else if (errorCode === 1211 || message.includes("dial timeout") || message.includes("i/o timeout")) {
    dialTimeouts.add(1);
  } else if (errorCode === 1220 || message.includes("reset by peer")) {
    connectionResets.add(1);
  } else {
    otherTransportErrors.add(1);
  }
}

export function setup() {
  const actor = actorForUser(1);
  const response = http.post(
    `${BASE_URL}/api/internal/performance-probe/${RUN_ID}/reset`,
    null,
    controlParams(actor)
  );

  if (response.status !== 204) {
    throw new Error(`Performance probe reset failed: status=${response.status}, error=${response.error || ""}`);
  }
  return { runId: RUN_ID };
}

export default function (data) {
  const iteration = Number(exec.scenario.iterationInTest);
  const vuId = Number(exec.vu.idInTest);
  const actorId = 1 + (iteration % 1000);

  if (LOAD_MODEL === "linear-spread" && VUS > 1 && SPREAD_DURATION_SECONDS > 0) {
    sleep(((vuId - 1) / (VUS - 1)) * SPREAD_DURATION_SECONDS);
  }

  const actor = actorForUser(actorId);
  const requestId = `${data.runId}-${vuId}-${iteration}`;
  const headers = {
    Accept: "application/json",
    Cookie: `access_token=${actor.accessToken}`,
    "X-Performance-Test-Run-Id": data.runId,
    "X-Performance-Test-Request-Id": requestId
  };
  const params = {
    headers,
    timeout: HTTP_TIMEOUT,
    tags: { name: `experiment ${REQUEST_MODE}` }
  };

  let response;
  if (REQUEST_MODE === "single-row-purchase") {
    response = http.post(`${BASE_URL}/api/sales/2/purchases`, null, params);
  } else if (REQUEST_MODE === "distributed-purchase") {
    const saleId = 3 + (iteration % 100);
    response = http.post(`${BASE_URL}/api/sales/${saleId}/purchases`, null, params);
  } else {
    const saleId = 3 + (iteration % 100);
    response = http.get(`${BASE_URL}/api/sales/${saleId}`, params);
  }

  probeRequests.add(1);
  requestDuration.add(response.timings.duration);
  requestBlocked.add(response.timings.blocked);
  requestConnecting.add(response.timings.connecting);
  requestWaiting.add(response.timings.waiting);

  const successful = response.status === 200;
  if (successful) {
    probeSuccesses.add(1);
  } else {
    probeFailures.add(1);
  }

  if (response.status === 0) {
    probeStatusZero.add(1);
    classifyTransportError(response);
  }

  check(response, { "experiment request returned 200": () => successful });
}

export function teardown(data) {
  const actor = actorForUser(1);
  const deadline = Date.now() + (SERVER_SETTLE_SECONDS * 1000);
  let previousArrivals = -1;
  let previousCompletions = -1;
  let snapshot = null;

  do {
    const response = http.get(
      `${BASE_URL}/api/internal/performance-probe/${data.runId}`,
      controlParams(actor)
    );
    if (response.status !== 200) {
      throw new Error(`Performance probe snapshot failed: status=${response.status}, error=${response.error || ""}`);
    }

    snapshot = response.json();
    const arrivals = Number(snapshot?.uniqueRequests || 0);
    const completions = Number(snapshot?.completedRequests || 0);
    const active = Number(snapshot?.activeRequests || 0);
    if (active === 0 && arrivals === previousArrivals && completions === previousCompletions) {
      break;
    }
    previousArrivals = arrivals;
    previousCompletions = completions;
    if (Date.now() < deadline) sleep(1);
  } while (Date.now() < deadline);

  serverArrivals.add(Number(snapshot?.uniqueRequests || 0));
  serverCompletions.add(Number(snapshot?.completedRequests || 0));
  serverActiveAtSnapshot.add(Number(snapshot?.activeRequests || 0));

  http.del(
    `${BASE_URL}/api/internal/performance-probe/${data.runId}`,
    null,
    controlParams(actor)
  );
}
