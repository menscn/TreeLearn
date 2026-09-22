# T-006 evidence capture helper for non-Gradle commands (throwaway, ASCII-only).
# Same rules as the Gradle variant: streamed line-by-line capture, no Out-String, no ">>".
param(
    [Parameter(Mandatory = $true)][string]$EvidenceFile,
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$Mode,
    [Parameter(Mandatory = $true)][string]$CommandText,
    [Parameter(Mandatory = $true)][string]$Program,
    [Parameter(Mandatory = $true)][string[]]$ProgramArgs,
    [Parameter(Mandatory = $true)][string]$WorkingDirectory
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

Push-Location $WorkingDirectory
& $Program @ProgramArgs 2>&1 | ForEach-Object { [string]$_ } | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
$code = $LASTEXITCODE
Pop-Location

"--- end of output ---" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
"EXITCODE=$code" | Out-File -LiteralPath $EvidenceFile -Encoding utf8 -Append
Write-Output "EXITCODE=$code"