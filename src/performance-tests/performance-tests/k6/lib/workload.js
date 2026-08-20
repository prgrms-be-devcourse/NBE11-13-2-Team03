import { check } from "k6";
import { Rate, Trend } from "k6/metrics";
import { actorForUser } from "./data.js";
import { getPurchases, getSale, getSales, jsonBody } from "./client.js";

export const loadErrors = new Rate("load_errors");
export const saleListDuration = new Trend("sale_list_duration", true);
export const saleDetailDuration = new Trend("sale_detail_duration", true);
export const purchaseListDuration = new Trend("purchase_list_duration", true);

function record(response, metric, label, bodyPredicate) {
  const body = jsonBody(response);
  const passed = response.status === 200 && bodyPredicate(body);
  metric.add(response.timings.duration);
  loadErrors.add(!passed);
  check(response, { [`${label}: valid 200 response`]: () => passed });
}

export function runReadIteration(iteration) {
  const userId = 1 + (Number(iteration) % 1000);
  const actor = actorForUser(userId);
  const selector = Number(iteration) % 20;

  // 50% sale list, 35% sale detail, 15% authenticated purchase history.
  if (selector < 10) {
    const response = getSales(actor);
    record(response, saleListDuration, "sale list", (body) => Array.isArray(body) && body.length === 104);
    return;
  }

  if (selector < 17) {
    const saleId = 1 + (Number(iteration) % 104);
    const response = getSale(saleId, actor);
    record(response, saleDetailDuration, "sale detail", (body) => body?.id === saleId);
    return;
  }

  const response = getPurchases(actor);
  record(response, purchaseListDuration, "purchase list", (body) => Array.isArray(body?.purchases));
}

export function readThresholds({ errorRate = 0.01, listP95 = 1000, detailP95 = 500, purchasesP95 = 750 } = {}) {
  return {
    load_errors: [`rate<${errorRate}`],
    checks: [`rate>${1 - errorRate}`],
    sale_list_duration: [`p(95)<${listP95}`],
    sale_detail_duration: [`p(95)<${detailP95}`],
    purchase_list_duration: [`p(95)<${purchasesP95}`],
    dropped_iterations: ["count==0"]
  };
}

export const summaryTrendStats = ["count", "min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"];
