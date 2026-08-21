param(
    [switch]$ResetPerformanceDatabase,
    [string]$Container = "gudit-performance-postgres",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres"
)

$ErrorActionPreference = "Stop"

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

if ($LASTEXITCODE -ne 0) { throw "PostgreSQL seed import failed." }
Write-Host "Performance-test data loaded into database '$Database' in container '$Container'."
