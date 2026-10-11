#requires -Version 5.1
<#
.SYNOPSIS
    Stops the Treelearn web release entry point (idempotent).

.DESCRIPTION
    The release entry (start-web.ps1) runs the same loopback-only service as the
    dev entry, so stopping is the same operation: release the 127.0.0.1:8733
    listener. This delegates to stop-backend.ps1 for the port-owner logic. It is
    idempotent: exits 0 when the port is already free.

    ASCII-only on purpose: Windows PowerShell 5.1 decodes a BOM-less .ps1 with
    the system ANSI code page, so non-ASCII text can break parsing.
#>
[CmdletBinding()]
param(
    [int] $Port = 8733
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'stop-backend.ps1') -Port $Port
exit $LASTEXITCODE
