param(
    [switch]$ResetPerformanceDatabase,
    [int]$RepeatCount = 5,
    [int]$Port = 8080,
    [int]$StartupTimeoutSeconds = 120,
    [int]$PreflightWaitSeconds = 90,
    [int]$CooldownSeconds = 10,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

if (-not $ResetPerformanceDatabase) {
    throw "격리된 성능테스트 DB인지 확인한 뒤 -ResetPerformanceDatabase를 지정하세요."
}
if ($RepeatCount -ne 5) {
    throw "accept-count 비교 실험은 각 조건을 정확히 5회 반복해야 합니다. RepeatCount=5로 실행하세요."
}

$runner = Join-Path $PSScriptRoot "run-performance-experiments.ps1"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw "실험 실행기를 찾을 수 없습니다: $runner"
}

$arguments = @{
    Group = "tomcat-accept"
    RepeatCount = $RepeatCount
    Port = $Port
    StartupTimeoutSeconds = $StartupTimeoutSeconds
    PreflightWaitSeconds = $PreflightWaitSeconds
    CooldownSeconds = $CooldownSeconds
    ResetPerformanceDatabase = $true
}
if ($SkipBuild) {
    $arguments["SkipBuild"] = $true
}

& $runner @arguments
