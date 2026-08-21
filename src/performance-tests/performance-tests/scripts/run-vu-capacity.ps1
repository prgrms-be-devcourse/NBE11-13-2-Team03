param(
    [string]$BaseUrl = "http://localhost:8080",
    [int[]]$VuSteps = @(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000),
    [string]$HttpTimeout = "30s",
    [string]$MaxDuration = "2m",
    [int]$PreflightWaitSeconds = 90,
    [ValidateSet("Read", "DistributedPurchase", "SingleRowPurchase")]
    [string]$ProbeMode = "SingleRowPurchase",
    [int]$ServerSettleSeconds = 5,
    [int]$CooldownSeconds = 10,
    [double]$AllowedMissingRate = 0,
    [switch]$StopAfterFirstMissing,
    [switch]$ResetPerformanceDatabase,
    [switch]$SkipFinalDatabaseReset,
    [string]$DatabaseContainer = "gudit-performance-postgres",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres",
    [string]$RedisContainer = "gudit-performance-redis",
    [int]$RedisDatabase = 0
)

$ErrorActionPreference = "Stop"

if (-not $ResetPerformanceDatabase) {
    throw "이 스크립트는 VU 테스트 전후에 성능테스트 DB를 초기화합니다. 격리된 DB인지 확인한 뒤 -ResetPerformanceDatabase를 지정하세요."
}
if ($VuSteps.Count -eq 0) { throw "VuSteps must contain at least one value." }
if ($ServerSettleSeconds -lt 0) { throw "ServerSettleSeconds cannot be negative." }
if ($PreflightWaitSeconds -lt 0) { throw "PreflightWaitSeconds cannot be negative." }
if ($CooldownSeconds -lt 0) { throw "CooldownSeconds cannot be negative." }
if ($AllowedMissingRate -lt 0 -or $AllowedMissingRate -ge 1) {
    throw "AllowedMissingRate must be at least 0 and less than 1."
}
if ($RedisDatabase -lt 0) { throw "RedisDatabase cannot be negative." }

$suiteRoot = Split-Path $PSScriptRoot -Parent
$k6Directory = Join-Path $suiteRoot "k6"
$probePath = Join-Path $k6Directory "vu-capacity\reachability-probe.js"
$preflightPath = Join-Path $k6Directory "preflight.js"
$prepareDbPath = Join-Path $suiteRoot "test-data\prepare-db.ps1"
$fixturePath = Join-Path $suiteRoot "test-data\generated\performance-test-data.json"
$summarizerPath = Join-Path $PSScriptRoot "summarize-vu-capacity.mjs"

foreach ($requiredPath in @($probePath, $preflightPath, $prepareDbPath, $fixturePath, $summarizerPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "필수 파일을 찾을 수 없습니다: $requiredPath"
    }
}

foreach ($command in @("k6", "node", "docker")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "필수 명령을 찾을 수 없습니다: $command"
    }
}

$fixtures = Get-Content -Raw -LiteralPath $fixturePath | ConvertFrom-Json
$maximumVus = [int]$fixtures.metadata.maximumConcurrentVus
$normalizedSteps = @($VuSteps | Sort-Object -Unique)
foreach ($vus in $normalizedSteps) {
    if ($vus -le 0 -or $vus -gt $maximumVus) {
        throw "각 VU 단계는 1 이상, 생성 데이터가 지원하는 최대값 $maximumVus 이하여야 합니다. actual=$vus"
    }
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path $suiteRoot "results\vu-capacity-$runId"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$probeModeValue = switch ($ProbeMode) {
    "Read" { "read" }
    "DistributedPurchase" { "distributed-purchase" }
    "SingleRowPurchase" { "single-row-purchase" }
}
$probeMutatesDatabase = $ProbeMode -ne "Read"

function Reset-Fixtures {
    Write-Host "성능테스트 Redis와 PostgreSQL을 생성 데이터의 기준 상태로 초기화합니다."
    & docker exec $RedisContainer redis-cli -n $RedisDatabase FLUSHDB SYNC | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 Redis 초기화에 실패했습니다." }

    & $prepareDbPath `
        -ResetPerformanceDatabase `
        -Container $DatabaseContainer `
        -Database $Database `
        -DatabaseUser $DatabaseUser
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 DB 초기화에 실패했습니다." }
}

function Invoke-Preflight {
    & k6 run `
        -e "BASE_URL=$BaseUrl" `
        -e "PREFLIGHT_WAIT_SECONDS=$PreflightWaitSeconds" `
        $preflightPath
    if ($LASTEXITCODE -ne 0) {
        throw "Preflight failed. Spring performance 프로필, JWT 키, DB 기준 데이터를 확인하세요."
    }
}

function Get-CounterValue {
    param([object]$Summary, [string]$MetricName)

    $metric = $Summary.metrics.PSObject.Properties[$MetricName].Value
    if ($null -eq $metric) { return 0 }
    if ($null -ne $metric.values -and $null -ne $metric.values.count) {
        return [double]$metric.values.count
    }
    if ($null -ne $metric.count) { return [double]$metric.count }
    return 0
}

Reset-Fixtures
Invoke-Preflight

$failedRuns = @()
try {
    for ($stepIndex = 0; $stepIndex -lt $normalizedSteps.Count; $stepIndex += 1) {
        $vus = $normalizedSteps[$stepIndex]
        if ($probeMutatesDatabase -and $stepIndex -gt 0) {
            Reset-Fixtures
            Invoke-Preflight
        }

        $stageRunId = "vu-$runId-$vus"
        $summaryPath = Join-Path $resultDirectory "vu-$vus.summary.json"
        Write-Host "VU $vus 도달성 테스트를 시작합니다."

        $arguments = @(
            "run",
            "-e", "BASE_URL=$BaseUrl",
            "-e", "VUS=$vus",
            "-e", "RUN_ID=$stageRunId",
            "-e", "HTTP_TIMEOUT=$HttpTimeout",
            "-e", "MAX_DURATION=$MaxDuration",
            "-e", "PROBE_MODE=$probeModeValue",
            "-e", "SERVER_SETTLE_SECONDS=$ServerSettleSeconds",
            "--summary-export", $summaryPath,
            $probePath
        )
        & k6 @arguments
        if ($LASTEXITCODE -ne 0) {
            $failedRuns += $vus
        }

        if (Test-Path -LiteralPath $summaryPath -PathType Leaf) {
            $summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
            $attempts = Get-CounterValue -Summary $summary -MetricName "probe_attempts"
            $arrivals = Get-CounterValue -Summary $summary -MetricName "probe_server_arrivals"
            $completions = Get-CounterValue -Summary $summary -MetricName "probe_server_completions"
            $activeAtSnapshot = Get-CounterValue -Summary $summary -MetricName "probe_server_active_at_snapshot"
            $missingRate = if ($attempts -gt 0) { ($attempts - $arrivals) / $attempts } else { 1 }

            Write-Host "VU ${vus}: k6 시도=$attempts, 진입=$arrivals, 완료=$completions, snapshot active=$activeAtSnapshot, 미도달률=$([Math]::Round($missingRate * 100, 3))%"
            if ($activeAtSnapshot -gt 0) {
                Write-Warning "snapshot 시점에 처리 중인 요청이 남아 있습니다. ServerSettleSeconds를 늘려 재측정하세요."
            }
            if ($StopAfterFirstMissing -and $missingRate -gt $AllowedMissingRate) {
                Write-Warning "허용 미도달률을 처음 초과한 VU 단계에서 탐색을 중단합니다."
                break
            }
        }

        if ($vus -ne $normalizedSteps[-1] -and $CooldownSeconds -gt 0) {
            Start-Sleep -Seconds $CooldownSeconds
        }
    }

    & node $summarizerPath $resultDirectory $AllowedMissingRate $ProbeMode
    if ($LASTEXITCODE -ne 0) { throw "VU 결과 요약 생성에 실패했습니다." }
}
finally {
    if (-not $SkipFinalDatabaseReset) {
        Reset-Fixtures
        Invoke-Preflight
        Write-Host "다음 성능테스트를 실행할 수 있도록 Redis/DB 기준 상태와 preflight를 복원했습니다."
    }
}

Write-Host "VU 테스트 결과: $resultDirectory"
if ($failedRuns.Count -gt 0) {
    Write-Warning "k6 실행 코드가 0이 아니었던 단계: $($failedRuns -join ', '). JSON/CSV 결과의 분류를 확인하세요."
}
