import { createHash, createHmac } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const USER_COUNT = 1_004;
const DISTRIBUTED_SALE_COUNT = 100;
const ISSUER = "test@naver.com";

const KST_OFFSET_MILLIS = 9 * 60 * 60 * 1000;
const ONE_MINUTE_MILLIS = 60 * 1000;
const ONE_DAY_MILLIS = 24 * 60 * 60 * 1000;

const FIXTURE_NOW = Date.now();
const CREATED_AT = toKstLocalDateTime(FIXTURE_NOW);
const SALE_START_AT = toKstLocalDateTime(FIXTURE_NOW - 5 * ONE_MINUTE_MILLIS);
const SALE_END_AT = toKstLocalDateTime(FIXTURE_NOW + ONE_DAY_MILLIS);
const JWT_ISSUED_AT = Math.floor(Date.parse("2026-01-01T00:00:00Z") / 1000);
const JWT_EXPIRES_AT = Math.floor(Date.parse("2035-01-01T00:00:00Z") / 1000);

const secretBytes = createHash("sha512")
    .update(
        "gudit-performance-test-only-key-2026"
    )
    .digest();

const jwtSecretKeyBase64 =
    secretBytes.toString("base64");

function base64Url(value) {
  return Buffer.from(value)
      .toString("base64")
      .replaceAll("=", "")
      .replaceAll("+", "-")
      .replaceAll("/", "_");
}

function toKstLocalDateTime(epochMillis) {
  return new Date(epochMillis + KST_OFFSET_MILLIS)
    .toISOString()
    .slice(0, 19);
}

function createAccessToken(userId) {
  const header = base64Url(
      JSON.stringify({
        alg: "HS512",
        typ: "JWT"
      })
  );

  const payload = base64Url(
      JSON.stringify({
        iss: ISSUER,
        iat: JWT_ISSUED_AT,
        exp: JWT_EXPIRES_AT,
        sub: String(userId),
        role: "USER",
        token_type: "ACCESS"
      })
  );

  const signature = createHmac(
      "sha512",
      secretBytes
  )
      .update(`${header}.${payload}`)
      .digest("base64url");

  return `${header}.${payload}.${signature}`;
}

const users = Array.from(
    { length: USER_COUNT },
    (_, index) => {
      const id = index + 1;

      return {
        id,
        kakao_id: 9_000_000_000 + id,
        nickname:
            `perf-user-${String(id).padStart(4, "0")}`,
        email:
            `perf-user-${String(id).padStart(4, "0")}@example.test`,
        role: "USER",
        provider: "KAKAO",
        created_at: CREATED_AT,
        updated_at: CREATED_AT
      };
    }
);

const goods = Array.from(
    { length: 106 },
    (_, index) => {
      const id = index + 1;

      return {
        id,
        name:
            `performance-goods-${String(id).padStart(3, "0")}`,
        description:
            `k6 concurrency fixture ${id}`,
        price: 10_000 + id,
        image_url: null,
        status: "ACTIVE",
        created_at: CREATED_AT,
        updated_at: CREATED_AT
      };
    }
);

function sale(id, stock, label) {
  return {
    id,
    goods_id: id,
    created_by: 1,
    initial_stock: stock,
    remaining_stock: stock,
    max_purchase_quantity: 1,

    // 성능 테스트 실행 시 즉시 구매 가능하도록 ON_SALE로 생성
    status: "ON_SALE",

    start_at: SALE_START_AT,
    end_at: SALE_END_AT,
    created_at: CREATED_AT,
    updated_at: CREATED_AT,
    fixture_label: label
  };
}

const sales = [
  sale(
      1,
      100,
      "oversell-hotspot"
  ),

  sale(
      2,
      1_000,
      "single-row-lock-capacity"
  ),

  ...Array.from(
      {
        length: DISTRIBUTED_SALE_COUNT
      },
      (_, index) =>
          sale(
              index + 3,
              10,
              "distributed-baseline"
          )
  ),

  sale(
      103,
      100,
      "duplicate-purchase-race"
  ),

  sale(
      104,
      100,
      "cancel-race"
  ),

  sale(
      105,
      100,
      "payment-confirm-race"
  ),

  sale(
      106,
      100,
      "payment-confirm-cancel-race"
  )
];

/**
 * 판매 104번에는 미결제 구매 1건이 존재한다.
 * 해당 구매가 재고 1개를 예약했으므로 남은 재고는 99다.
 */
sales.find(
    ({ id }) => id === 104
).remaining_stock = 99;

/**
 * 판매 105번에는 결제 승인 동시성 테스트용 미결제 구매 1건이 존재한다.
 * 해당 구매가 재고 1개를 예약했으므로 남은 재고는 99다.
 */
sales.find(
    ({ id }) => id === 105
).remaining_stock = 99;

/**
 * 판매 106번에는 결제 승인-취소 경쟁 테스트용 미결제 구매 1건이 존재한다.
 * 해당 구매가 재고 1개를 예약했으므로 남은 재고는 99다.
 */
sales.find(
    ({ id }) => id === 106
).remaining_stock = 99;

const actors = users.map(({ id }) => ({
  userId: id,
  accessToken: createAccessToken(id)
}));

const output = {
  metadata: {
    name: "Gudit k6 concurrency performance fixtures",
    generatedAt: `${CREATED_AT}+09:00`,
    targetApi: "POST /api/sales/{saleId}/purchases",
    cancelApi: "POST /api/purchases/{purchaseId}/cancel",
    paymentConfirmApi: "POST /api/payments/confirm",
    maximumConcurrentVus: 1_000,

    requiresEmptyIsolatedDatabase: true,

    warning:
        "The bundled JWT secret and tokens are test-only. Never use them in production.",

    schedulerInitialization: {
      initialStatus: "ON_SALE",
      behavior:
          "Performance fixtures are preloaded in ON_SALE state for immediate test execution."
    },

    requiredEnvironment: {
      PERFORMANCE_JWT_SECRET_KEY:
      jwtSecretKeyBase64,
      JWT_ISSUER:
      ISSUER
    },

    activePeriod: {
      startAt: SALE_START_AT,
      endAt: SALE_END_AT
    },

    importOrder: [
      "users",
      "goods",
      "sales",
      "purchases",
      "payments"
    ],

    sequenceResetAfterImport: {
      users: USER_COUNT,
      goods: 106,
      goods_sales: 106,
      purchases: 3,
      payments: 3
    }
  },

  volumePlan: {
    users: {
      count: USER_COUNT,
      reason:
          "1,000 unique VUs plus one duplicate-purchase actor, one cancellation actor, one payment-confirm actor, and one payment-confirm-cancel actor"
    },

    goods: {
      count: 106,
      reason:
          "one goods row per sale fixture"
    },

    sales: {
      count: 106,
      reason:
          "two hotspot fixtures, 100 distributed fixtures, and three race-condition fixtures"
    },

    purchases: {
      count: 3,
      reason:
          "pending purchases used by the concurrent cancellation and payment-confirm tests"
    },

    payments: {
      count: 3,
      reason:
          "READY payments paired with the cancellation and payment-confirm test purchases"
    },

    accessTokens: {
      count: USER_COUNT,
      reason:
          "authenticated k6 requests without invoking Kakao OAuth during the measured run"
    }
  },

  scenarios: {
    oversellHotspot: {
      description:
          "1,000 unique users compete for 100 units on one locked sale row.",

      saleIds: [1],

      actorUserIds: {
        from: 1,
        to: 1_000
      },

      requestsPerActor: 1,

      expectedInvariant: {
        successfulPurchases: 100,
        rejectedPurchases: 900,

        allowedRejectCodes: [
          "SALE_002",
          "SALE_004"
        ],

        finalRemainingStock: 0,
        finalSaleStatus: "SOLD_OUT"
      }
    },

    singleRowLockCapacity: {
      description:
          "1,000 unique users compete on one sale with sufficient stock to expose lock queue latency.",

      saleIds: [2],

      actorUserIds: {
        from: 1,
        to: 1_000
      },

      requestsPerActor: 1,

      expectedInvariant: {
        successfulPurchases: 1_000,
        rejectedPurchases: 0,
        finalRemainingStock: 0,
        finalSaleStatus: "SOLD_OUT"
      }
    },

    distributedBaseline: {
      description: "The same 1,000 users are spread evenly over 100 Redis stock keys to provide a distributed baseline.",
      saleIds: { from: 3, to: 102 },
      actorUserIds: { from: 1, to: 1_000 },
      mapping: "saleId = 3 + ((userId - 1) % 100)",
      requestsPerActor: 1,

      expectedInvariant: {
        successfulPurchases: 1_000,
        rejectedPurchases: 0,
        remainingStockPerSale: 0,
        finalSaleStatus: "SOLD_OUT"
      }
    },

    duplicatePurchaseRace: {
      description:
          "One authenticated user fires 50 concurrent requests at the same sale.",

      saleIds: [103],
      actorUserIds: [1_001],
      concurrentRequests: 50,

      expectedInvariant: {
        successfulPurchases: 1,
        rejectedPurchases: 49,
        allowedRejectCodes: [
          "PURCHASE_002",
          "SALE_003"
        ],
        finalRemainingStock: 99,
        activePurchasesForUserAndSale: 1
      },
      riskNote: "Concurrent duplicate requests may be rejected either by the database duplicate check or by the Redis per-user purchase limit."
    },

    cancelRace: {
      description:
          "One authenticated user fires 50 concurrent cancels for the same pending purchase.",

      saleIds: [104],
      purchaseIds: [1],
      actorUserIds: [1_002],
      concurrentRequests: 50,

      expectedInvariant: {
        successfulCancellations: 1,
        rejectedCancellations: 49,
        finalPurchaseStatus: "CANCELED",
        finalRemainingStock: 100
      },
      riskNote: "The Purchase pessimistic lock and status revalidation must ensure that Redis stock is restored exactly once."
    },

    paymentConfirmRace: {
      description:
          "One authenticated user fires 50 concurrent payment confirmation requests for the same READY payment.",
      saleIds: [105],
      purchaseIds: [2],
      paymentIds: [2],
      actorUserIds: [1_003],
      concurrentRequests: 50,
      expectedInvariant: {
        successfulConfirmations: 1,
        rejectedConfirmations: 49,
        allowedRejectCodes: [
          "PAYMENT_006",
          "PURCHASE_004"
        ],
        finalPurchaseStatus: "PURCHASED",
        finalPaymentStatus: "DONE",
        finalRemainingStock: 99
      },
      riskNote:
          "Concurrent confirmation requests must not complete the same payment more than once or corrupt the Purchase and Payment state."
    },

    paymentConfirmCancelRace: {
      description:
          "One payment confirmation request and one purchase cancellation request race against the same pending purchase.",
      saleIds: [106],
      purchaseIds: [3],
      paymentIds: [3],
      actorUserIds: [1_004],
      concurrentRequests: 2,

      expectedInvariant: {
        allowedFinalStates: [
          {
            purchaseStatus: "PURCHASED",
            paymentStatus: "DONE",
            remainingStock: 99
          },
          {
            purchaseStatus: "CANCELED",
            paymentStatus: "CANCELED",
            remainingStock: 100
          }
        ]
      },

      riskNote:
          "Payment confirmation and purchase cancellation must not deadlock or leave Payment, Purchase, and Redis stock in inconsistent states."
    }
  },

  users,
  goods,
  sales,

  purchases: [
    {
      id: 1,
      user_id: 1_002,
      sale_id: 104,
      quantity: 1,
      purchase_price: 10_104,
      status: "PENDING_PAYMENT",
      purchased_at: null,
      canceled_at: null,

      // 고정된 과거 날짜가 아니라 데이터 생성 시각
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    },

    {
      id: 2,
      user_id: 1_003,
      sale_id: 105,
      quantity: 1,
      purchase_price: 10_105,
      status: "PENDING_PAYMENT",
      purchased_at: null,
      canceled_at: null,
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    },

    {
      id: 3,
      user_id: 1_004,
      sale_id: 106,
      quantity: 1,
      purchase_price: 10_106,
      status: "PENDING_PAYMENT",
      purchased_at: null,
      canceled_at: null,
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    }
  ],

  payments: [
    {
      id: 1,
      purchase_id: 1,
      order_id:
          "GUDIT_PERF_CANCEL_RACE_0001",
      payment_key: null,
      amount: 10_104,
      status: "READY",
      approved_at: null,
      canceled_at: null,
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    },

    {
      id: 2,
      purchase_id: 2,
      order_id:
          "GUDIT_PERF_PAYMENT_CONFIRM_RACE_0002",
      payment_key: null,
      amount: 10_105,
      status: "READY",
      approved_at: null,
      canceled_at: null,
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    },

    {
      id: 3,
      purchase_id: 3,
      order_id:
          "GUDIT_PERF_PAYMENT_CONFIRM_CANCEL_RACE_0003",
      payment_key: null,
      amount: 10_106,
      status: "READY",
      approved_at: null,
      canceled_at: null,
      created_at: CREATED_AT,
      updated_at: CREATED_AT
    }
  ],

  actors
};

const here = dirname(
    fileURLToPath(import.meta.url)
);

const outputPath = resolve(
    here,
    "generated",
    "performance-test-data.json"
);

mkdirSync(
    dirname(outputPath),
    {
      recursive: true
    }
);

writeFileSync(
    outputPath,
    `${JSON.stringify(output, null, 2)}\n`,
    "utf8"
);

console.log(
    `Created ${outputPath}`
);

console.log(
    JSON.stringify(
        output.volumePlan,
        null,
        2
    )
);