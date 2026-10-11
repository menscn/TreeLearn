#requires -Version 5.1
<#
.SYNOPSIS
    Starts the Treelearn web release entry point with a single command.

.DESCRIPTION
    Release entry (task T-2026-10-10-029). Unlike the dev entry
    (start-backend.ps1, which runs Gradle bootRun for the Vite dev server to
    proxy), this builds the backend bootJar and launches it as a detached
    process that serves the frontend build (web/frontend/dist) same-origin on
    http://127.0.0.1:8733. It waits for /api/health/ready, then prints the
    access URL and the data directory. Stop it with web/scripts/stop-web.ps1.

    -Build first runs `pnpm build` in web/frontend so the served dist is fresh.
    When the dist is missing the service still starts and '/' shows a readable
    hint instead of failing.

    ASCII-only on purpose: Windows PowerShell 5.1 decodes a BOM-less .ps1 with
    the system ANSI code page, so non-ASCII text can break parsing.
#>
[CmdletBinding()]
param(
    [switch] $Build,
    [int] $Port = 8733,
    [int] $ReadyTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backend = Join-Path $repoRoot 'web\backend'
$frontend = Join-Path $repoRoot 'web\frontend'
$distDir = Join-Path $frontend 'dist'

if ($Build) {
    Write-Host "Building frontend (pnpm build) in $frontend ..."
    Push-Location $frontend
    try { & pnpm build } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Host "FAIL: pnpm build exited with $LASTEXITCODE"; exit 1 }
}

if (-not (Test-Path (Join-Path $distDir 'index.html'))) {
    Write-Warning "frontend dist not found at $distDir; '/' will show a build-missing hint. Re-run with -Build to build it."
}

$listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($listeners.Count -gt 0) {
    $owners = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
    Write-Host "FAIL: port $Port is already in use by process(es) $owners."
    Write-Host "Stop the current service first: powershell -File web/scripts/stop-web.ps1"
    exit 2
}

Write-Host "Building backend bootJar ..."
Push-Location $backend
try { & .\gradlew.bat bootJar --console=plain } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL: gradlew bootJar exited with $LASTEXITCODE"; exit 1 }

$jar = Get-ChildItem (Join-Path $backend 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*-plain.jar' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $jar) { Write-Host "FAIL: no bootJar found under $backend\build\libs"; exit 1 }

$java = 'java'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
}

$dataDir = if ($env:TREELEARN_DATA_DIR) { $env:TREELEARN_DATA_DIR }
elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'TreeLearn-Web' }
else { Join-Path $env:USERPROFILE 'AppData\Local\TreeLearn-Web' }
New-Item -ItemType Directory -Force -Path (Join-Path $dataDir 'logs') | Out-Null
$stdout = Join-Path $dataDir 'logs\web-stdout.log'
$stderr = Join-Path $dataDir 'logs\web-stderr.log'

Write-Host "Starting TreeLearn web service (jar=$($jar.Name), frontend-dist=$distDir) ..."
$service = Start-Process -FilePath $java `
    -ArgumentList @('-jar', $jar.FullName, "--treelearn.frontend-dist=$distDir") `
    -WorkingDirectory $backend -PassThru `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

$readyUrl = "http://127.0.0.1:$Port/api/health/ready"
$deadline = (Get-Date).AddSeconds($ReadyTimeoutSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    if ($service.HasExited) { break }
    $response = $null
    try { $response = Invoke-WebRequest -Uri $readyUrl -UseBasicParsing -TimeoutSec 2 } catch { }
    if ($response -and $response.StatusCode -eq 200) { $ready = $true; break }
    Start-Sleep -Milliseconds 500
}

if (-not $ready) {
    Write-Host "FAIL: service did not become ready within $ReadyTimeoutSeconds s."
    if (Test-Path $stderr) { Write-Host "---- last stderr ----"; Get-Content $stderr -Tail 20 }
    if (-not $service.HasExited) { Stop-Process -Id $service.Id -Force }
    exit 1
}

Write-Host ""
Write-Host "TreeLearn web is ready."
Write-Host "  Open in browser : http://127.0.0.1:$Port"
Write-Host "  Data directory  : $dataDir"
Write-Host "  Stop with       : powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/stop-web.ps1"
exit 0
