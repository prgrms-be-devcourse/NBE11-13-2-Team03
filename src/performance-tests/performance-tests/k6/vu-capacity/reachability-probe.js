import { check, sleep } from "k6";
import exec from "k6/execution";
import http from "k6/http";
import { Counter, Trend } from "k6/metrics";
import { actorForUser } from "../lib/data.js";
import { BASE_URL, jsonBody } from "../lib/client.js";

const VUS = Number(__ENV.VUS || 100);
const RUN_ID = __ENV.RUN_ID || `vu-probe-${VUS}`;
const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || "30s";
const MAX_DURATION = __ENV.MAX_DURATION || "2m";
const SERVER_SETTLE_SECONDS = Number(__ENV.SERVER_SETTLE_SECONDS || 5);
const PROBE_MODE = __ENV.PROBE_MODE || "single-row-purchase";
const ALLOWED_PROBE_MODES = ["read", "distributed-purchase", "single-row-purchase"];

if (!Number.isInteger(VUS) || VUS <= 0 || VUS > 1000) {
  throw new Error(`VUS must be an integer from 1 to 1000. actual=${VUS}`);
}
if (!ALLOWED_PROBE_MODES.includes(PROBE_MODE)) {
  throw new Error(`PROBE_MODE must be one of ${ALLOWED_PROBE_MODES.join(", ")}. actual=${PROBE_MODE}`);
}

export const probeAttempts = new Counter("probe_attempts");
export const probeHttpResponses = new Counter("probe_http_responses");
export const probeSuccessfulResponses = new Counter("probe_successful_responses");
export const probeResponseMarkedReached = new Counter("probe_response_marked_reached");
export const probeClientTimeoutErrors = new Counter("probe_client_timeout_errors");
export const probeClientConnectionErrors = new Counter("probe_client_connection_errors");
export const probeClientDnsErrors = new Counter("probe_client_dns_errors");
export const probeClientTlsErrors = new Counter("probe_client_tls_errors");
export const probeClientOtherTransportErrors = new Counter("probe_client_other_transport_errors");
export const probeHttp4xx = new Counter("probe_http_4xx");
export const probeHttp5xx = new Counter("probe_http_5xx");
export const probeServerArrivals = new Counter("probe_server_arrivals");
export const probeServerCompletions = new Counter("probe_server_completions");
export const probeServerActiveAtSnapshot = new Counter("probe_server_active_at_snapshot");
export const probeServerMissing = new Counter("probe_server_missing");
export const probeServerDuplicates = new Counter("probe_server_duplicates");
export const probeDuration = new Trend("probe_duration", true);
export const probeBlocked = new Trend("probe_blocked", true);
export const probeConnecting = new Trend("probe_connecting", true);
export const probeWaiting = new Trend("probe_waiting", true);

export const options = {
  noConnectionReuse: true,
  scenarios: {
    reachability_probe: {
      executor: "per-vu-iterations",
      vus: VUS,
      iterations: 1,
      maxDuration: MAX_DURATION,
      gracefulStop: "5s"
    }
  },
  thresholds: {
    probe_attempts: [`count==${VUS}`]
  },
  summaryTrendStats: ["count", "min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
  tags: {
    test_scenario: "vu-reachability",
    probe_vus: String(VUS),
    probe_mode: PROBE_MODE
  }
};

function controlParams(actor) {
  return {
    headers: {
      Accept: "application/json",
      Cookie: `access_token=${actor.accessToken}`
    },
    timeout: HTTP_TIMEOUT,
    tags: { name: "performance probe control" }
  };
}

function firstHeader(response, name) {
  const value = response.headers?.[name];
  return Array.isArray(value) ? value[0] : value;
}

function recordTransportError(response) {
  const message = String(response.error || "").toLowerCase();

  if (message.includes("timeout") || message.includes("deadline exceeded")) {
    probeClientTimeoutErrors.add(1);
  } else if (message.includes("lookup") || message.includes("dns")) {
    probeClientDnsErrors.add(1);
  } else if (message.includes("tls") || message.includes("certificate")) {
    probeClientTlsErrors.add(1);
  } else if (
    message.includes("connect") ||
    message.includes("dial") ||
    message.includes("refused") ||
    message.includes("reset") ||
    message.includes("eof")
  ) {
    probeClientConnectionErrors.add(1);
  } else {
    probeClientOtherTransportErrors.add(1);
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
    throw new Error(
      `Performance probe reset failed: status=${response.status}, error=${response.error || ""}. ` +
      "Run the Spring application with the performance profile and install the probe server code."
    );
  }

  return { runId: RUN_ID };
}

export default function (data) {
  const vuId = Number(exec.vu.idInTest);
  const actor = actorForUser(vuId);
  const saleId = PROBE_MODE === "single-row-purchase"
    ? 2
    : 3 + ((vuId - 1) % 100);
  const requestId = `${data.runId}-${vuId}`;

  probeAttempts.add(1);
  const requestParams = {
    headers: {
      Accept: "application/json",
      Cookie: `access_token=${actor.accessToken}`,
      "X-Performance-Test-Run-Id": data.runId,
      "X-Performance-Test-Request-Id": requestId
    },
    timeout: HTTP_TIMEOUT,
    tags: {
      name: PROBE_MODE === "read"
        ? "GET /api/sales/:saleId [VU probe]"
        : "POST /api/sales/:saleId/purchases [VU probe]"
    }
  };
  const response = PROBE_MODE === "read"
    ? http.get(`${BASE_URL}/api/sales/${saleId}`, requestParams)
    : http.post(`${BASE_URL}/api/sales/${saleId}/purchases`, null, requestParams);

  probeDuration.add(response.timings.duration);
  probeBlocked.add(response.timings.blocked);
  probeConnecting.add(response.timings.connecting);
  probeWaiting.add(response.timings.waiting);

  if (response.status === 0) {
    recordTransportError(response);
    return;
  }

  probeHttpResponses.add(1);
  if (String(firstHeader(response, "X-Performance-Test-Reached")).toLowerCase() === "true") {
    probeResponseMarkedReached.add(1);
  }

  if (response.status >= 400 && response.status < 500) {
    probeHttp4xx.add(1);
  } else if (response.status >= 500) {
    probeHttp5xx.add(1);
  }

  const body = jsonBody(response);
  const validBody = PROBE_MODE === "read"
    ? body?.id === saleId
    : body?.saleId === saleId;
  const passed = response.status === 200 && validBody;
  if (passed) {
    probeSuccessfulResponses.add(1);
  }
  check(response, { "VU probe: valid application response": () => passed });
}

export function teardown(data) {
  const actor = actorForUser(1);
  const deadline = Date.now() + (SERVER_SETTLE_SECONDS * 1000);
  let previousArrivals = -1;
  let previousCompletions = -1;
  let stableSamples = 0;
  let snapshot = null;

  do {
    const snapshotResponse = http.get(
      `${BASE_URL}/api/internal/performance-probe/${data.runId}`,
      controlParams(actor)
    );

    if (snapshotResponse.status !== 200) {
      throw new Error(
        `Performance probe snapshot failed: status=${snapshotResponse.status}, error=${snapshotResponse.error || ""}`
      );
    }

    snapshot = jsonBody(snapshotResponse);
    const currentArrivals = Number(snapshot?.uniqueRequests || 0);
    const currentCompletions = Number(snapshot?.completedRequests || 0);
    const currentActive = Number(snapshot?.activeRequests || 0);
    const unchanged =
      currentArrivals === previousArrivals && currentCompletions === previousCompletions;
    stableSamples = currentActive === 0 && unchanged ? stableSamples + 1 : 0;
    previousArrivals = currentArrivals;
    previousCompletions = currentCompletions;

    if (stableSamples >= 1 || Date.now() >= deadline) {
      break;
    }
    sleep(1);
  } while (true);

  const arrivals = Number(snapshot?.uniqueRequests || 0);
  const completions = Number(snapshot?.completedRequests || 0);
  const active = Number(snapshot?.activeRequests || 0);
  const duplicates = Number(snapshot?.duplicateRequests || 0);
  probeServerArrivals.add(arrivals);
  probeServerCompletions.add(completions);
  probeServerActiveAtSnapshot.add(active);
  probeServerMissing.add(Math.max(0, VUS - arrivals));
  probeServerDuplicates.add(duplicates);

  http.del(
    `${BASE_URL}/api/internal/performance-probe/${data.runId}`,
    null,
    controlParams(actor)
  );
}
