import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const fixtures = JSON.parse(
  readFileSync(resolve(here, "generated", "performance-test-data.json"), "utf8")
);

function sql(value) {
  if (value === null || value === undefined) return "NULL";
  if (typeof value === "number") return String(value);
  return `'${String(value).replaceAll("'", "''")}'`;
}

function insert(table, columns, rows) {
  const values = rows.map((row) =>
    `(${columns.map((column) => sql(row[column])).join(", ")})`
  );
  return `INSERT INTO ${table} (${columns.join(", ")}) VALUES\n${values.join(",\n")};`;
}

const statements = [
  "BEGIN;",
  "TRUNCATE TABLE payments, purchases, goods_sales, goods, users RESTART IDENTITY CASCADE;",
  insert("users", ["id", "kakao_id", "nickname", "email", "role", "provider", "created_at", "updated_at"], fixtures.users),
  insert("goods", ["id", "name", "description", "price", "image_url", "status", "created_at", "updated_at"], fixtures.goods),
  insert("goods_sales", ["id", "goods_id", "created_by", "initial_stock", "remaining_stock", "max_purchase_quantity", "status", "start_at", "end_at", "created_at", "updated_at"], fixtures.sales),
  insert("purchases", ["id", "user_id", "sale_id", "quantity", "purchase_price", "status", "purchased_at", "canceled_at", "created_at", "updated_at"], fixtures.purchases),
  insert("payments", ["id", "purchase_id", "order_id", "payment_key", "amount", "status", "approved_at", "canceled_at", "created_at", "updated_at"], fixtures.payments),
  "SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users), true);",
  "SELECT setval(pg_get_serial_sequence('goods', 'id'), (SELECT MAX(id) FROM goods), true);",
  "SELECT setval(pg_get_serial_sequence('goods_sales', 'id'), (SELECT MAX(id) FROM goods_sales), true);",
  "SELECT setval(pg_get_serial_sequence('purchases', 'id'), (SELECT MAX(id) FROM purchases), true);",
  "SELECT setval(pg_get_serial_sequence('payments', 'id'), (SELECT MAX(id) FROM payments), true);",
  "COMMIT;",
  ""
];

const outputPath = resolve(here, "generated", "seed-performance-data.sql");
writeFileSync(outputPath, statements.join("\n\n"), "utf8");
console.log(`Created ${outputPath}`);
