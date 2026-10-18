param([string]$Version)

$ErrorActionPreference = 'Stop'
$desktop = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path (Join-Path $desktop 'dist/NeoSerf/bin/NeoSerf.exe'))) {
  throw 'Run desktop/build.ps1 first: the installer packages desktop/dist/NeoSerf.'
}
if (-not $Version) {
  # Default to the version declared in CMakeLists.txt.
  $match = Select-String -Path (Join-Path $desktop 'CMakeLists.txt') -Pattern 'project\(NeoSerf VERSION ([0-9.]+)'
  $Version = $match.Matches[0].Groups[1].Value
}
$Version = $Version.TrimStart('v')
$iscc = (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source
if (-not $iscc) {
  $iscc = @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $iscc) { throw 'Inno Setup 6 (ISCC.exe) is required: https://jrsoftware.org/isdl.php' }
& $iscc "/DAppVersion=$Version" (Join-Path $PSScriptRoot 'NeoSerf.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer build failed.' }
$setup = Join-Path $desktop "dist/installer/NeoSerf-Setup-$Version-x64.exe"
Write-Host "Ready: $setup"
