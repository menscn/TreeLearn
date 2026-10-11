#requires -Version 5.1
<#
.SYNOPSIS
    Starts the Treelearn web backend (development entry point).

.DESCRIPTION
    Runs the Spring Boot service from web/backend with the Gradle wrapper.
    The service binds 127.0.0.1:8733 only (see application.properties).

    Keep this window open; Ctrl+C stops the service. Alternatively use
    web/scripts/stop-backend.ps1 from another window to stop the listener
    process by port.

    ASCII-only on purpose: Windows PowerShell 5.1 decodes a BOM-less .ps1 with
    the system ANSI code page, so non-ASCII text can break parsing.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot '..\backend'
Write-Host "Starting Treelearn web backend from $backend (loopback 127.0.0.1:8733)"
Push-Location $backend
try {
    & .\gradlew.bat bootRun --console=plain
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}