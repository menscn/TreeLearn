#requires -Version 5.1
<#
    Generator for T-007's RED evidence (artifacts/evidence/T-007-red.txt).

    RED here is compile-level, and that is stated rather than dressed up: the acceptance suite is
    written before any production type it names exists, so javac stops on those names. It proves the
    capability is absent; it does not claim "a behaviour was observed and then fixed". GREEN uses a
    command that is character-for-character identical.

    House rules honoured on purpose (see the failure library in AGENTS.md):
      * child output is captured line by line and streamed with Out-File -Encoding utf8 -Append, never
        with >> (UTF-16LE) and never through Out-String (host-width re-wrapping truncates proof lines);
      * native stderr is captured with $ErrorActionPreference = Continue and $LASTEXITCODE only, so a
        Gradle warning cannot be promoted into a terminating NativeCommandError;
      * every number in the conclusion block is derived from the capture by this script, counted on
        top-level diagnostics only. Gradle echoes javac's diagnostics twice (inline and again under its
        FAILURE block); counting both would publish 2x numbers whose sum exceeds javac's own total --
        exactly the "parts must add up to the whole" defect recorded in the failure library;
      * the parts are asserted to sum to javac's reported total, and the written file is read back and
        scanned for U+FFFD before this script reports success.

    Run from the repository root:
      powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-t007-red.ps1
#>
$ErrorActionPreference = 'Continue'

$root = 'D:\Project\Android\Treelearn'
$out  = Join-Path $root 'artifacts\evidence\T-007-red.txt'
$mode = 'RED (this suite is written before the production types it names exist)'
$command = 'cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest --no-daemon --console=plain'
$started = (Get-Date).ToString('o')

@(
    '# T-007 RED evidence - immutable birth snapshot + later-context encoding (:core / :ai)'
    "# captured-at: $started"
    "# command: $command"
    "# mode: $mode"
    ''
) | Out-File -LiteralPath $out -Encoding utf8

Push-Location (Join-Path $root 'android')
try {
    & .\gradlew.bat ':core:test' ':ai:testDebugUnitTest' --no-daemon --console=plain 2>&1 |
        ForEach-Object { [string] $_ } |
        Out-File -LiteralPath $out -Encoding utf8 -Append
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}

"EXITCODE=$exit" | Out-File -LiteralPath $out -Encoding utf8 -Append

$captured = @(Get-Content -LiteralPath $out -Encoding UTF8)
$joined = $captured -join "`n"

# Top-level diagnostics only: a line that starts at a drive letter, names the java file and line, then
# carries the javac diagnostic. Gradle's re-indented echo of the same lines does not match.
$diagnostics = @($captured | Where-Object { $_ -match '^[A-Z]:[^:]+\.java:\d+: \u9519\u8bef: ' })
$reportedMatch = [regex]::Match($joined, '(\d+) \u4e2a\u9519\u8bef')
$reported = if ($reportedMatch.Success) { [int] $reportedMatch.Groups[1].Value } else { -1 }
$unresolved = @($diagnostics | Where-Object { $_ -match '\u627e\u4e0d\u5230\u7b26\u53f7' }).Count
$missingPackage = @($diagnostics | Where-Object { $_ -match '\u7a0b\u5e8f\u5305.+\u4e0d\u5b58\u5728' }).Count
$otherDiagnostics = $diagnostics.Count - $unresolved - $missingPackage
$failedTasks = @($captured | Where-Object { $_ -match '^> Task .* FAILED' })
$files = @($diagnostics | ForEach-Object { [regex]::Match($_, '^[A-Z]:[^:]+\.java').Value } |
    Sort-Object -Unique)
$replacementChars = @($captured | Where-Object { $_.Contains([char] 0xFFFD) }).Count

@(
    ''
    '=== conclusion (derived by this script from the capture above; no number is typed by hand) ==='
    "javac reported error total        : $reported"
    "top-level diagnostic lines        : $($diagnostics.Count)"
    "'cannot find symbol'              : $unresolved"
    "'package ... does not exist'      : $missingPackage"
    "other diagnostics                 : $otherDiagnostics"
    "sum of the four parts             : $($unresolved + $missingPackage + $otherDiagnostics)"
    "failed gradle tasks               : $($failedTasks.Count)"
    "java files named in diagnostics   : $($files.Count)"
    'U+FFFD replacement characters     : ' + $replacementChars
    ''
    'commands that reproduce the numbers above:'
    "  Select-String -Path '$out' -Pattern '^[A-Z]:[^:]+\.java:\d+: 错误: ' -Encoding UTF8 | Measure-Object"
    "  Select-String -Path '$out' -Pattern '(\d+) 个错误' -Encoding UTF8 | Select-Object -First 1"
    "  Select-String -Path '$out' -Pattern '^> Task .* FAILED' -Encoding UTF8"
) | Out-File -LiteralPath $out -Encoding utf8 -Append

$failedTasks | ForEach-Object { Write-Host $_ }
Write-Host "wrote $out (EXITCODE=$exit, javac reported $reported, diagnostics=$($diagnostics.Count), unresolved=$unresolved, missingPackage=$missingPackage, other=$otherDiagnostics, files=$($files.Count))"
if ($replacementChars -ne 0) { throw "encoding guard failed: $replacementChars replacement character(s) in $out" }
if ($exit -eq 0) { throw 'this was supposed to be a failing run but the exit code was 0' }
if ($reported -ne $diagnostics.Count) { throw "derivation guard failed: javac reported $reported but this script counted $($diagnostics.Count) top-level diagnostics" }