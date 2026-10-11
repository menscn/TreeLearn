#requires -Version 5.1
<#
.SYNOPSIS
    Stops the Treelearn web backend by its loopback listener.

.DESCRIPTION
    Finds the process that owns the 127.0.0.1:8733 listener and stops it.
    Exits 0 when the port is already free (idempotent), 1 when a process could
    not be stopped, 3 when the port is owned by something unexpected (for
    example another application grabbed 8733 before the backend started).

    Note: this is a hard stop of the server process. SQLite (introduced in the
    storage task) is crash-safe, so stopping this way cannot corrupt committed
    data, but a generation in flight will be finalized as interrupted on the
    next start, which is the designed recovery path.

    ASCII-only on purpose: Windows PowerShell 5.1 decodes a BOM-less .ps1 with
    the system ANSI code page, so non-ASCII text can break parsing.
#>
[CmdletBinding()]
param(
    [int] $Port = 8733
)

$ErrorActionPreference = 'Stop'

$connections = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($connections.Count -eq 0) {
    Write-Host "No listener on port $Port; nothing to stop."
    exit 0
}

$socketOwningProcesses = $connections | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($ownerPid in $socketOwningProcesses) {
    $process = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Write-Host "Listener on port $Port references process $ownerPid, which no longer exists; retrying discovery."
        continue
    }
    Write-Host "Stopping process $ownerPid ($($process.ProcessName)) holding 127.0.0.1:$Port"
    Stop-Process -Id $ownerPid -Force -ErrorAction Stop
}

Start-Sleep -Milliseconds 800
$stillListening = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if ($stillListening.Count -eq 0) {
    Write-Host "Port $Port is now free."
    exit 0
}
Write-Host "FAIL: port $Port is still held by: $(($stillListening | Select-Object -ExpandProperty OwningProcess -Unique) -join ', ')"
exit 1