import { check } from "k6";
import { actorForUser } from "./lib/data.js";
import { getPurchase, getSale, jsonBody } from "./lib/client.js";

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
  { id: 104, stock: 99 }
];

export default function () {
  const actor = actorForUser(1002);

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
