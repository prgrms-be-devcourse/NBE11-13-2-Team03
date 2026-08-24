param(
    [string]$MatrixPath = "",
    [string]$Group = "",
    [string]$CaseId = "",
    [switch]$All,
    [int]$RepeatCount = 0,
    [switch]$ResetPerformanceDatabase,
    [switch]$SkipBuild,
    [int]$Port = 8080,
    [int]$StartupTimeoutSeconds = 120,
    [int]$PreflightWaitSeconds = 90,
    [int]$CooldownSeconds = 10,
    [string]$K6DockerImage = "grafana/k6:latest",
    [string]$JavaDockerImage = "eclipse-temurin:25-jre",
    [string]$DockerNetwork = "gudit-performance_default"
)

$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

if (-not $ResetPerformanceDatabase) {
    throw "격리된 성능테스트 DB를 초기화할 권한을 확인한 뒤 -ResetPerformanceDatabase를 지정하세요."
}
if ([string]::IsNullOrWhiteSpace($Group) -and [string]::IsNullOrWhiteSpace($CaseId) -and -not $All) {
    throw "한 번에 필요한 실험만 실행하도록 -Group, -CaseId 또는 -All 중 하나를 지정하세요."
}
if ($Port -lt 1 -or $Port -gt 65535) { throw "Port must be from 1 to 65535." }
if ($StartupTimeoutSeconds -lt 1) { throw "StartupTimeoutSeconds must be positive." }
if ($PreflightWaitSeconds -lt 0) { throw "PreflightWaitSeconds cannot be negative." }
if ($CooldownSeconds -lt 0) { throw "CooldownSeconds cannot be negative." }
if ($RepeatCount -lt 0) { throw "RepeatCount cannot be negative." }

$suiteRoot = Split-Path $PSScriptRoot -Parent
$repositoryRoot = (Resolve-Path (Join-Path $suiteRoot "..\..\..")).Path
$probePath = Join-Path $suiteRoot "k6\experiments\bottleneck-probe.js"
$preflightPath = Join-Path $suiteRoot "k6\preflight.js"
$prepareDbPath = Join-Path $suiteRoot "test-data\prepare-db.ps1"
$fixturePath = Join-Path $suiteRoot "test-data\generated\performance-test-data.json"
$summarizerPath = Join-Path $PSScriptRoot "summarize-performance-experiments.mjs"
$defaultMatrixPath = Join-Path $suiteRoot "config\performance-experiment-matrix.csv"
$composePath = Join-Path $repositoryRoot "docker-compose.performance.yml"
$envFile = Join-Path $repositoryRoot ".env"

if ([string]::IsNullOrWhiteSpace($MatrixPath)) {
    $MatrixPath = $defaultMatrixPath
} else {
    $MatrixPath = (Resolve-Path -LiteralPath $MatrixPath).Path
}

foreach ($requiredPath in @(
    $MatrixPath,
    $probePath,
    $preflightPath,
    $prepareDbPath,
    $fixturePath,
    $summarizerPath,
    $composePath
)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "필수 파일을 찾을 수 없습니다: $requiredPath"
    }
}

foreach ($command in @("docker", "node")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "필수 명령을 찾을 수 없습니다: $command"
    }
}

function Convert-ToBoolean {
    param([string]$Value, [string]$FieldName)
    if ($Value -match "^(?i:true|false)$") { return [bool]::Parse($Value) }
    throw "$FieldName must be true or false. actual=$Value"
}

function Convert-ToPositiveInteger {
    param([string]$Value, [string]$FieldName, [switch]$AllowZero)
    $parsed = 0
    if (-not [int]::TryParse($Value, [ref]$parsed)) {
        throw "$FieldName must be an integer. actual=$Value"
    }
    if (($AllowZero -and $parsed -lt 0) -or (-not $AllowZero -and $parsed -le 0)) {
        throw "$FieldName has an invalid value: $parsed"
    }
    return $parsed
}

$matrix = @(Import-Csv -LiteralPath $MatrixPath | Where-Object {
    (Convert-ToBoolean -Value $_.enabled -FieldName "enabled") -and
    ($All -or ([string]::IsNullOrWhiteSpace($Group) -or $_.experiment_group -eq $Group)) -and
    ([string]::IsNullOrWhiteSpace($CaseId) -or $_.case_id -eq $CaseId)
})

if ($matrix.Count -eq 0) {
    $availableGroups = @(Import-Csv -LiteralPath $MatrixPath | Select-Object -ExpandProperty experiment_group -Unique)
    throw "선택한 실험이 없습니다. available groups=$($availableGroups -join ', ')"
}

$duplicateIds = @($matrix | Group-Object case_id | Where-Object Count -gt 1)
if ($duplicateIds.Count -gt 0) {
    throw "선택 범위에서 case_id가 중복됩니다: $($duplicateIds.Name -join ', ')"
}

$usesHostK6 = @($matrix | Where-Object k6_environment -eq "host").Count -gt 0
if ($usesHostK6 -and -not (Get-Command "k6" -ErrorAction SilentlyContinue)) {
    throw "host k6 실험이 포함되어 있지만 k6 명령을 찾을 수 없습니다."
}
$usesHostApplication = @($matrix | Where-Object application_environment -eq "host").Count -gt 0
if ($usesHostApplication -and -not (Get-Command "java" -ErrorAction SilentlyContinue)) {
    throw "host 애플리케이션 실험이 포함되어 있지만 java 명령을 찾을 수 없습니다."
}
if (@($matrix | Where-Object application_environment -eq "docker").Count -gt 0 -and
    -not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw "Docker 애플리케이션 실행에 필요한 .env 파일을 찾을 수 없습니다: $envFile"
}

foreach ($row in $matrix) {
    foreach ($field in @(
        "repeats", "tomcat_max_threads", "tomcat_min_spare", "tomcat_max_connections",
        "tomcat_accept_count", "hikari_max_pool_size", "hikari_min_idle",
        "hikari_connection_timeout_ms", "vus", "pre_allocated_vus", "max_vus"
    )) {
        $null = Convert-ToPositiveInteger -Value $row.$field -FieldName "$($row.case_id).$field"
    }
    $null = Convert-ToPositiveInteger -Value $row.spread_duration_seconds -FieldName "$($row.case_id).spread_duration_seconds" -AllowZero
    $null = Convert-ToPositiveInteger -Value $row.start_rate -FieldName "$($row.case_id).start_rate"
    $null = Convert-ToPositiveInteger -Value $row.target_rate -FieldName "$($row.case_id).target_rate"
    $null = Convert-ToBoolean -Value $row.no_connection_reuse -FieldName "$($row.case_id).no_connection_reuse"

    if ([int]$row.tomcat_min_spare -gt [int]$row.tomcat_max_threads) {
        throw "$($row.case_id): tomcat_min_spare cannot exceed tomcat_max_threads."
    }
    if ([int]$row.hikari_min_idle -gt [int]$row.hikari_max_pool_size) {
        throw "$($row.case_id): hikari_min_idle cannot exceed hikari_max_pool_size."
    }
    if ([int]$row.vus -gt 1000) {
        throw "$($row.case_id): generated actors support at most 1000 VUs."
    }
    if ($row.load_model -notin @("burst", "linear-spread", "ramping-vus", "ramping-arrival-rate")) {
        throw "$($row.case_id): unsupported load_model=$($row.load_model)"
    }
    if ($row.request_mode -notin @("read", "single-row-purchase", "distributed-purchase")) {
        throw "$($row.case_id): unsupported request_mode=$($row.request_mode)"
    }
    if ($row.inventory_mode -notin @("redis", "db-pessimistic")) {
        throw "$($row.case_id): unsupported inventory_mode=$($row.inventory_mode)"
    }
    if ($row.k6_environment -notin @("host", "docker")) {
        throw "$($row.case_id): k6_environment must be host or docker."
    }
    if ($row.application_environment -notin @("host", "docker")) {
        throw "$($row.case_id): application_environment must be host or docker."
    }
}

function Assert-Infrastructure {
    foreach ($container in @("gudit-performance-postgres", "gudit-performance-redis")) {
        $running = & docker inspect --format "{{.State.Running}}" $container 2>$null
        if ($LASTEXITCODE -ne 0 -or $running.Trim() -ne "true") {
            throw "성능테스트 컨테이너가 실행 중이 아닙니다: $container. docker compose -f `"$composePath`" up -d 를 먼저 실행하세요."
        }
    }
}

function Reset-Fixtures {
    & $prepareDbPath `
        -ResetPerformanceDatabase `
        -Scenario All `
        -Container "gudit-performance-postgres" `
        -Database "gudit" `
        -DatabaseUser "postgres" `
        -RedisContainer "gudit-performance-redis"
    if ($LASTEXITCODE -ne 0) { throw "성능테스트 데이터 초기화에 실패했습니다." }
}

function Test-PortOpen {
    param([int]$TargetPort)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync("127.0.0.1", $TargetPort)
        return $task.Wait(250) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForApplication {
    param([int]$TargetPort, [object]$HostProcess)
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -ne $HostProcess -and $HostProcess.HasExited) {
            throw "Spring 애플리케이션이 준비되기 전에 종료되었습니다. 애플리케이션 로그를 확인하세요."
        }
        if (Test-PortOpen -TargetPort $TargetPort) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Spring 애플리케이션이 ${StartupTimeoutSeconds}초 안에 포트 $TargetPort 를 열지 못했습니다."
}

function Resolve-HostJavaExecutable {
    $settings = @(& java -XshowSettings:properties -version 2>&1)
    $javaHomeLine = $settings |
        Where-Object { "$_" -match "^\s*java\.home\s*=" } |
        Select-Object -First 1
    if ($null -eq $javaHomeLine) {
        throw "java.home을 확인할 수 없습니다."
    }

    $javaHome = (("$javaHomeLine" -split "=", 2)[1]).Trim()
    $executable = Join-Path $javaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
        throw "실제 JDK java.exe를 찾을 수 없습니다: $executable"
    }
    return $executable
}

function Get-ApplicationArguments {
    param([object]$Case, [bool]$DockerApplication)
    $applicationPort = if ($DockerApplication) { 8080 } else { $Port }
    $arguments = @(
        "--spring.profiles.active=performance",
        "--server.port=$applicationPort",
        "--server.tomcat.threads.max=$($Case.tomcat_max_threads)",
        "--server.tomcat.threads.min-spare=$($Case.tomcat_min_spare)",
        "--server.tomcat.max-connections=$($Case.tomcat_max_connections)",
        "--server.tomcat.accept-count=$($Case.tomcat_accept_count)",
        "--spring.datasource.hikari.maximum-pool-size=$($Case.hikari_max_pool_size)",
        "--spring.datasource.hikari.minimum-idle=$($Case.hikari_min_idle)",
        "--spring.datasource.hikari.connection-timeout=$($Case.hikari_connection_timeout_ms)",
        "--gudit.inventory.mode=$($Case.inventory_mode)"
    )
    if ($DockerApplication) {
        $arguments += @(
            "--spring.datasource.url=jdbc:postgresql://postgres-performance:5432/gudit",
            "--spring.data.redis.host=redis-performance",
            "--spring.data.redis.port=6379"
        )
    }
    return $arguments
}

function Start-HostApplication {
    param([object]$Case, [string]$JarPath, [string]$LogDirectory, [string]$JwtSecret)
    if (Test-PortOpen -TargetPort $Port) {
        throw "포트 $Port 가 이미 사용 중입니다. IntelliJ의 기존 Spring 실행을 먼저 종료하세요."
    }

    $stdoutPath = Join-Path $LogDirectory "application.stdout.log"
    $stderrPath = Join-Path $LogDirectory "application.stderr.log"
    # Start-Process joins ArgumentList into one Windows command line. Quote the jar path
    # explicitly because this repository path contains spaces.
    $arguments = @("-jar", "`"$JarPath`"") +
        (Get-ApplicationArguments -Case $Case -DockerApplication $false)
    $previousSecret = [Environment]::GetEnvironmentVariable("PERFORMANCE_JWT_SECRET_KEY", "Process")
    try {
        [Environment]::SetEnvironmentVariable("PERFORMANCE_JWT_SECRET_KEY", $JwtSecret, "Process")
        return Start-Process `
            -FilePath $script:hostJavaExecutable `
            -ArgumentList $arguments `
            -WorkingDirectory $repositoryRoot `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -WindowStyle Hidden `
            -PassThru
    } finally {
        [Environment]::SetEnvironmentVariable("PERFORMANCE_JWT_SECRET_KEY", $previousSecret, "Process")
    }
}

function Start-DockerApplication {
    param(
        [object]$Case,
        [string]$JarPath,
        [string]$ContainerName,
        [string]$JwtSecret
    )
    if (Test-PortOpen -TargetPort $Port) {
        throw "포트 $Port 가 이미 사용 중입니다. IntelliJ의 기존 Spring 실행을 먼저 종료하세요."
    }

    $existing = & docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}"
    if (-not [string]::IsNullOrWhiteSpace(($existing -join ""))) {
        throw "같은 이름의 Docker 컨테이너가 이미 존재합니다: $ContainerName"
    }

    $arguments = @(
        "run", "--detach",
        "--name", $ContainerName,
        "--network", $DockerNetwork,
        "--publish", "${Port}:8080",
        "--env-file", $envFile,
        "--env", "PERFORMANCE_JWT_SECRET_KEY=$JwtSecret",
        "--volume", "${JarPath}:/app/app.jar:ro",
        $JavaDockerImage,
        "java", "-jar", "/app/app.jar"
    ) + (Get-ApplicationArguments -Case $Case -DockerApplication $true)

    & docker @arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Docker Spring 애플리케이션 시작에 실패했습니다." }
}

function Stop-ManagedApplication {
    param(
        [object]$HostProcess,
        [string]$ContainerName,
        [bool]$DockerStarted,
        [string]$LogDirectory
    )
    if ($null -ne $HostProcess -and -not $HostProcess.HasExited) {
        Stop-Process -Id $HostProcess.Id -Force
        $HostProcess.WaitForExit(10000) | Out-Null
    }
    if ($DockerStarted) {
        & docker logs $ContainerName 2>&1 |
            Set-Content -LiteralPath (Join-Path $LogDirectory "application.docker.log") -Encoding utf8
        & docker rm --force $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "실험용 애플리케이션 컨테이너 정리에 실패했습니다: $ContainerName"
        }
    }

    $closeDeadline = (Get-Date).AddSeconds(15)
    while ((Get-Date) -lt $closeDeadline -and (Test-PortOpen -TargetPort $Port)) {
        Start-Sleep -Milliseconds 250
    }
    if (Test-PortOpen -TargetPort $Port) {
        throw "관리형 Spring 종료 후에도 포트 $Port listener가 남아 있습니다."
    }
}

function Get-K6BaseUrl {
    param([object]$Case, [string]$ContainerName)
    if ($Case.k6_environment -eq "docker" -and $Case.application_environment -eq "host") {
        return "http://host.docker.internal:$Port"
    }
    if ($Case.k6_environment -eq "docker" -and $Case.application_environment -eq "docker") {
        return "http://${ContainerName}:8080"
    }
    return "http://localhost:$Port"
}

function Invoke-K6Script {
    param(
        [object]$Case,
        [string]$ScriptPath,
        [string]$BaseUrl,
        [System.Collections.IDictionary]$EnvironmentValues,
        [string]$SummaryPath,
        [string]$ContainerName
    )

    $relativeScriptPath = [System.IO.Path]::GetRelativePath($suiteRoot, $ScriptPath).Replace("\", "/")
    if ($Case.k6_environment -eq "host") {
        $arguments = @("run")
        foreach ($entry in $EnvironmentValues.GetEnumerator()) {
            $arguments += @("-e", "$($entry.Key)=$($entry.Value)")
        }
        if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
            $arguments += @("--summary-export", $SummaryPath)
        }
        $arguments += $ScriptPath
        & k6 @arguments | Out-Host
        return $LASTEXITCODE
    }

    $arguments = @("run", "--rm")
    if ($Case.application_environment -eq "docker") {
        $arguments += @("--network", $DockerNetwork)
    }
    $arguments += @("--volume", "${suiteRoot}:/tests:ro")
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        $resultMount = Split-Path $SummaryPath -Parent
        $arguments += @("--volume", "${resultMount}:/results")
    }
    $arguments += @($K6DockerImage, "run")
    foreach ($entry in $EnvironmentValues.GetEnumerator()) {
        $arguments += @("-e", "$($entry.Key)=$($entry.Value)")
    }
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        $arguments += @("--summary-export", "/results/summary.json")
    }
    $arguments += "/tests/$relativeScriptPath"
    & docker @arguments | Out-Host
    return $LASTEXITCODE
}

function Write-Metadata {
    param([System.Collections.IDictionary]$Metadata, [string]$Path)
    $Metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $Path -Encoding utf8
}

Assert-Infrastructure

$fixture = Get-Content -Raw -LiteralPath $fixturePath | ConvertFrom-Json
$jwtSecret = [string]$fixture.metadata.requiredEnvironment.PERFORMANCE_JWT_SECRET_KEY
if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
    throw "생성 fixture에서 성능테스트 JWT 키를 찾을 수 없습니다."
}

$requiresJar = @($matrix | Where-Object application_environment -in @("host", "docker")).Count -gt 0
$script:hostJavaExecutable = if ($usesHostApplication) {
    Resolve-HostJavaExecutable
} else {
    ""
}
if ($requiresJar -and -not $SkipBuild) {
    Write-Host "실험용 Spring Boot jar를 빌드합니다."
    & (Join-Path $repositoryRoot "gradlew.bat") bootJar
    if ($LASTEXITCODE -ne 0) { throw "Spring Boot jar 빌드에 실패했습니다." }
}

$jar = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "build\libs") -Filter "*.jar" -File |
    Where-Object Name -NotMatch "-plain\.jar$" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($requiresJar -and $null -eq $jar) {
    throw "실행할 Spring Boot jar가 없습니다. -SkipBuild를 제거하고 다시 실행하세요."
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$resultRoot = Join-Path $suiteRoot "results\performance-experiments-$runId"
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$failedRuns = @()

foreach ($case in $matrix) {
    $repeatCount = if ($RepeatCount -gt 0) {
        $RepeatCount
    } else {
        Convert-ToPositiveInteger -Value $case.repeats -FieldName "$($case.case_id).repeats"
    }
    for ($repeat = 1; $repeat -le $repeatCount; $repeat += 1) {
        $caseRunId = "$runId-$($case.case_id)-$repeat"
        $caseDirectory = Join-Path $resultRoot "$($case.case_id)\repeat-$repeat"
        New-Item -ItemType Directory -Path $caseDirectory -Force | Out-Null
        $metadataPath = Join-Path $caseDirectory "case.json"
        $summaryPath = Join-Path $caseDirectory "summary.json"
        $containerName = "gudit-perf-exp-$($case.case_id)-$repeat-$runId" -replace "[^a-zA-Z0-9_.-]", "-"
        $hostProcess = $null
        $dockerStarted = $false
        $metadata = [ordered]@{}
        foreach ($property in $case.PSObject.Properties) {
            $metadata[$property.Name] = $property.Value
        }
        $metadata["repeats"] = $repeatCount
        $metadata["repeat"] = $repeat
        $metadata["run_id"] = $caseRunId
        $metadata["started_at"] = (Get-Date).ToString("o")
        $metadata["status"] = "running"
        $metadata["error"] = ""
        Write-Metadata -Metadata $metadata -Path $metadataPath

        Write-Host "[$($case.experiment_group)] $($case.case_id), repeat $repeat/$repeatCount"
        try {
            # 이전 실행의 상태와 무관하도록 본 테스트 직전에도 항상 초기화한다.
            Reset-Fixtures

            if ($case.application_environment -eq "docker") {
                Start-DockerApplication `
                    -Case $case `
                    -JarPath $jar.FullName `
                    -ContainerName $containerName `
                    -JwtSecret $jwtSecret
                $dockerStarted = $true
            } else {
                $hostProcess = Start-HostApplication `
                    -Case $case `
                    -JarPath $jar.FullName `
                    -LogDirectory $caseDirectory `
                    -JwtSecret $jwtSecret
            }

            Wait-ForApplication -TargetPort $Port -HostProcess $hostProcess
            $baseUrl = Get-K6BaseUrl -Case $case -ContainerName $containerName
            $metadata["base_url"] = $baseUrl

            $preflightEnvironment = [ordered]@{
                BASE_URL = $baseUrl
                PREFLIGHT_WAIT_SECONDS = $PreflightWaitSeconds
            }
            $preflightExit = Invoke-K6Script `
                -Case $case `
                -ScriptPath $preflightPath `
                -BaseUrl $baseUrl `
                -EnvironmentValues $preflightEnvironment `
                -SummaryPath "" `
                -ContainerName $containerName
            if ($preflightExit -ne 0) { throw "preflight가 실패했습니다. exitCode=$preflightExit" }

            $testEnvironment = [ordered]@{
                BASE_URL = $baseUrl
                RUN_ID = $caseRunId
                VUS = $case.vus
                LOAD_MODEL = $case.load_model
                REQUEST_MODE = $case.request_mode
                SPREAD_DURATION_SECONDS = $case.spread_duration_seconds
                RAMP_UP = $case.ramp_up
                HOLD = $case.hold
                RAMP_DOWN = $case.ramp_down
                START_RATE = $case.start_rate
                TARGET_RATE = $case.target_rate
                PRE_ALLOCATED_VUS = $case.pre_allocated_vus
                MAX_VUS = $case.max_vus
                NO_CONNECTION_REUSE = $case.no_connection_reuse
            }
            $k6Exit = Invoke-K6Script `
                -Case $case `
                -ScriptPath $probePath `
                -BaseUrl $baseUrl `
                -EnvironmentValues $testEnvironment `
                -SummaryPath $summaryPath `
                -ContainerName $containerName
            if ($k6Exit -ne 0) { throw "k6 실험이 실패했습니다. exitCode=$k6Exit" }

            $metadata["status"] = "completed"
        } catch {
            $metadata["status"] = "failed"
            $metadata["error"] = $_.Exception.Message
            $failedRuns += "$($case.case_id)/repeat-$repeat"
            Write-Warning "$($case.case_id) repeat $repeat 실패: $($_.Exception.Message)"
        } finally {
            try {
                Stop-ManagedApplication `
                    -HostProcess $hostProcess `
                    -ContainerName $containerName `
                    -DockerStarted $dockerStarted `
                    -LogDirectory $caseDirectory
            } catch {
                $metadata["status"] = "failed"
                $metadata["error"] = ($metadata["error"] + " | application stop: " + $_.Exception.Message).Trim(" ", "|")
                $failedRuns += "$($case.case_id)/repeat-$repeat/stop"
                Write-Warning "애플리케이션 종료 확인 실패: $($_.Exception.Message)"
            }
            try {
                # 오류가 나도 다음 실험과 후속 테스트가 기준 데이터에서 시작하도록 복원한다.
                Reset-Fixtures
            } catch {
                $metadata["status"] = "failed"
                $metadata["error"] = ($metadata["error"] + " | final reset: " + $_.Exception.Message).Trim(" ", "|")
                $failedRuns += "$($case.case_id)/repeat-$repeat/reset"
                Write-Warning "종료 후 데이터 복원 실패: $($_.Exception.Message)"
            }
            $metadata["finished_at"] = (Get-Date).ToString("o")
            Write-Metadata -Metadata $metadata -Path $metadataPath
        }

        if ($CooldownSeconds -gt 0) { Start-Sleep -Seconds $CooldownSeconds }
    }
}

& node $summarizerPath $resultRoot
if ($LASTEXITCODE -ne 0) { throw "실험 결과 요약 생성에 실패했습니다." }

Write-Host "성능 실험 결과: $resultRoot"
if ($failedRuns.Count -gt 0) {
    throw "실패한 실험이 있습니다: $($failedRuns -join ', ')"
}
