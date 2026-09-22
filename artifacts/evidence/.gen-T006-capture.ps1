# T-006 evidence capture helper (throwaway, ASCII-only).
# Writes: header (timestamp, command, mode), streamed line-by-line output, then EXITCODE.
# Never uses Out-String (it re-wraps at the host width and truncates long proof lines) and never
# uses ">>" (which writes UTF-16 in PowerShell 5.1).
param(
    [Parameter(Mandatory = $true)][string]$EvidenceFile,
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$Mode,
    [Parameter(Mandatory = $true)][string]$CommandText,
    [Parameter(Mandatory = $true)][string[]]$GradleArgs
)

$ErrorActionPreference = 'Continue'

if (Test-Path -LiteralPath $EvidenceFile) {
    Remove-Item -LiteralPath $EvidenceFile -Force
}

$timestamp = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
"# $Title" | Out-File -LiteralPath $EvidenceFile -Encoding utf8
"CapturedAt: $timestamp" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
"Command: $CommandText" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
"Mode: $Mode" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
"--- output ---" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append

Push-Location (Join-Path $PSScriptRoot '..\..\android')
& .\gradlew.bat @GradleArgs 2>&1 | ForEach-Object { [string]$_ } | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
$code = $LASTEXITCODE
Pop-Location

"--- end of output ---" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
"EXITCODE=$code" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
Write-Output "EXITCODE=$code"