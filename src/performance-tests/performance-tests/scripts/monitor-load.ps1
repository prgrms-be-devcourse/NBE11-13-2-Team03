param(
    [string]$OutputDirectory = "",
    [string]$DatabaseContainer = "postgres-db",
    [string]$ApplicationContainer = "",
    [string]$Database = "gudit",
    [string]$DatabaseUser = "postgres",
    [int]$DurationSeconds = 600,
    [int]$IntervalSeconds = 1,
    [switch]$ResetPgStatStatements
)

$ErrorActionPreference = "Stop"

if ($DurationSeconds -le 0) { throw "DurationSeconds must be greater than zero." }
if ($IntervalSeconds -le 0) { throw "IntervalSeconds must be greater than zero." }

if (-not $OutputDirectory) {
    $runId = Get-Date -Format "yyyyMMdd-HHmmss"
    $suiteRoot = Split-Path $PSScriptRoot -Parent
    $OutputDirectory = Join-Path $suiteRoot "results/monitor-$runId"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

$metricsPath = Join-Path $OutputDirectory "server-metrics.csv"
$settingsPath = Join-Path $OutputDirectory "postgres-settings.csv"
$queryStatsBeforePath = Join-Path $OutputDirectory "pg-stat-statements-before.csv"
$queryStatsAfterPath = Join-Path $OutputDirectory "pg-stat-statements-after.csv"

function Invoke-PostgresText {
    param([string]$Query)

    $result = & docker exec $DatabaseContainer psql `
        -v ON_ERROR_STOP=1 `
        -U $DatabaseUser `
        -d $Database `
        -t -A -F "|" `
        -c $Query

    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL monitoring query failed." }
    return ($result -join "`n").Trim()
}

function Export-PostgresCsv {
    param([string]$Query, [string]$Path)

    $result = & docker exec $DatabaseContainer psql `
        -v ON_ERROR_STOP=1 `
        -U $DatabaseUser `
        -d $Database `
        --csv `
        -c $Query

    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL CSV export failed." }
    $result | Set-Content -LiteralPath $Path -Encoding utf8
}

function Get-ContainerStats {
    param([string]$Container)

    if (-not $Container) { return @("", "", "", "", "", "") }

    $stats = (& docker stats --no-stream `
        --format "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.BlockIO}}|{{.NetIO}}|{{.PIDs}}" `
        $Container).Trim()

    if ($LASTEXITCODE -ne 0 -or -not $stats) {
        throw "Could not read Docker statistics for container '$Container'."
    }
    return $stats -split "\|", 6
}

$settingsQuery = @"
SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
    'max_connections', 'shared_buffers', 'work_mem',
    'effective_cache_size', 'shared_preload_libraries', 'track_io_timing'
)
ORDER BY name;
"@
Export-PostgresCsv -Query $settingsQuery -Path $settingsPath

$pgStatStatementsEnabled = (Invoke-PostgresText -Query @"
SELECT
    EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'
    )
    AND current_setting('shared_preload_libraries') LIKE '%pg_stat_statements%';
"@) -eq "t"

$topQueriesQuery = @"
SELECT
    queryid,
    calls,
    round(total_exec_time::numeric, 2) AS total_exec_time_ms,
    round(mean_exec_time::numeric, 2) AS mean_exec_time_ms,
    round(max_exec_time::numeric, 2) AS max_exec_time_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    temp_blks_written,
    wal_bytes,
    left(regexp_replace(query, E'[\n\r]+', ' ', 'g'), 500) AS query
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
ORDER BY total_exec_time DESC
LIMIT 30;
"@

if ($pgStatStatementsEnabled) {
    if ($ResetPgStatStatements) {
        Invoke-PostgresText -Query "SELECT pg_stat_statements_reset();" | Out-Null
    }
    Export-PostgresCsv -Query $topQueriesQuery -Path $queryStatsBeforePath
} else {
    Write-Warning "pg_stat_statements is not enabled. Aggregate server metrics will still be collected."
}

$activityQuery = @"
SELECT
    count(*) FILTER (WHERE pid <> pg_backend_pid()) AS connections,
    count(*) FILTER (WHERE state = 'active' AND pid <> pg_backend_pid()) AS active_connections,
    count(*) FILTER (WHERE wait_event IS NOT NULL AND pid <> pg_backend_pid()) AS all_waiters,
    count(*) FILTER (WHERE wait_event_type = 'Lock') AS lock_waiters,
    count(*) FILTER (WHERE wait_event_type = 'LWLock') AS lwlock_waiters,
    count(*) FILTER (WHERE wait_event_type = 'IO') AS io_waiters,
    count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
    count(*) FILTER (WHERE cardinality(pg_blocking_pids(pid)) > 0) AS blocked_sessions,
    COALESCE(
        max(EXTRACT(EPOCH FROM clock_timestamp() - query_start) * 1000)
            FILTER (WHERE state = 'active' AND pid <> pg_backend_pid()),
        0
    )::bigint AS longest_query_ms,
    (SELECT count(*) FROM pg_locks WHERE granted = false) AS ungranted_locks,
    d.deadlocks,
    d.xact_commit,
    d.xact_rollback,
    d.blks_read,
    d.blks_hit,
    d.temp_files,
    d.temp_bytes
FROM pg_stat_activity a
CROSS JOIN pg_stat_database d
WHERE a.datname = current_database()
  AND d.datname = current_database()
GROUP BY
    d.deadlocks, d.xact_commit, d.xact_rollback,
    d.blks_read, d.blks_hit, d.temp_files, d.temp_bytes;
"@

$samples = [Math]::Ceiling($DurationSeconds / $IntervalSeconds)
Write-Host "Monitoring started: samples=$samples, interval=${IntervalSeconds}s"

for ($index = 0; $index -lt $samples; $index += 1) {
    $sampleStartedAt = Get-Date
    $timestamp = $sampleStartedAt.ToString("o")
    $databaseParts = Get-ContainerStats -Container $DatabaseContainer
    $applicationParts = Get-ContainerStats -Container $ApplicationContainer

    $databaseActivity = Invoke-PostgresText -Query $activityQuery
    $activityParts = $databaseActivity -split "\|", 17
    if ($activityParts.Count -ne 17) {
        throw "Unexpected PostgreSQL metrics response: '$databaseActivity'"
    }

    $blocksRead = [double]$activityParts[13]
    $blocksHit = [double]$activityParts[14]
    $cacheDenominator = $blocksRead + $blocksHit
    $cacheHitRatio = if ($cacheDenominator -gt 0) {
        [Math]::Round($blocksHit / $cacheDenominator, 6)
    } else { 1 }

    [PSCustomObject]@{
        timestamp = $timestamp
        database_cpu = $databaseParts[0]
        database_memory = $databaseParts[1]
        database_memory_percent = $databaseParts[2]
        database_block_io = $databaseParts[3]
        database_network_io = $databaseParts[4]
        database_pids = $databaseParts[5]
        database_connections = $activityParts[0]
        database_active_connections = $activityParts[1]
        database_all_waiters = $activityParts[2]
        database_lock_waiters = $activityParts[3]
        database_lwlock_waiters = $activityParts[4]
        database_io_waiters = $activityParts[5]
        database_idle_in_transaction = $activityParts[6]
        database_blocked_sessions = $activityParts[7]
        database_longest_query_ms = $activityParts[8]
        database_ungranted_locks = $activityParts[9]
        database_deadlocks_total = $activityParts[10]
        database_commits_total = $activityParts[11]
        database_rollbacks_total = $activityParts[12]
        database_blocks_read_total = $activityParts[13]
        database_blocks_hit_total = $activityParts[14]
        database_cache_hit_ratio = $cacheHitRatio
        database_temp_files_total = $activityParts[15]
        database_temp_bytes_total = $activityParts[16]
        application_cpu = $applicationParts[0]
        application_memory = $applicationParts[1]
        application_memory_percent = $applicationParts[2]
        application_block_io = $applicationParts[3]
        application_network_io = $applicationParts[4]
        application_pids = $applicationParts[5]
    } | Export-Csv -LiteralPath $metricsPath -NoTypeInformation -Append

    if ($index -lt $samples - 1) {
        $elapsedSeconds = ((Get-Date) - $sampleStartedAt).TotalSeconds
        $remainingSeconds = [Math]::Max(0, $IntervalSeconds - $elapsedSeconds)
        if ($remainingSeconds -gt 0) {
            Start-Sleep -Milliseconds ([int]($remainingSeconds * 1000))
        }
    }
}

if ($pgStatStatementsEnabled) {
    Export-PostgresCsv -Query $topQueriesQuery -Path $queryStatsAfterPath
}

Write-Host "Server metrics: $metricsPath"
Write-Host "PostgreSQL settings: $settingsPath"
if ($pgStatStatementsEnabled) {
    Write-Host "Query statistics before: $queryStatsBeforePath"
    Write-Host "Query statistics after: $queryStatsAfterPath"
}
