param([string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'SVBK\TH06Reference'))
$ErrorActionPreference = 'Stop'
$compiler = Get-Command rustc -ErrorAction SilentlyContinue
if ($compiler) { $rustCompiler = $compiler.Source } else {
    $env:CARGO_HOME = Join-Path $env:LOCALAPPDATA 'SVBK\BuildTools\cargo'
    $env:RUSTUP_HOME = Join-Path $env:LOCALAPPDATA 'SVBK\BuildTools\rustup'
    $rustCompiler = Join-Path $env:CARGO_HOME 'bin\rustc.exe'
}
if (!(Test-Path -LiteralPath $rustCompiler)) { throw 'Install Rust (stable GNU toolchain) to build the original TH06 pattern host.' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$temporaryBinary = Join-Path $OutputDirectory ("th06-host-build-" + [Guid]::NewGuid().ToString('N') + '.exe')
& $rustCompiler --edition=2024 -O -A dead_code (Join-Path $PSScriptRoot 'src\main.rs') -C link-arg=-static -o $temporaryBinary
if ($LASTEXITCODE -ne 0) { throw 'TH06 host build failed' }
$binaryHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporaryBinary).Hash.ToLowerInvariant()
$binaryName = 'th06-host-' + $binaryHash.Substring(0,16) + '.exe'
$binaryPath = Join-Path $OutputDirectory $binaryName
if (Test-Path -LiteralPath $binaryPath) { Remove-Item -LiteralPath $temporaryBinary } else { Move-Item -LiteralPath $temporaryBinary -Destination $binaryPath }
# Publish a new immutable binary; an in-progress encounter may keep the previous version open.
$manifestTemporary = Join-Path $OutputDirectory 'host-path.tmp'
[IO.File]::WriteAllText($manifestTemporary, $binaryName, [Text.Encoding]::ASCII)
Move-Item -LiteralPath $manifestTemporary -Destination (Join-Path $OutputDirectory 'host-path.txt') -Force
Get-FileHash -Algorithm SHA256 -LiteralPath $binaryPath
