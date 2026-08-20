param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$IncludeSoak,
    [switch]$SkipMixedPurchase,
    [int]$CooldownSeconds = 15
)

$ErrorActionPreference = "Stop"
$suiteRoot = Split-Path $PSScriptRoot -Parent
$k6Directory = Join-Path $suiteRoot "k6"
$loadDirectory = Join-Path $k6Directory "load"
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resultDirectory = Join-Path $suiteRoot "results/load-$runId"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

& k6 run -e "BASE_URL=$BaseUrl" (Join-Path $k6Directory "preflight.js")
if ($LASTEXITCODE -ne 0) {
    throw "Preflight failed. Reset fixture data and verify the Spring application."
}

$scenarios = @(
    "00-warmup.js",
    "01-baseline-load.js",
    "02-stress-load.js",
    "03-spike-load.js"
)
if ($IncludeSoak) {
    $scenarios += "04-soak-load.js"
}
if (-not $SkipMixedPurchase) {
    $scenarios += "05-mixed-purchase-peak.js"
}

$failed = @()
foreach ($scenario in $scenarios) {
    $scriptPath = Join-Path $loadDirectory $scenario
    $summaryPath = Join-Path $resultDirectory ($scenario -replace "\.js$", ".summary.json")
    Write-Host "Starting $scenario"
    & k6 run -e "BASE_URL=$BaseUrl" --summary-export $summaryPath $scriptPath
    if ($LASTEXITCODE -ne 0) {
        $failed += $scenario
    }
    if ($scenario -ne $scenarios[-1] -and $CooldownSeconds -gt 0) {
        Start-Sleep -Seconds $CooldownSeconds
    }
}

& node (Join-Path $PSScriptRoot "summarize-results.mjs") $resultDirectory
Write-Host "Load-test results: $resultDirectory"

if ($failed.Count -gt 0) {
    throw "Threshold failures: $($failed -join ', ')"
}

Write-Host "All selected load-test scenarios passed."
