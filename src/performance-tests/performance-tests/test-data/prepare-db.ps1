param(
    [switch]$ResetPerformanceDatabase,
    [ValidateSet("1", "2", "3", "4", "5", "6", "7", "All")]
    [string]$Scenario = "All",
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
if ($LASTEXITCODE -ne 0) {
    throw "Fixture JSON generation failed."
}

& node (Join-Path $dataDirectory "generate-seed-sql.mjs")
if ($LASTEXITCODE -ne 0) {
    throw "Seed SQL generation failed."
}

$seedPath = Join-Path `
    $dataDirectory `
    "generated/seed-performance-data.sql"

Get-Content -Raw -LiteralPath $seedPath |
    docker exec -i `
        $Container `
        psql `
        -v ON_ERROR_STOP=1 `
        -U $DatabaseUser `
        -d $Database

if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL seed import failed."
}

Write-Host (
    "Performance-test data loaded into " +
    "database '$Database' in container '$Container'."
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

# Sale 1: 단일 판매 재고 초과 구매 테스트
if ($Scenario -in @("1", "All")) {
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
    $saleCacheExpireAt =
        $endAt + 172800000

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
}

# Sale 2: 재고 1,000개에 사용자 1,000명이 동시에 구매하는 처리량 테스트
if ($Scenario -in @("2", "All")) {
    $sale2Fixture = $fixture.sales |
        Where-Object { $_.id -eq 2 }

    if ($null -eq $sale2Fixture) {
        throw "Sale 2 fixture was not found."
    }

    $sale2StartAt = [DateTimeOffset]::Parse(
        "$($sale2Fixture.start_at)+09:00"
    ).ToUnixTimeMilliseconds()

    $sale2EndAt = [DateTimeOffset]::Parse(
        "$($sale2Fixture.end_at)+09:00"
    ).ToUnixTimeMilliseconds()

    # 판매 종료 시각 + 2일
    $sale2CacheExpireAt =
        $sale2EndAt + 172800000

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        "sale:2:stock" `
        "$($sale2Fixture.remaining_stock)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis capacity-test stock fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        HSET `
        "sale:2:info" `
        "startAt" `
        "$sale2StartAt" `
        "endAt" `
        "$sale2EndAt" `
        "maxPurchaseQuantity" `
        "$($sale2Fixture.max_purchase_quantity)" `
        "status" `
        "$($sale2Fixture.status)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis capacity-test info fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        "sale:2:stock" `
        "$sale2CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis capacity-test stock TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        "sale:2:info" `
        "$sale2CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis capacity-test info TTL initialization failed."
    }

    Write-Host (
        "Redis capacity-test fixture loaded: " +
        "sale:$($sale2Fixture.id):stock=$($sale2Fixture.remaining_stock), " +
        "status=$($sale2Fixture.status), " +
        "expireAt=$sale2CacheExpireAt"
    )
}

# Sale 3~102: 사용자 1,000명을 판매 100개에 분산하는 기준 성능 테스트
if ($Scenario -in @("3", "All")) {
    $distributedSaleFixtures = @(
        $fixture.sales |
            Where-Object {
                $_.id -ge 3 -and $_.id -le 102
            }
    )

    if ($distributedSaleFixtures.Count -ne 100) {
        throw (
            "Expected 100 distributed Sale fixtures, " +
            "but found $($distributedSaleFixtures.Count)."
        )
    }

    foreach ($distributedSaleFixture in $distributedSaleFixtures) {
        $distributedSaleId =
            $distributedSaleFixture.id

        $distributedStartAt = [DateTimeOffset]::Parse(
            "$($distributedSaleFixture.start_at)+09:00"
        ).ToUnixTimeMilliseconds()

        $distributedEndAt = [DateTimeOffset]::Parse(
            "$($distributedSaleFixture.end_at)+09:00"
        ).ToUnixTimeMilliseconds()

        # 판매 종료 시각 + 2일
        $distributedCacheExpireAt =
            $distributedEndAt + 172800000

        $distributedStockKey =
            "sale:$distributedSaleId`:stock"

        $distributedInfoKey =
            "sale:$distributedSaleId`:info"

        & docker exec $RedisContainer `
            redis-cli `
            SET `
            $distributedStockKey `
            "$($distributedSaleFixture.remaining_stock)" |
            Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw (
                "Redis distributed-test stock fixture " +
                "initialization failed: saleId=$distributedSaleId"
            )
        }

        & docker exec $RedisContainer `
            redis-cli `
            HSET `
            $distributedInfoKey `
            "startAt" `
            "$distributedStartAt" `
            "endAt" `
            "$distributedEndAt" `
            "maxPurchaseQuantity" `
            "$($distributedSaleFixture.max_purchase_quantity)" `
            "status" `
            "$($distributedSaleFixture.status)" |
            Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw (
                "Redis distributed-test info fixture " +
                "initialization failed: saleId=$distributedSaleId"
            )
        }

        & docker exec $RedisContainer `
            redis-cli `
            PEXPIREAT `
            $distributedStockKey `
            "$distributedCacheExpireAt" |
            Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw (
                "Redis distributed-test stock TTL " +
                "initialization failed: saleId=$distributedSaleId"
            )
        }

        & docker exec $RedisContainer `
            redis-cli `
            PEXPIREAT `
            $distributedInfoKey `
            "$distributedCacheExpireAt" |
            Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw (
                "Redis distributed-test info TTL " +
                "initialization failed: saleId=$distributedSaleId"
            )
        }
    }

    Write-Host (
        "Redis distributed-test fixtures loaded: " +
        "saleIds=3..102, count=$($distributedSaleFixtures.Count)"
    )
}

# Sale 103: 동일 사용자의 중복 구매 요청 50건 동시 처리 테스트
if ($Scenario -in @("4", "All")) {
    $sale103Fixture = $fixture.sales |
        Where-Object { $_.id -eq 103 }

    if ($null -eq $sale103Fixture) {
        throw "Sale 103 fixture was not found."
    }

    $sale103StartAt = [DateTimeOffset]::Parse(
        "$($sale103Fixture.start_at)+09:00"
    ).ToUnixTimeMilliseconds()

    $sale103EndAt = [DateTimeOffset]::Parse(
        "$($sale103Fixture.end_at)+09:00"
    ).ToUnixTimeMilliseconds()

    # 판매 종료 시각 + 2일
    $sale103CacheExpireAt =
        $sale103EndAt + 172800000

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        "sale:103:stock" `
        "$($sale103Fixture.remaining_stock)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis duplicate-purchase stock fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        HSET `
        "sale:103:info" `
        "startAt" `
        "$sale103StartAt" `
        "endAt" `
        "$sale103EndAt" `
        "maxPurchaseQuantity" `
        "$($sale103Fixture.max_purchase_quantity)" `
        "status" `
        "$($sale103Fixture.status)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis duplicate-purchase info fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        "sale:103:stock" `
        "$sale103CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis duplicate-purchase stock TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        "sale:103:info" `
        "$sale103CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis duplicate-purchase info TTL initialization failed."
    }

    Write-Host (
        "Redis duplicate-purchase fixture loaded: " +
        "sale:$($sale103Fixture.id):stock=$($sale103Fixture.remaining_stock), " +
        "status=$($sale103Fixture.status), " +
        "expireAt=$sale103CacheExpireAt"
    )
}

# Sale 104: 동일 Purchase에 대한 취소 요청 50건 동시 처리 테스트
if ($Scenario -in @("5", "All")) {
    $sale104Fixture = $fixture.sales |
        Where-Object { $_.id -eq 104 }

    if ($null -eq $sale104Fixture) {
        throw "Sale 104 fixture was not found."
    }

    $cancelPurchaseFixture = $fixture.purchases |
        Where-Object {
            $_.sale_id -eq $sale104Fixture.id
        }

    if ($null -eq $cancelPurchaseFixture) {
        throw "Cancel-race Purchase fixture was not found."
    }

    $sale104StartAt = [DateTimeOffset]::Parse(
        "$($sale104Fixture.start_at)+09:00"
    ).ToUnixTimeMilliseconds()

    $sale104EndAt = [DateTimeOffset]::Parse(
        "$($sale104Fixture.end_at)+09:00"
    ).ToUnixTimeMilliseconds()

    # stock/info Key: 판매 종료 시각 + 2일
    $sale104CacheExpireAt =
        $sale104EndAt + 172800000

    # 사용자 구매 Key: 판매 종료 시각 + 1일
    $sale104UserExpireAt =
        $sale104EndAt + 86400000

    $sale104StockKey =
        "sale:$($sale104Fixture.id):stock"

    $sale104InfoKey =
        "sale:$($sale104Fixture.id):info"

    $sale104UserKey =
        "sale:$($sale104Fixture.id):user:$($cancelPurchaseFixture.user_id)"

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale104StockKey `
        "$($sale104Fixture.remaining_stock)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race stock fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        HSET `
        $sale104InfoKey `
        "startAt" `
        "$sale104StartAt" `
        "endAt" `
        "$sale104EndAt" `
        "maxPurchaseQuantity" `
        "$($sale104Fixture.max_purchase_quantity)" `
        "status" `
        "$($sale104Fixture.status)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race info fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale104UserKey `
        "$($cancelPurchaseFixture.quantity)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race user fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale104StockKey `
        "$sale104CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race stock TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale104InfoKey `
        "$sale104CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race info TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale104UserKey `
        "$sale104UserExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis cancel-race user TTL initialization failed."
    }

    Write-Host (
        "Redis cancel-race fixture loaded: " +
        "$sale104StockKey=$($sale104Fixture.remaining_stock), " +
        "$sale104UserKey=$($cancelPurchaseFixture.quantity), " +
        "status=$($sale104Fixture.status)"
    )
}

# Sale 105: 동일 Payment에 대한 결제 승인 요청 50건 동시 처리 테스트
if ($Scenario -in @("6", "All")) {
    $sale105Fixture = $fixture.sales |
        Where-Object { $_.id -eq 105 }

    if ($null -eq $sale105Fixture) {
        throw "Sale 105 fixture was not found."
    }

    $paymentConfirmPurchaseFixture = $fixture.purchases |
        Where-Object {
            $_.sale_id -eq $sale105Fixture.id
        }

    if ($null -eq $paymentConfirmPurchaseFixture) {
        throw "Payment-confirm-race Purchase fixture was not found."
    }

    $paymentConfirmFixture = $fixture.payments |
        Where-Object {
            $_.purchase_id -eq $paymentConfirmPurchaseFixture.id
        }

    if ($null -eq $paymentConfirmFixture) {
        throw "Payment-confirm-race Payment fixture was not found."
    }

    $sale105StartAt = [DateTimeOffset]::Parse(
        "$($sale105Fixture.start_at)+09:00"
    ).ToUnixTimeMilliseconds()

    $sale105EndAt = [DateTimeOffset]::Parse(
        "$($sale105Fixture.end_at)+09:00"
    ).ToUnixTimeMilliseconds()

    # stock/info Key: 판매 종료 시각 + 2일
    $sale105CacheExpireAt =
        $sale105EndAt + 172800000

    # 사용자 구매 Key: 판매 종료 시각 + 1일
    $sale105UserExpireAt =
        $sale105EndAt + 86400000

    $sale105StockKey =
        "sale:$($sale105Fixture.id):stock"

    $sale105InfoKey =
        "sale:$($sale105Fixture.id):info"

    $sale105UserKey =
        "sale:$($sale105Fixture.id):user:$($paymentConfirmPurchaseFixture.user_id)"

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale105StockKey `
        "$($sale105Fixture.remaining_stock)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race stock fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        HSET `
        $sale105InfoKey `
        "startAt" `
        "$sale105StartAt" `
        "endAt" `
        "$sale105EndAt" `
        "maxPurchaseQuantity" `
        "$($sale105Fixture.max_purchase_quantity)" `
        "status" `
        "$($sale105Fixture.status)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race info fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale105UserKey `
        "$($paymentConfirmPurchaseFixture.quantity)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race user fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale105StockKey `
        "$sale105CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race stock TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale105InfoKey `
        "$sale105CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race info TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale105UserKey `
        "$sale105UserExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-race user TTL initialization failed."
    }

    Write-Host (
        "Redis payment-confirm-race fixture loaded: " +
        "$sale105StockKey=$($sale105Fixture.remaining_stock), " +
        "$sale105UserKey=$($paymentConfirmPurchaseFixture.quantity), " +
        "paymentId=$($paymentConfirmFixture.id), " +
        "paymentStatus=$($paymentConfirmFixture.status), " +
        "status=$($sale105Fixture.status)"
    )
}

# Sale 106: 결제 승인과 구매 취소 동시 처리 테스트
if ($Scenario -in @("7", "All")) {
    $sale106Fixture = $fixture.sales |
        Where-Object { $_.id -eq 106 }

    if ($null -eq $sale106Fixture) {
        throw "Sale 106 fixture was not found."
    }

    $paymentConfirmCancelPurchaseFixture = $fixture.purchases |
        Where-Object {
            $_.sale_id -eq $sale106Fixture.id
        }

    if ($null -eq $paymentConfirmCancelPurchaseFixture) {
        throw "Payment-confirm-cancel-race Purchase fixture was not found."
    }

    $paymentConfirmCancelFixture = $fixture.payments |
        Where-Object {
            $_.purchase_id -eq $paymentConfirmCancelPurchaseFixture.id
        }

    if ($null -eq $paymentConfirmCancelFixture) {
        throw "Payment-confirm-cancel-race Payment fixture was not found."
    }

    $sale106StartAt = [DateTimeOffset]::Parse(
        "$($sale106Fixture.start_at)+09:00"
    ).ToUnixTimeMilliseconds()

    $sale106EndAt = [DateTimeOffset]::Parse(
        "$($sale106Fixture.end_at)+09:00"
    ).ToUnixTimeMilliseconds()

    # stock/info Key: 판매 종료 시각 + 2일
    $sale106CacheExpireAt =
        $sale106EndAt + 172800000

    # 사용자 구매 Key: 판매 종료 시각 + 1일
    $sale106UserExpireAt =
        $sale106EndAt + 86400000

    $sale106StockKey =
        "sale:$($sale106Fixture.id):stock"

    $sale106InfoKey =
        "sale:$($sale106Fixture.id):info"

    $sale106UserKey =
        "sale:$($sale106Fixture.id):user:$($paymentConfirmCancelPurchaseFixture.user_id)"

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale106StockKey `
        "$($sale106Fixture.remaining_stock)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race stock fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        HSET `
        $sale106InfoKey `
        "startAt" `
        "$sale106StartAt" `
        "endAt" `
        "$sale106EndAt" `
        "maxPurchaseQuantity" `
        "$($sale106Fixture.max_purchase_quantity)" `
        "status" `
        "$($sale106Fixture.status)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race info fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        SET `
        $sale106UserKey `
        "$($paymentConfirmCancelPurchaseFixture.quantity)"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race user fixture initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale106StockKey `
        "$sale106CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race stock TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale106InfoKey `
        "$sale106CacheExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race info TTL initialization failed."
    }

    & docker exec $RedisContainer `
        redis-cli `
        PEXPIREAT `
        $sale106UserKey `
        "$sale106UserExpireAt"

    if ($LASTEXITCODE -ne 0) {
        throw "Redis payment-confirm-cancel-race user TTL initialization failed."
    }

    Write-Host (
        "Redis payment-confirm-cancel-race fixture loaded: " +
        "$sale106StockKey=$($sale106Fixture.remaining_stock), " +
        "$sale106UserKey=$($paymentConfirmCancelPurchaseFixture.quantity), " +
        "paymentId=$($paymentConfirmCancelFixture.id), " +
        "paymentStatus=$($paymentConfirmCancelFixture.status), " +
        "status=$($sale106Fixture.status)"
    )
}

$targetSaleIds = switch ($Scenario) {
    "1" { @(1) }
    "2" { @(2) }
    "3" { @(3..102) }
    "4" { @(103) }
    "5" { @(104) }
    "6" { @(105) }
    "7" { @(106) }
    "All" { @(1..105) }
}

foreach ($saleId in $targetSaleIds) {
    $redisStatus = & docker exec `
        $RedisContainer `
        redis-cli `
        HGET `
        "sale:$saleId`:info" `
        status

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read Redis fixture: saleId=$saleId"
    }

    if ($redisStatus.Trim() -ne "ON_SALE") {
        throw (
            "Redis fixture is not ON_SALE: " +
            "saleId=$saleId, status=$redisStatus"
        )
    }
}

Write-Host (
    "Performance fixtures are ready: " +
    "scenario=$Scenario, status=ON_SALE"
)