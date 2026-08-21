import { createHash, createHmac } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const USER_COUNT = 1_002;
const DISTRIBUTED_SALE_COUNT = 100;
const ISSUER = "test@naver.com";

const JWT_ISSUED_AT = Math.floor(
    Date.parse("2026-01-01T00:00:00Z") / 1000
);

const JWT_EXPIRES_AT = Math.floor(
    Date.parse("2035-01-01T00:00:00Z") / 1000
);

function pad(value) {
  return String(value).padStart(2, "0");
}

function toLocalDateTime(date) {
  return [
    date.getFullYear(),
    "-",
    pad(date.getMonth() + 1),
    "-",
    pad(date.getDate()),
    "T",
    pad(date.getHours()),
    ":",
    pad(date.getMinutes()),
    ":",
    pad(date.getSeconds())
  ].join("");
}

const generatedAt = new Date();

/**
 * 고정된 과거 시각을 제거한다.
 *
 * PurchaseTimeoutScheduler가 생성 후 10분이 지난
 * PENDING_PAYMENT 구매를 자동 취소하므로 모든 테스트 데이터의
 * 생성 시각을 스크립트 실행 시각으로 설정한다.
 */
const CREATED_AT =
    toLocalDateTime(generatedAt);

/**
 * 판매는 READY 상태로 생성한다.
 *
 * 시작 시각을 생성 시각보다 1분 전으로 설정하면
 * startSales() 스케줄러의 다음 조건에 포함된다.
 *
 * status = READY
 * startAt <= now
 * endAt > now
 *
 * 이후 스케줄러가 다음 순서로 처리한다.
 *
 * warmupSaleInfo()
 * → Redis 적재
 * → startSale()
 * → ON_SALE 전환
 */
const SALE_START_AT = toLocalDateTime(
    new Date(
        generatedAt.getTime() - 60 * 1_000
    )
);

const SALE_END_AT = toLocalDateTime(
    new Date(
        generatedAt.getTime()
        + 24 * 60 * 60 * 1_000
    )
);

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
    { length: 104 },
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

    // 스케줄러가 조회할 수 있도록 READY로 생성
    status: "READY",

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
  )
];

/**
 * 판매 104번에는 미결제 구매 1건이 존재한다.
 * 해당 구매가 재고 1개를 예약했으므로 남은 재고는 99다.
 */
sales.find(
    ({ id }) => id === 104
).remaining_stock = 99;

const actors = users.map(({ id }) => ({
  userId: id,
  accessToken: createAccessToken(id)
}));

const output = {
  metadata: {
    name:
        "Gudit k6 concurrency performance fixtures",

    generatedAt:
        generatedAt.toISOString(),

    targetApi:
        "POST /api/sales/{saleId}/purchases",

    cancelApi:
        "POST /api/purchases/{purchaseId}/cancel",

    maximumConcurrentVus: 1_000,

    requiresEmptyIsolatedDatabase: true,

    warning:
        "The bundled JWT secret and tokens are test-only. Never use them in production.",

    schedulerInitialization: {
      initialStatus: "READY",
      expectedStatusAfterScheduler:
          "ON_SALE",
      behavior:
          "startSales warms Redis before changing the sale status"
    },

    requiredEnvironment: {
      JWT_SECRET_KEY:
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
      goods: 104,
      goods_sales: 104,
      purchases: 1,
      payments: 1
    }
  },

  volumePlan: {
    users: {
      count: USER_COUNT,
      reason:
          "1,000 unique VUs plus one duplicate-purchase actor and one cancellation actor"
    },

    goods: {
      count: 104,
      reason:
          "one goods row per sale fixture"
    },

    sales: {
      count: 104,
      reason:
          "two hotspot fixtures, 100 distributed fixtures, and two race-condition fixtures"
    },

    purchases: {
      count: 1,
      reason:
          "pending purchase used by the concurrent cancellation test"
    },

    payments: {
      count: 1,
      reason:
          "READY payment paired with the pending cancellation purchase"
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
      description:
          "The same 1,000 users are spread evenly over 100 sale rows to provide a low-contention baseline.",

      saleIds: {
        from: 3,
        to: 102
      },

      actorUserIds: {
        from: 1,
        to: 1_000
      },

      mapping:
          "saleId = 3 + ((userId - 1) % 100)",

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
        rejectCode: "PURCHASE_002",
        finalRemainingStock: 99,
        activePurchasesForUserAndSale: 1
      },

      riskNote:
          "The current application checks duplicates before inserting but has no database unique constraint, so this scenario can reveal duplicate rows."
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

      riskNote:
          "The current application reads the purchase without a row lock, so repeated stock restoration is an important invariant to check."
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