param([string]$QtRoot = $env:QT_ROOT_DIR)

$ErrorActionPreference = 'Stop'
if (-not $QtRoot -or -not (Test-Path (Join-Path $QtRoot 'bin/qtpaths6.exe'))) {
  throw 'Pass -QtRoot with the MSVC x64 Qt folder, for example C:\Qt\6.10.3\msvc2022_64.'
}
$QtRoot = (Resolve-Path -LiteralPath $QtRoot).Path
if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
  $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
  $installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
  if (-not $installation) { throw 'Install Visual Studio C++ desktop build tools and a Windows SDK.' }
  $devCmd = Join-Path $installation 'Common7/Tools/VsDevCmd.bat'
  $lines = & $env:ComSpec /d /s /c "call `"$devCmd`" -arch=x64 >nul && set"
  if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the MSVC build environment.' }
  foreach ($line in $lines) {
    $separator = $line.IndexOf('=')
    if ($separator -gt 0) { [Environment]::SetEnvironmentVariable($line.Substring(0, $separator), $line.Substring($separator + 1), 'Process') }
  }
}
$env:PATH = (Join-Path $QtRoot 'bin') + ';' + $env:PATH
$buildDir = Join-Path $PSScriptRoot 'build-release'
$packageDir = Join-Path $PSScriptRoot 'dist/NeoSerf'
cmake -S $PSScriptRoot -B $buildDir -G Ninja -DCMAKE_BUILD_TYPE=Release "-DCMAKE_PREFIX_PATH=$QtRoot" -DBUILD_TESTING=ON
if ($LASTEXITCODE -ne 0) { throw 'CMake configuration failed.' }
cmake --build $buildDir --parallel 4
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
ctest --test-dir $buildDir --output-on-failure
if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
cmake --install $buildDir --prefix $packageDir
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
if ($env:VCToolsRedistDir) {
  $crt = Get-ChildItem (Join-Path $env:VCToolsRedistDir 'x64') -Directory -Filter 'Microsoft.VC*.CRT' | Select-Object -First 1
  if ($crt) {
    Copy-Item -Path (Join-Path $crt.FullName '*.dll') -Destination (Join-Path $packageDir 'bin')
  }
}
Write-Host "Ready: $packageDir\bin\NeoSerf.exe"
