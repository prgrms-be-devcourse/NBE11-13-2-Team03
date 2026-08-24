import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";

const root = resolve(process.argv[2] || ".");

function findMetadata(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) result.push(...findMetadata(path));
    else if (entry.name === "case.json") result.push(path);
  }
  return result;
}

function metric(metrics, name, field, fallback = 0) {
  const data = metrics?.[name];
  const raw = data?.values?.[field] ?? data?.[field];
  return raw === undefined || raw === null ? fallback : Number(raw);
}

function fixed(value, digits = 3) {
  return Number.isFinite(value) ? Number(value.toFixed(digits)) : 0;
}

const rows = findMetadata(root).map((metadataPath) => {
  const metadata = JSON.parse(readFileSync(metadataPath, "utf8"));
  const summaryPath = join(resolve(metadataPath, ".."), "summary.json");
  const summary = existsSync(summaryPath)
    ? JSON.parse(readFileSync(summaryPath, "utf8"))
    : { metrics: {} };
  const metrics = summary.metrics || {};
  const requests = metric(metrics, "experiment_requests", "count");
  const successes = metric(metrics, "experiment_successes", "count");
  const failures = metric(metrics, "experiment_failures", "count", Math.max(0, requests - successes));
  const arrivals = metric(metrics, "experiment_server_arrivals", "count");

  return {
    experiment_group: metadata.experiment_group,
    case_id: metadata.case_id,
    repeat: metadata.repeat,
    status: metadata.status,
    tomcat_max_threads: metadata.tomcat_max_threads,
    tomcat_max_connections: metadata.tomcat_max_connections,
    tomcat_accept_count: metadata.tomcat_accept_count,
    hikari_max_pool_size: metadata.hikari_max_pool_size,
    hikari_connection_timeout_ms: metadata.hikari_connection_timeout_ms,
    vus: metadata.vus,
    load_model: metadata.load_model,
    request_mode: metadata.request_mode,
    inventory_mode: metadata.inventory_mode,
    k6_environment: metadata.k6_environment,
    application_environment: metadata.application_environment,
    requests,
    successes,
    failures,
    connection_refused: metric(metrics, "experiment_connection_refused", "count"),
    dial_timeouts: metric(metrics, "experiment_dial_timeouts", "count"),
    connection_resets: metric(metrics, "experiment_connection_resets", "count"),
    other_transport_errors: metric(metrics, "experiment_other_transport_errors", "count"),
    server_arrivals: arrivals,
    server_completions: metric(metrics, "experiment_server_completions", "count"),
    server_missing: Math.max(0, requests - arrivals),
    response_avg_ms: fixed(metric(metrics, "experiment_request_duration", "avg")),
    response_p95_ms: fixed(metric(metrics, "experiment_request_duration", "p(95)")),
    response_p99_ms: fixed(metric(metrics, "experiment_request_duration", "p(99)")),
    http_req_failed_rate: fixed(metric(metrics, "http_req_failed", "value"), 6),
    http_req_duration_p95_ms: fixed(metric(metrics, "http_req_duration", "p(95)")),
    http_req_blocked_p95_ms: fixed(metric(metrics, "http_req_blocked", "p(95)")),
    http_req_connecting_p95_ms: fixed(metric(metrics, "http_req_connecting", "p(95)")),
    http_req_waiting_p95_ms: fixed(metric(metrics, "http_req_waiting", "p(95)")),
    dropped_iterations: metric(metrics, "dropped_iterations", "count")
  };
}).sort((left, right) =>
  left.experiment_group.localeCompare(right.experiment_group) ||
  left.case_id.localeCompare(right.case_id) ||
  Number(left.repeat) - Number(right.repeat)
);

function csv(records) {
  const columns = Object.keys(records[0] || { case_id: "" });
  const escape = (value) => `"${String(value ?? "").replaceAll('"', '""')}"`;
  return [
    columns.join(","),
    ...records.map((record) => columns.map((column) => escape(record[column])).join(","))
  ].join("\n") + "\n";
}

const groups = new Map();
for (const row of rows) {
  if (!groups.has(row.case_id)) groups.set(row.case_id, []);
  groups.get(row.case_id).push(row);
}

const aggregates = [...groups.values()].map((caseRows) => {
  const first = caseRows[0];
  const sum = (field) => caseRows.reduce((total, row) => total + Number(row[field] || 0), 0);
  const mean = (field) => fixed(sum(field) / Math.max(1, caseRows.length));
  return {
    experiment_group: first.experiment_group,
    case_id: first.case_id,
    completed_repeats: caseRows.filter((row) => row.status === "completed").length,
    total_repeats: caseRows.length,
    requests: sum("requests"),
    successes: sum("successes"),
    failures: sum("failures"),
    connection_refused: sum("connection_refused"),
    connection_refused_rate: fixed(sum("connection_refused") / Math.max(1, sum("requests")), 6),
    server_missing: sum("server_missing"),
    mean_response_p95_ms: mean("response_p95_ms"),
    mean_http_req_failed_rate: mean("http_req_failed_rate"),
    mean_blocked_p95_ms: mean("http_req_blocked_p95_ms"),
    mean_connecting_p95_ms: mean("http_req_connecting_p95_ms"),
    mean_waiting_p95_ms: mean("http_req_waiting_p95_ms")
  };
}).sort((left, right) =>
  left.experiment_group.localeCompare(right.experiment_group) || left.case_id.localeCompare(right.case_id)
);

writeFileSync(join(root, "comparison.csv"), csv(rows), "utf8");
writeFileSync(join(root, "comparison-aggregate.csv"), csv(aggregates), "utf8");

console.table(aggregates.map((row) => ({
  group: row.experiment_group,
  case: row.case_id,
  repeats: `${row.completed_repeats}/${row.total_repeats}`,
  requests: row.requests,
  failed: row.failures,
  refused: row.connection_refused,
  refused_rate: row.connection_refused_rate,
  p95_ms: row.mean_response_p95_ms
})));
console.log(`Created ${join(root, "comparison.csv")}`);
console.log(`Created ${join(root, "comparison-aggregate.csv")}`);
