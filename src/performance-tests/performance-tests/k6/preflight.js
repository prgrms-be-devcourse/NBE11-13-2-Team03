import { check, sleep } from "k6";
import { actorForUser } from "./lib/data.js";
import { getPurchase, getSale, getSales, jsonBody } from "./lib/client.js";

const WAIT_SECONDS = Number(__ENV.PREFLIGHT_WAIT_SECONDS || 90);
const POLL_SECONDS = Number(__ENV.PREFLIGHT_POLL_SECONDS || 2);

if (WAIT_SECONDS < 0 || POLL_SECONDS <= 0) {
  throw new Error("PREFLIGHT_WAIT_SECONDS must be non-negative and PREFLIGHT_POLL_SECONDS must be positive.");
}

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ["rate==1"],
    http_req_failed: ["rate==0"]
  },
  tags: { test_scenario: "preflight" }
};

const expectedSales = [
  { id: 1, stock: 100 },
  { id: 2, stock: 1000 },
  ...Array.from({ length: 100 }, (_, index) => ({ id: index + 3, stock: 10 })),
  { id: 103, stock: 100 },
  { id: 104, stock: 99 },
  { id: 105, stock: 99 },
  { id: 106, stock: 99 }
];

function statusSummary(sales) {
  return sales.reduce((counts, sale) => {
    const status = sale?.status || "UNKNOWN";
    counts[status] = (counts[status] || 0) + 1;
    return counts;
  }, {});
}

function waitUntilAllSalesAreOnSale(actor) {
  const deadline = Date.now() + (WAIT_SECONDS * 1000);
  let lastSummary = "";

  while (true) {
    const response = getSales(actor);
    const sales = jsonBody(response);
    const validSales = Array.isArray(sales) ? sales : [];

    if (response.status === 0) {
      throw new Error(
        `Preflight cannot connect to the application at ${__ENV.BASE_URL || "http://localhost:8080"}: ${response.error || "transport error"}`
      );
    }

    const allOnSale =
      response.status === 200 &&
      validSales.length === expectedSales.length &&
      validSales.every((sale) => sale?.status === "ON_SALE");

    if (allOnSale) {
      console.log(`Preflight scheduler wait completed: ON_SALE=${validSales.length}`);
      return;
    }

    const summary = JSON.stringify(statusSummary(validSales));
    if (summary !== lastSummary) {
      console.log(`Waiting for sale scheduler: ${summary}`);
      lastSummary = summary;
    }

    if (Date.now() >= deadline) {
      console.error(
        `Sale scheduler did not move all fixtures to ON_SALE within ${WAIT_SECONDS}s. last=${summary}`
      );
      return;
    }
    sleep(POLL_SECONDS);
  }
}

export default function () {
  const actor = actorForUser(1002);
  waitUntilAllSalesAreOnSale(actor);

  for (const expected of expectedSales) {
    const response = getSale(expected.id, actor);
    const sale = jsonBody(response);
    check(response, {
      [`sale ${expected.id}: exists and token is accepted`]: (r) => r.status === 200,
      [`sale ${expected.id}: initial remaining stock is ${expected.stock}`]: () =>
        sale?.remainingStock === expected.stock,
      [`sale ${expected.id}: initially ON_SALE`]: () => sale?.status === "ON_SALE"
    });
  }

  const purchaseResponse = getPurchase(1, actor);
  const purchase = jsonBody(purchaseResponse);
  check(purchaseResponse, {
    "cancel fixture purchase exists": (r) => r.status === 200,
    "cancel fixture purchase is PENDING_PAYMENT": () => purchase?.status === "PENDING_PAYMENT",
    "cancel fixture belongs to sale 104": () => purchase?.saleId === 104
  });
}
