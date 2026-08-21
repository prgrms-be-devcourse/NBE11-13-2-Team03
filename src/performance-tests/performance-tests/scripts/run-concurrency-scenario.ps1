param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("1", "2", "3", "4", "5")]
    [string]$Scenario,

    [string]$BaseUrl = "http://localhost:8080",
    [string]$P95Milliseconds = ""
)

$ErrorActionPreference = "Stop"

$suiteRoot = Split-Path $PSScriptRoot -Parent

$prepareScript = Join-Path `
    $suiteRoot `
    "test-data/prepare-db.ps1"

$scenarioScripts = @{
    "1" = "01-oversell-hotspot.js"
    "2" = "02-single-row-lock-capacity.js"
    "3" = "03-distributed-baseline.js"
    "4" = "04-duplicate-purchase-race.js"
    "5" = "05-cancel-race.js"
}

$scenarioFile = $scenarioScripts[$Scenario]

$scenarioPath = Join-Path `
    $suiteRoot `
    "k6/concurrency/$scenarioFile"

Write-Host (
    "[1/2] Preparing performance fixture: " +
    "scenario=$Scenario"
)

& $prepareScript `
    -ResetPerformanceDatabase `
    -Scenario $Scenario

Write-Host (
    "[2/2] Running k6 scenario: " +
    "$scenarioFile"
)

$k6Arguments = @(
    "run",
    "-e",
    "BASE_URL=$BaseUrl"
)

if ($P95Milliseconds) {
    $k6Arguments += @(
        "-e",
        "P95_MS=$P95Milliseconds"
    )
}

$k6Arguments += $scenarioPath

& k6 @k6Arguments

if ($LASTEXITCODE -ne 0) {
    throw "k6 scenario $Scenario failed."
}

Write-Host "k6 scenario $Scenario passed."