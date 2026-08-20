param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$P95Milliseconds = ""
)

$ErrorActionPreference = "Stop"
$suiteRoot = Split-Path $PSScriptRoot -Parent
$k6Directory = Join-Path $suiteRoot "k6"
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

$failed = @()
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

Write-Host "Summaries: $resultDirectory"
if ($failed.Count -gt 0) {
    throw "Failed scenarios: $($failed -join ', ')"
}

Write-Host "All concurrency scenarios passed."
