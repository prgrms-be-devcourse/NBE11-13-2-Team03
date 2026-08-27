param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$IncludeSoak,
    [switch]$SkipMixedPurchase,
    [int]$CooldownSeconds = 15,
    [int]$PurchaseVus = 100,
    [int]$PurchaseIterations = 1000
)

$ErrorActionPreference = "Stop"
if ($PurchaseVus -le 0) { throw "PurchaseVus must be greater than zero." }
if ($PurchaseIterations -le 0 -or $PurchaseIterations -gt 1000 -or $PurchaseVus -gt $PurchaseIterations) {
    throw "PurchaseIterations must be 1..1000 and at least PurchaseVus because the generated fixture has 1000 purchase actors."
}
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
    $arguments = @("run", "-e", "BASE_URL=$BaseUrl")
    if ($scenario -eq "05-mixed-purchase-peak.js") {
        $arguments += @(
            "-e", "PURCHASE_VUS=$PurchaseVus",
            "-e", "PURCHASE_ITERATIONS=$PurchaseIterations"
        )
    }
    $arguments += @("--summary-export", $summaryPath, $scriptPath)
    & k6 @arguments
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
