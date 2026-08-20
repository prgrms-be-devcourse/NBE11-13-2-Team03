# 이 파일은 Windows 시스템 PATH를 변경하지 않는다.
# 현재 PowerShell 세션에서 사용할 성능테스트 경로 변수만 준비한다.
#
# 변수들을 현재 터미널에 유지하려면 호출 연산자(&)가 아니라
# 점 표기법(dot-sourcing)으로 실행해야 한다.
#
# 예:
# . ".\performance-tests\scripts\set-performance-paths.ps1"

$ErrorActionPreference = "Stop"

# 이 스크립트가 들어 있는 scripts 폴더의 상위 폴더가 performance-tests 루트다.
# $PSScriptRoot를 사용하므로 performance-tests 폴더를 다른 위치로 옮겨도 동작한다.
$performanceRoot = Split-Path $PSScriptRoot -Parent

# performance-tests 루트를 기준으로 자주 사용하는 파일 경로를 만든다.
$prepareDbPath = Join-Path `
    $performanceRoot `
    "test-data\prepare-db.ps1"

$preflightPath = Join-Path `
    $performanceRoot `
    "k6\preflight.js"

$concurrencyRunnerPath = Join-Path `
    $performanceRoot `
    "scripts\run-concurrency-suite.ps1"

$loadRunnerPath = Join-Path `
    $performanceRoot `
    "scripts\run-load-suite.ps1"

$generatedDataPath = Join-Path `
    $performanceRoot `
    "test-data\generated\performance-test-data.json"

# 필수 파일이 모두 존재하는지 검사한다.
# 하나라도 없으면 잘못된 폴더 구조이므로 명확한 오류와 함께 중단한다.
$requiredPaths = @(
    $prepareDbPath,
    $preflightPath,
    $concurrencyRunnerPath,
    $loadRunnerPath,
    $generatedDataPath
)

foreach ($requiredPath in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "필수 성능테스트 파일을 찾을 수 없습니다: $requiredPath"
    }
}

# 기존 명령과 호환되도록 prepare-db.ps1의 FileInfo 객체도 제공한다.
$prepareDbScript = Get-Item -LiteralPath $prepareDbPath

# 경로들을 한 객체로도 사용할 수 있게 묶는다.
$performancePaths = [PSCustomObject]@{
    Root              = $performanceRoot
    PrepareDb         = $prepareDbPath
    Preflight         = $preflightPath
    ConcurrencyRunner = $concurrencyRunnerPath
    LoadRunner        = $loadRunnerPath
    GeneratedData     = $generatedDataPath
}

# 현재 세션에 준비된 경로를 출력한다.
Write-Host "성능테스트 경로 변수가 준비되었습니다." -ForegroundColor Green
$performancePaths | Format-List

Write-Host "사용 가능한 변수:" -ForegroundColor Cyan
Write-Host '  $performanceRoot'
Write-Host '  $prepareDbPath'
Write-Host '  $prepareDbScript'
Write-Host '  $preflightPath'
Write-Host '  $concurrencyRunnerPath'
Write-Host '  $loadRunnerPath'
Write-Host '  $generatedDataPath'
Write-Host '  $performancePaths'
