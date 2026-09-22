# Evidence capture: current release APK identity + unreachable P0 entry review (2026-09-22).
# Pure ASCII on purpose: AGENTS.md records that PS 5.1 mis-decodes a UTF-8-without-BOM script.
# Every path resolves from this script's own location (PSScriptRoot), never from the caller's cwd.
# Section 7 runs cmdlets in-process instead of nesting another shell: composing a command string and
# handing it to powershell.exe is the quoting trap the rules call out, and it silently ate the paths.
# Exit codes: 0 = every check passed, 1 = a check failed, 3 = INCONCLUSIVE (prerequisite missing).
$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$dev = $PSScriptRoot
$apk = Join-Path $root 'android\app\build\outputs\apk\release\app-release.apk'
$out = Join-Path $root 'artifacts\evidence\T-REV-2026-09-22-apk-and-gap-review.txt'
$script:failed = 0
$script:lastExit = 0

function W([string] $line) { $line | Out-File -LiteralPath $out -Encoding utf8 -Append }

function Check([string] $label, [bool] $ok, [string] $detail) {
    if (-not $ok) { $script:failed = $script:failed + 1 }
    $word = 'FAIL'
    if ($ok) { $word = 'PASS' }
    W ('CHECK ' + $label + ': ' + $word + ' (' + $detail + ')')
}

function Capture([string] $exe, [string[]] $cmdArgs) {
    W ('COMMAND: ' + $exe + ' ' + ($cmdArgs -join ' '))
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $lines = & $exe @cmdArgs 2>&1 | ForEach-Object { [string] $_ }
    $code = $LASTEXITCODE
    $script:lastExit = $code
    $ErrorActionPreference = $prev
    if ($lines) { foreach ($l in $lines) { W $l } } else { W '(no output)' }
    W ('EXITCODE=' + $code)
    W ''
}

if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }

W ('# captured-at: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
W ('# cwd: ' + (Get-Location).Path)
W ('# script: ' + $MyInvocation.MyCommand.Path)
W ('# command: powershell -ExecutionPolicy Bypass -File .trae/scripts/dev/review_apk_and_gaps.ps1')
W ('# repo-root: ' + $root)
W ''

# The Android SDK: environment first, then android/local.properties, exactly like Gradle does.
$sdk = $env:ANDROID_SDK
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
$lp = Join-Path $root 'android\local.properties'
if (-not $sdk -and (Test-Path -LiteralPath $lp)) {
    foreach ($line in Get-Content -LiteralPath $lp) {
        # local.properties escapes the backslash AND the drive-letter colon (its own header says so),
        # so unescape every backslash-escaped character instead of only doubling them.
        if ($line -match '^sdk\.dir\s*=\s*(.+)$') { $sdk = $Matches[1].Trim() -replace '\\(.)', '$1' }
    }
}
$apksigner = Join-Path (Join-Path $sdk 'build-tools\36.1.0') 'apksigner.bat'
$aapt2 = Join-Path (Join-Path $sdk 'build-tools\36.1.0') 'aapt2.exe'
$missing = @()
if (-not $sdk) { $missing += 'android sdk' }
if (-not (Test-Path -LiteralPath $apksigner)) { $missing += $apksigner }
if (-not (Test-Path -LiteralPath $aapt2)) { $missing += $aapt2 }
if (-not (Test-Path -LiteralPath $apk)) { $missing += $apk }
if ($missing.Count -gt 0) {
    foreach ($item in $missing) { W ('FATAL missing prerequisite: ' + $item) }
    Write-Output ('FATAL: missing prerequisite(s): ' + ($missing -join '; '))
    exit 3
}
W ('# android-sdk: ' + $sdk)
W ''

W '== 1. release APK identity =='
W ('COMMAND: Get-Item -LiteralPath ' + $apk)
$fi = Get-Item -LiteralPath $apk
W ('size=' + $fi.Length)
W ('mtime=' + $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))
W 'COMMAND: Get-FileHash -Algorithm SHA256 <apk>'
W ('sha256=' + (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash)
W 'COMMAND: newest mtime under android\app\src\main'
$newest = Get-ChildItem (Join-Path $root 'android\app\src\main') -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
W ('newest-source=' + $newest.FullName.Replace($root + '\', '') + '  mtime=' + $newest.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))
Check 'apk-newer-than-newest-source' ($fi.LastWriteTime -gt $newest.LastWriteTime) ('apk=' + $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') + ' source=' + $newest.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))
W ''

W '== 2. signing =='
Capture $apksigner @('verify', '--print-certs', '--verbose', $apk)

W '== 3. manifest identity =='
Capture $aapt2 @('dump', 'badging', $apk)

W '== 4. which production symbols are actually inside the shipped dex =='
Capture 'python' @('-X', 'utf8', (Join-Path $dev 'review_dex_symbols.py'), $apk)

W '== 5. unreachable P0 entries: who references these presenters at all =='
W 'A presenter referenced only from its own package plus src/test has no screen class, no layout and'
W 'no src/main caller, so no user gesture can reach it. That is the evidence these areas are unbuilt.'
Capture 'python' @('-X', 'utf8', (Join-Path $dev 'review_refs.py'), $root)

W '== 6. does the quoted sentence reach the DeepSeek request =='
# The checker derives YES/NO from the code and exits non-zero when a link is broken, so the verdict
# is an assertion here rather than a paragraph the reader has to take on trust. An earlier revision
# of it printed a hardcoded NO and kept saying so after the chain changed.
Capture 'python' @('-X', 'utf8', (Join-Path $dev 'review_quote_path.py'), $root)
Check 'quote-reaches-prompt' ($script:lastExit -eq 0) ('python-exit=' + $script:lastExit)

W '== 7. dead entries visible in the shipped layouts =='
$homeBody = Join-Path $root 'android\app\src\main\res\layout\screen_home_body.xml'
$homeScreen = Join-Path $root 'android\app\src\main\java\com\treelearn\app\screen\HomeScreen.java'
W ('COMMAND (in-process): Select-String -LiteralPath ' + $homeBody + ' -Pattern enabled="false"')
# The id and the enabled attribute of one view are on different lines, so the association rule has
# to be stated: a view starts at its own android:id line and the attribute belongs to the nearest
# preceding id - which is how this hand-written layout reads top to bottom.
$bodyLines = Get-Content -LiteralPath $homeBody
$currentId = '(no-id-yet)'
$disabled = @()
$lineNumber = 0
foreach ($line in $bodyLines) {
    $lineNumber = $lineNumber + 1
    if ($line -match '@\+id/([A-Za-z0-9_]+)') { $currentId = $Matches[1] }
    if ($line -match 'enabled="false"') {
        $disabled += [pscustomobject]@{ Id = $currentId; Line = $lineNumber; Text = $line.Trim() }
    }
}
foreach ($hit in $disabled) { W ($hit.Line.ToString() + ': ' + $hit.Text + '   [id=' + $hit.Id + ']') }
W ('matches=' + $disabled.Count)
Check 'dead-buttons-exist' ($disabled.Count -gt 0) ('matches=' + $disabled.Count)
$disabledIds = @()
foreach ($hit in $disabled) { $disabledIds += $hit.Id }
W ('disabled-ids=' + ($disabledIds -join ','))
Check 'dead-buttons-are-search-and-archived' (($disabledIds -contains 'home_search') -and ($disabledIds -contains 'home_archived')) ('ids=' + ($disabledIds -join ','))
W ''
W 'NOTE: home_send carries the same layout default (enabled="false") but is NOT a dead entry, so the'
W 'layout default alone proves nothing - what separates the two is whether runtime code drives it.'
W ('COMMAND (in-process): Select-String -LiteralPath ' + $homeScreen + ' -Pattern send\\.setEnabled')
$sendWiring = Select-String -LiteralPath $homeScreen -Pattern 'send\.setEnabled'
foreach ($hit in $sendWiring) { W ($hit.LineNumber.ToString() + ': ' + $hit.Line.Trim()) }
W ('matches=' + $sendWiring.Count)
Check 'the-composer-button-is-driven-at-runtime' ($sendWiring.Count -gt 0) ('matches=' + $sendWiring.Count)
W ''
W ('COMMAND (in-process): Select-String -LiteralPath ' + $homeScreen + ' -Pattern home_search|home_archived')
$listeners = Select-String -LiteralPath $homeScreen -Pattern 'home_search|home_archived'
foreach ($hit in $listeners) { W ($hit.LineNumber.ToString() + ': ' + $hit.Line.Trim()) }
W ('matches=' + $listeners.Count)
Check 'no-listener-registered' ($listeners.Count -eq 0) ('matches=' + $listeners.Count)
W ''
W 'COMMAND (in-process): Select-String -Path <src\main\**\*.java> -Pattern home_entry_pending|chat_children_pending'
$toasts = Get-ChildItem -LiteralPath (Join-Path $root 'android\app\src\main\java') -Recurse -Filter *.java | Select-String -Pattern 'home_entry_pending|chat_children_pending'
foreach ($hit in $toasts) { W ($hit.Filename + ':' + $hit.LineNumber.ToString() + ': ' + $hit.Line.Trim()) }
W ('matches=' + $toasts.Count)
Check 'toast-placeholders-exist' ($toasts.Count -gt 0) ('matches=' + $toasts.Count)
W ''

W '== 8. how to read this file =='
W 'Section 1 is the identity of the exact APK the user installs (byte count, SHA-256, mtime).'
W 'Sections 4-7 are the mechanical half of the review: what shipped, what is dead, and what the'
W 'request does or does not carry to the model. Every CHECK line above is an assertion, not a note.'
W 'The prose conclusions are in the delivery report under Docs/06-delivery/.'
W ''

if ($script:failed -gt 0) {
    W ('REVIEW-VERDICT: FAIL (' + $script:failed + ' check(s) failed)')
    Write-Output ('REVIEW-VERDICT: FAIL (' + $script:failed + ' check(s) failed)')
    exit 1
}
W 'REVIEW-VERDICT: PASS (every check above passed)'
Write-Output 'REVIEW-VERDICT: PASS'
exit 0
