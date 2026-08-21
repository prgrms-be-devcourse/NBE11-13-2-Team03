import http from "k6/http";

export const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");

function params(actor, name) {
  return {
    headers: {
      Accept: "application/json",
      Cookie: `access_token=${actor.accessToken}`
    },
    tags: { name }
  };
}

export function purchase(saleId, actor) {
  return http.post(
    `${BASE_URL}/api/sales/${saleId}/purchases`,
    null,
    params(actor, "POST /api/sales/:saleId/purchases")
  );
}

export function cancelPurchase(purchaseId, actor) {
  return http.post(
    `${BASE_URL}/api/purchases/${purchaseId}/cancel`,
    null,
    params(actor, "POST /api/purchases/:purchaseId/cancel")
  );
}

export function confirmPayment(paymentKey, orderId, amount, actor) {
  const requestParams = params(
      actor,
      "POST /api/payments/confirm"
  );

  requestParams.headers["Content-Type"] =
      "application/json";

  return http.post(
      `${BASE_URL}/api/payments/confirm`,
      JSON.stringify({
        paymentKey,
        orderId,
        amount
      }),
      requestParams
  );
}

export function getSale(saleId, actor) {
  return http.get(
    `${BASE_URL}/api/sales/${saleId}`,
    params(actor, "GET /api/sales/:saleId")
  );
}

export function getSales(actor) {
  return http.get(
    `${BASE_URL}/api/sales`,
    params(actor, "GET /api/sales")
  );
}

export function getPurchase(purchaseId, actor) {
  return http.get(
    `${BASE_URL}/api/purchases/${purchaseId}`,
    params(actor, "GET /api/purchases/:purchaseId")
  );
}

export function getPurchases(actor) {
  return http.get(
    `${BASE_URL}/api/purchases`,
    params(actor, "GET /api/purchases")
  );
}

export function jsonBody(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}
