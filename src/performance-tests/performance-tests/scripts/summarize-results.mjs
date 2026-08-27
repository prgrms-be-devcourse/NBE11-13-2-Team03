import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const resultDirectory = resolve(process.argv[2] || ".");
const files = readdirSync(resultDirectory)
  .filter((name) => name.endsWith(".summary.json"))
  .sort();

function value(metrics, metric, field) {
  return metrics?.[metric]?.values?.[field] ?? "";
}

const rows = files.map((file) => {
  const summary = JSON.parse(readFileSync(resolve(resultDirectory, file), "utf8"));
  const metrics = summary.metrics;
  return {
    scenario: file.replace(".summary.json", ""),
    requests: value(metrics, "http_reqs", "count"),
    request_rate: value(metrics, "http_reqs", "rate"),
    checks_rate: value(metrics, "checks", "rate"),
    load_error_rate: value(metrics, "load_errors", "rate"),
    dropped_iterations: value(metrics, "dropped_iterations", "count"),
    sale_list_p95_ms: value(metrics, "sale_list_duration", "p(95)"),
    sale_detail_p95_ms: value(metrics, "sale_detail_duration", "p(95)"),
    purchase_list_p95_ms: value(metrics, "purchase_list_duration", "p(95)"),
    purchase_p95_ms: value(metrics, "business_request_duration", "p(95)"),
    business_successes: value(metrics, "business_successes", "count"),
    unexpected_responses: value(metrics, "business_unexpected_responses", "count")
  };
});

const columns = Object.keys(rows[0] || { scenario: "" });
const escapeCsv = (value) => `"${String(value).replaceAll('"', '""')}"`;
const csv = [columns.join(","), ...rows.map((row) => columns.map((column) => escapeCsv(row[column])).join(","))].join("\n");
const outputPath = resolve(resultDirectory, "comparison.csv");
writeFileSync(outputPath, `${csv}\n`, "utf8");
console.log(`Created ${outputPath}`);
