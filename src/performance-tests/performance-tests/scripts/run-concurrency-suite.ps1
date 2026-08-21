param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$P95Milliseconds = "",
    [string]$DatabaseContainer = "gudit-performance-postgres",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres",
    [string]$RedisContainer = "gudit-performance-redis",
    [int]$RedisDatabase = 0
)

$ErrorActionPreference = "Stop"
if ($RedisDatabase -lt 0) { throw "RedisDatabase cannot be negative." }
$suiteRoot = Split-Path $PSScriptRoot -Parent
$k6Directory = Join-Path $suiteRoot "k6"
$prepareDbPath = Join-Path $suiteRoot "test-data/prepare-db.ps1"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path $suiteRoot "results/concurrency-$runId"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

$preflight = Join-Path $k6Directory "preflight.js"
& k6 run -e "BASE_URL=$BaseUrl" $preflight
if ($LASTEXITCODE -ne 0) {
    throw "Preflight failed. Reset the fixture data and verify the Spring application before running load tests."
}

$scenarios = @(
    "01-oversell-hotspot.js",
    "02-single-row-lock-capacity.js",
    "03-distributed-baseline.js",
    "04-duplicate-purchase-race.js",
    "05-cancel-race.js"
)

function Reset-ConcurrencyState {
    Write-Host "시나리오 05 종료 후 성능테스트 Redis와 PostgreSQL을 기준 상태로 초기화합니다."
    & docker exec $RedisContainer redis-cli -n $RedisDatabase FLUSHDB SYNC | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 Redis 초기화에 실패했습니다." }

    & $prepareDbPath `
        -ResetPerformanceDatabase `
        -Container $DatabaseContainer `
        -Database $Database `
        -DatabaseUser $DatabaseUser
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 DB 초기화에 실패했습니다." }
}

$failed = @()
$finalResetError = $null
try {
    foreach ($scenario in $scenarios) {
        $scriptPath = Join-Path $k6Directory "concurrency/$scenario"
        $summaryPath = Join-Path $resultDirectory ($scenario -replace "\.js$", ".summary.json")
        $arguments = @("run", "-e", "BASE_URL=$BaseUrl")
        if ($P95Milliseconds) {
            $arguments += @("-e", "P95_MS=$P95Milliseconds")
        }
        $arguments += @("--summary-export", $summaryPath, $scriptPath)
        & k6 @arguments
        if ($LASTEXITCODE -ne 0) {
            $failed += $scenario
        }
    }
}
finally {
    try {
        Reset-ConcurrencyState
    }
    catch {
        $finalResetError = $_
    }
}

Write-Host "Summaries: $resultDirectory"
if ($null -ne $finalResetError) {
    throw "동시성 테스트 종료 후 기준 상태 복원에 실패했습니다: $($finalResetError.Exception.Message)"
}
if ($failed.Count -gt 0) {
    throw "Failed scenarios: $($failed -join ', ')"
}

Write-Host "All concurrency scenarios passed."
