import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const resultDirectory = resolve(process.argv[2] || ".");
const allowedMissingRate = Number(process.argv[3] || 0);
const probeMode = process.argv[4] || "unknown";
const files = readdirSync(resultDirectory)
  .filter((name) => /^vu-\d+\.summary\.json$/.test(name))
  .sort((left, right) => vuFromName(left) - vuFromName(right));

function vuFromName(name) {
  return Number(name.match(/^vu-(\d+)/)?.[1] || 0);
}

function value(metrics, metric, field, fallback = 0) {
  const metricData = metrics?.[metric];
  return Number(metricData?.values?.[field] ?? metricData?.[field] ?? fallback);
}

function totalTransportErrors(metrics) {
  return [
    "probe_client_timeout_errors",
    "probe_client_connection_errors",
    "probe_client_dns_errors",
    "probe_client_tls_errors",
    "probe_client_other_transport_errors"
  ].reduce((sum, metric) => sum + value(metrics, metric, "count"), 0);
}

function classify(row) {
  if (row.attempts < row.vus) return "load-generator-did-not-start-all";
  if (row.server_arrivals < row.attempts) return "missing-before-app-or-after-settle-window";
  if (row.server_completions < row.server_arrivals) return "reached-app-but-still-processing-at-snapshot";
  if (row.http_responses < row.attempts) return "reached-app-but-client-got-no-response";
  if (row.http_5xx > 0 || row.http_4xx > 0) return "reached-app-and-returned-http-error";
  if (row.successful_responses < row.attempts) return "invalid-application-response";
  return "passed";
}

const rows = files.map((file) => {
  const summary = JSON.parse(readFileSync(resolve(resultDirectory, file), "utf8"));
  const metrics = summary.metrics || {};
  const vus = vuFromName(file);
  const attempts = value(metrics, "probe_attempts", "count");
  const serverArrivals = value(metrics, "probe_server_arrivals", "count");
  const serverMissing = Math.max(0, attempts - serverArrivals);
  const row = {
    vus,
    attempts,
    server_arrivals: serverArrivals,
    server_completions: value(metrics, "probe_server_completions", "count"),
    server_active_at_snapshot: value(metrics, "probe_server_active_at_snapshot", "count"),
    server_missing: serverMissing,
    server_missing_rate: attempts > 0 ? serverMissing / attempts : 1,
    http_responses: value(metrics, "probe_http_responses", "count"),
    successful_responses: value(metrics, "probe_successful_responses", "count"),
    response_marked_reached: value(metrics, "probe_response_marked_reached", "count"),
    transport_errors: totalTransportErrors(metrics),
    timeout_errors: value(metrics, "probe_client_timeout_errors", "count"),
    connection_errors: value(metrics, "probe_client_connection_errors", "count"),
    dns_errors: value(metrics, "probe_client_dns_errors", "count"),
    tls_errors: value(metrics, "probe_client_tls_errors", "count"),
    other_transport_errors: value(metrics, "probe_client_other_transport_errors", "count"),
    http_4xx: value(metrics, "probe_http_4xx", "count"),
    http_5xx: value(metrics, "probe_http_5xx", "count"),
    duration_p95_ms: value(metrics, "probe_duration", "p(95)"),
    duration_p99_ms: value(metrics, "probe_duration", "p(99)"),
    waiting_p95_ms: value(metrics, "probe_waiting", "p(95)"),
    connecting_p95_ms: value(metrics, "probe_connecting", "p(95)"),
    blocked_p95_ms: value(metrics, "probe_blocked", "p(95)")
  };
  row.classification = classify(row);
  row.reachability_passed =
    row.attempts === row.vus && row.server_missing_rate <= allowedMissingRate;
  return row;
});

const columns = Object.keys(rows[0] || { vus: "" });
const escapeCsv = (input) => `"${String(input).replaceAll('"', '""')}"`;
const csv = [
  columns.join(","),
  ...rows.map((row) => columns.map((column) => escapeCsv(row[column])).join(","))
].join("\n");
writeFileSync(resolve(resultDirectory, "vu-capacity-comparison.csv"), `${csv}\n`, "utf8");

const firstFailure = rows.find((row) => !row.reachability_passed) || null;
const passingRowsBeforeFailure = firstFailure
  ? rows.filter((row) => row.vus < firstFailure.vus && row.reachability_passed)
  : rows.filter((row) => row.reachability_passed);
const lastPassing = passingRowsBeforeFailure.at(-1) || null;
const passingAfterFirstFailure = firstFailure
  ? rows.filter((row) => row.vus > firstFailure.vus && row.reachability_passed).map((row) => row.vus)
  : [];
const verdict = {
  probeMode,
  allowedMissingRate,
  testedVuSteps: rows.map((row) => row.vus),
  lastPassingVu: lastPassing?.vus ?? null,
  firstFailingVu: firstFailure?.vus ?? null,
  firstFailureClassification: firstFailure?.classification ?? null,
  passingAfterFirstFailure,
  monotonicBoundary: passingAfterFirstFailure.length === 0,
  note: passingAfterFirstFailure.length > 0
    ? `Results are non-monotonic: VU ${passingAfterFirstFailure.join(", ")} passed after the first failure at ${firstFailure.vus}. Repeat the isolated test before selecting a boundary.`
    : firstFailure
      ? `The observed boundary is between ${lastPassing?.vus ?? 0} and ${firstFailure.vus} VUs. Add intermediate steps to narrow it.`
      : "No reachability failure was observed in the tested VU range."
};
writeFileSync(
  resolve(resultDirectory, "vu-capacity-verdict.json"),
  `${JSON.stringify(verdict, null, 2)}\n`,
  "utf8"
);

console.table(rows.map((row) => ({
  vus: row.vus,
  attempts: row.attempts,
  app: row.server_arrivals,
  complete: row.server_completions,
  active: row.server_active_at_snapshot,
  missing: row.server_missing,
  responses: row.http_responses,
  timeouts: row.timeout_errors,
  p95_ms: Math.round(row.duration_p95_ms),
  result: row.classification
})));
console.log(verdict.note);
console.log(`Created ${resolve(resultDirectory, "vu-capacity-comparison.csv")}`);
console.log(`Created ${resolve(resultDirectory, "vu-capacity-verdict.json")}`);
