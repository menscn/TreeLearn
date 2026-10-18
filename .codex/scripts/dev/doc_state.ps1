# Evidence: the state of the delivery report itself after the 2026-09-22 refresh.
# Pure ASCII. Records the file's SHA-256, mtime, line count and the exact claim lines whose wording
# changed, so a later reader can tell what this refresh did without trusting the prose.
# Exit codes: 0 = all expected claims present, 1 = a claim is missing, 3 = the report is missing.
$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$report = Join-Path $root 'Docs\06-delivery\TreeLearn_V1_交付报告.md'
$out = Join-Path $root 'artifacts\evidence\T-033-doc-refresh-2026-09-22-round2.txt'

function W([string] $line) { $line | Out-File -LiteralPath $out -Encoding utf8 -Append }

if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }
if (-not (Test-Path -LiteralPath $report)) {
    Write-Output ('FATAL: no report at ' + $report)
    exit 3
}
# Get-FileHash is a cmdlet, so it has no exit code: printing "EXITCODE=" here would write an empty
# value into an evidence file, which is the exact shape the rules forbid. Assert the shape instead.
$hash = (Get-FileHash -LiteralPath $report -Algorithm SHA256).Hash

W ('# captured-at: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
W ('# cwd: ' + (Get-Location).Path)
W ('# script: ' + $MyInvocation.MyCommand.Path)
W ('# command: powershell -ExecutionPolicy Bypass -File .codex/scripts/dev/doc_state.ps1')
W ('# report: ' + $report)
W ''
W 'COMMAND: Get-FileHash -Algorithm SHA256 <report>'
W ('sha256=' + $hash)
if ($hash -match '^[0-9A-F]{64}$') {
    W 'CHECK hash-well-formed: PASS (64 uppercase hex chars)'
} else {
    W ('CHECK hash-well-formed: FAIL (' + $hash + ')')
    Write-Output 'FATAL: Get-FileHash returned something that is not a SHA-256'
    exit 3
}
$fi = Get-Item -LiteralPath $report
W ('mtime=' + $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))
W ('bytes=' + $fi.Length)
W ('lines=' + (Get-Content -LiteralPath $report).Count)
W ''

$claims = @(
    '994C70259577DBFE7DD3D68ABEA1B9CFB369D666AA06D9B9010242434C4EA592',
    '2.1 这份 APK 装机后能用与不能用',
    '5.1 引用失效',
    'T-038 树画布生产装配',
    '后续步骤（建议顺序，2026-09-22 重写）',
    'REVIEW-VERDICT: PASS'
)
$missing = 0
W 'COMMAND (in-process): Select-String for each expected claim'
foreach ($claim in $claims) {
    $hits = Select-String -LiteralPath $report -Pattern ([regex]::Escape($claim))
    if ($hits.Count -gt 0) {
        W ('CLAIM ' + $claim + ': present (' + $hits.Count + ' line(s), first at line ' + $hits[0].LineNumber + ')')
    } else {
        W ('CLAIM ' + $claim + ': MISSING')
        $missing = $missing + 1
    }
}
W ''
W 'COMMAND (in-process): the stale fingerprint and the stale risk sentence must be gone'
$stale = @('EBDE40769D92C355B5527E2B5801FD7C5989CB77BB2617A005EA74D505EB0DAA', '即使装到设备上，首页与对话仍会显示')
foreach ($text in $stale) {
    $hits = Select-String -LiteralPath $report -Pattern ([regex]::Escape($text))
    if ($hits.Count -gt 0) {
        W ('STALE ' + $text + ': still present at line ' + $hits[0].LineNumber + ' -- FAIL')
        $missing = $missing + 1
    } else {
        W ('STALE ' + $text + ': gone')
    }
}
W ''
if ($missing -gt 0) {
    W ('DOC-VERDICT: FAIL (' + $missing + ' problem(s))')
    Write-Output ('DOC-VERDICT: FAIL (' + $missing + ' problem(s))')
    exit 1
}
W 'DOC-VERDICT: PASS'
Write-Output 'DOC-VERDICT: PASS'
exit 0
