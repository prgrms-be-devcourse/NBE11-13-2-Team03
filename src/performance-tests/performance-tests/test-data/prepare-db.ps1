param(
    [switch]$ResetPerformanceDatabase,
    [string]$Container = "gudit-performance-postgres",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres",
    [string]$RedisContainer = "gudit-performance-redis"
)

$ErrorActionPreference = "Stop"

if ($Container -ne "gudit-performance-postgres") {
    throw "Only gudit-performance-postgres can be reset."
}

if ($RedisContainer -ne "gudit-performance-redis") {
    throw "Only gudit-performance-redis can be reset."
}

if (-not $ResetPerformanceDatabase) {
    throw "This operation replaces rows in users, goods, goods_sales, purchases, and payments. Re-run with -ResetPerformanceDatabase only against the isolated performance-test database."
}

$dataDirectory = $PSScriptRoot
& node (Join-Path $dataDirectory "generate-data.mjs")
if ($LASTEXITCODE -ne 0) { throw "Fixture JSON generation failed." }

& node (Join-Path $dataDirectory "generate-seed-sql.mjs")
if ($LASTEXITCODE -ne 0) { throw "Seed SQL generation failed." }

$seedPath = Join-Path $dataDirectory "generated/seed-performance-data.sql"
Get-Content -Raw -LiteralPath $seedPath |
    docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $DatabaseUser -d $Database

if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL seed import failed."
}
Write-Host (
    "Performance-test data loaded into " +
    "database  '$Database' in container '$Container'."
)

& docker exec $RedisContainer redis-cli FLUSHDB

if ($LASTEXITCODE -ne 0) {
    throw "Performance Redis reset failed."
}

$fixturePath = Join-Path `
    $dataDirectory `
    "generated/performance-test-data.json"

$fixture = Get-Content `
    -Raw `
    -LiteralPath $fixturePath |
    ConvertFrom-Json

$saleFixture = $fixture.sales |
    Where-Object { $_.id -eq 1 }

if ($null -eq $saleFixture) {
    throw "Sale 1 fixture was not found."
}

$startAt = [DateTimeOffset]::Parse(
    "$($saleFixture.start_at)+09:00"
).ToUnixTimeMilliseconds()

$endAt = [DateTimeOffset]::Parse(
    "$($saleFixture.end_at)+09:00"
).ToUnixTimeMilliseconds()

# 판매 종료 시각 + 2일
$saleCacheExpireAt = $endAt + 172800000

& docker exec $RedisContainer `
    redis-cli `
    SET `
    "sale:1:stock" `
    "$($saleFixture.remaining_stock)"

if ($LASTEXITCODE -ne 0) {
    throw "Redis oversell-hotspot stock fixture initialization failed."
}

& docker exec $RedisContainer `
    redis-cli `
    HSET `
    "sale:1:info" `
    "startAt" `
    "$startAt" `
    "endAt" `
    "$endAt" `
    "maxPurchaseQuantity" `
    "$($saleFixture.max_purchase_quantity)" `
    "status" `
    "$($saleFixture.status)"

if ($LASTEXITCODE -ne 0) {
    throw "Redis oversell-hotspot info fixture initialization failed."
}

& docker exec $RedisContainer `
    redis-cli `
    PEXPIREAT `
    "sale:1:stock" `
    "$saleCacheExpireAt"

if ($LASTEXITCODE -ne 0) {
    throw "Redis oversell-hotspot stock TTL initialization failed."
}

& docker exec $RedisContainer `
    redis-cli `
    PEXPIREAT `
    "sale:1:info" `
    "$saleCacheExpireAt"

if ($LASTEXITCODE -ne 0) {
    throw "Redis oversell-hotspot info TTL initialization failed."
}

Write-Host (
    "Redis oversell-hotspot fixture loaded: " +
    "sale:$($saleFixture.id):stock=$($saleFixture.remaining_stock), " +
    "status=$($saleFixture.status), " +
    "expireAt=$saleCacheExpireAt"
)

& docker exec $RedisContainer `
    redis-cli `
    SET `
    "sale:104:stock" `
    "99"

if ($LASTEXITCODE -ne 0) {
    throw "Redis cancel-race stock fixture initialization failed."
}

& docker exec $RedisContainer `
    redis-cli `
    SET `
    "sale:104:user:1002" `
    "1"

if ($LASTEXITCODE -ne 0) {
    throw "Redis cancel-race user fixture initialization failed."
}

Write-Host(
        "Redis cancel-race fixture loaded: " +
        "sale:104:stock=99, " +
        "sale:104:user:1002=1"
)
