param([switch]$Benchmark, [switch]$Playtest, [switch]$Original)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $env:TEMP 'svbk-danmaku-tests'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$sources = @((Get-ChildItem -LiteralPath (Join-Path $projectDir 'java') -Filter '*.java').FullName) +
    @((Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java').FullName)
& javac -encoding UTF-8 -Xlint:all -d $buildDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
foreach ($test in @('GameplayTest', 'BossReworkTest', 'GameMenuTest', 'BossPracticeTest', 'EosdPatternTest', 'Th06DataTest', 'PatternSurvivalTest', 'BossPreviewTest')) {
    & java '-Djava.awt.headless=true' '-Dsvbk.legacyBosses=true' -cp $buildDir $test $buildDir
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
if ($Benchmark) {
    & java '-Djava.awt.headless=true' -cp $buildDir PerformanceTest $buildDir
    if ($LASTEXITCODE -ne 0) { throw 'Performance target failed' }
}
if ($Playtest) {
    & java '-Djava.awt.headless=true' -cp $buildDir PlaythroughTest $buildDir
    if ($LASTEXITCODE -ne 0) { throw 'Playthrough test failed' }
}
if ($Original) {
    & (Join-Path $projectDir 'native\th06-host\build.ps1')
    foreach ($test in @('Th06HealthTest', 'Th06IntegrationTest', 'Th06FullMovesetTest', 'Th06BehaviorTest')) {
        & java '-Djava.awt.headless=true' -cp $buildDir $test $buildDir
        if ($LASTEXITCODE -ne 0) { throw "$test failed" }
    }
    if ($Benchmark) {
        & java '-Djava.awt.headless=true' -cp $buildDir Th06PerformanceTest $buildDir
        if ($LASTEXITCODE -ne 0) { throw 'Native encounter frame budget failed' }
    }
}
Write-Output "Reports and renders: $buildDir"
