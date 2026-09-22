#requires -Version 5.1
<#
    Generator for T-007's GREEN evidence (artifacts/evidence/T-007-green.txt) and the JUnit counts
    that belong beside it (artifacts/evidence/T-007-tests.txt).

    The command string is character-for-character the one in T-007-red.txt; the task contract requires
    RED and GREEN to be produced by the same command.

    House rules honoured on purpose (see the failure library in AGENTS.md):
      * streamed capture with Out-File -Encoding utf8 -Append, never >> and never Out-String;
      * native stderr captured with $ErrorActionPreference = Continue plus $LASTEXITCODE;
      * every test number is read back out of the JUnit XML that the run itself wrote, never typed from
        memory, and the totals are re-summed from the per-class rows printed next to them;
      * the written evidence files are scanned for U+FFFD before this script reports success.

    Run from the repository root:
      powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-t007-green.ps1
#>
$ErrorActionPreference = 'Continue'

$root = 'D:\Project\Android\Treelearn'
$evidence = Join-Path $root 'artifacts\evidence'
$out  = Join-Path $evidence 'T-007-green.txt'
$counts = Join-Path $evidence 'T-007-tests.txt'
$mode = 'GREEN (same command as RED; the production types and the fixed fixtures are in place)'
$command = 'cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest --no-daemon --console=plain'
$started = (Get-Date).ToString('o')

@(
    '# T-007 GREEN evidence - immutable birth snapshot + later-context encoding (:core / :ai)'
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
$successLines = @($captured | Where-Object { $_ -match '^BUILD SUCCESSFUL' })
$failedTasks = @($captured | Where-Object { $_ -match '^> Task .* FAILED' })
$executedTasks = @($captured | Where-Object { $_ -match '^> Task ' })
$replacementChars = @($captured | Where-Object { $_.Contains([char] 0xFFFD) }).Count

# JUnit XML is the authority for the counts; it is written by the run above.
$reports = @()
foreach ($dir in @('core\build\test-results\test', 'ai\build\test-results\testDebugUnitTest')) {
    $full = Join-Path (Join-Path $root 'android') $dir
    if (Test-Path $full) {
        $reports += Get-ChildItem -LiteralPath $full -Filter 'TEST-*.xml'
    }
}

$rows = @()
foreach ($report in $reports) {
    [xml] $xml = Get-Content -LiteralPath $report.FullName -Encoding UTF8
    $suite = $xml.testsuite
    $rows += [pscustomobject] @{
        Class    = [string] $suite.name
        Tests    = [int] $suite.tests
        Failures = [int] $suite.failures
        Errors   = [int] $suite.errors
        Skipped  = [int] $suite.skipped
    }
}
$rows = $rows | Sort-Object Class
$totalTests = ($rows | Measure-Object -Property Tests -Sum).Sum
$totalFailures = ($rows | Measure-Object -Property Failures -Sum).Sum
$totalErrors = ($rows | Measure-Object -Property Errors -Sum).Sum
$totalSkipped = ($rows | Measure-Object -Property Skipped -Sum).Sum

@(
    ''
    '=== conclusion (derived by this script from the capture above; no number is typed by hand) ==='
    "BUILD SUCCESSFUL lines            : $($successLines.Count)"
    "failed gradle tasks               : $($failedTasks.Count)"
    "gradle task lines printed         : $($executedTasks.Count)"
    "junit classes in the reports      : $($rows.Count)"
    "junit tests / failures / errors   : $totalTests / $totalFailures / $totalErrors"
    "junit skipped                     : $totalSkipped"
    'U+FFFD replacement characters     : ' + $replacementChars
    ''
    'authoritative per-class rows, re-summed above from these very rows:'
    ($rows | ForEach-Object { "  {0,-58} tests={1,-4} failures={2,-3} errors={3,-3} skipped={4}" -f $_.Class, $_.Tests, $_.Failures, $_.Errors, $_.Skipped })
    ''
    'commands that reproduce the numbers above:'
    "  Select-String -Path '$out' -Pattern '^BUILD SUCCESSFUL' -Encoding UTF8"
    "  Get-ChildItem android/core/build/test-results/test, android/ai/build/test-results/testDebugUnitTest -Filter TEST-*.xml"
) | Out-File -LiteralPath $out -Encoding utf8 -Append

@(
    '# T-007 JUnit counts - read back from the XML the GREEN run wrote, never retyped'
    "# captured-at: $started"
    "# produced by: $command"
    "# read from: android/core/build/test-results/test/TEST-*.xml, android/ai/build/test-results/testDebugUnitTest/TEST-*.xml"
    ''
    'class                                                          tests failures errors skipped'
    '  ' + ('-' * 82)
) | Out-File -LiteralPath $counts -Encoding utf8
$rows | ForEach-Object {
    "  {0,-58} {1,5} {2,8} {3,6} {4,7}" -f $_.Class, $_.Tests, $_.Failures, $_.Errors, $_.Skipped
} | Out-File -LiteralPath $counts -Encoding utf8 -Append
@(
    '  ' + ('-' * 82)
    ("  {0,-58} {1,5} {2,8} {3,6} {4,7}" -f 'TOTAL', $totalTests, $totalFailures, $totalErrors, $totalSkipped)
    ''
    "sum of the per-class rows above: tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped"
    "gradle exit code of the run these numbers come from: $exit"
) | Out-File -LiteralPath $counts -Encoding utf8 -Append

$replacementCounts = @(Get-Content -LiteralPath $counts -Encoding UTF8 | Where-Object { $_.Contains([char] 0xFFFD) }).Count

Write-Host "wrote $out (EXITCODE=$exit, BUILD SUCCESSFUL=$($successLines.Count), failed tasks=$($failedTasks.Count))"
Write-Host "wrote $counts (classes=$($rows.Count), tests=$totalTests, failures=$totalFailures, errors=$totalErrors, skipped=$totalSkipped)"
$rows | ForEach-Object { Write-Host ("  {0} tests={1} failures={2} errors={3}" -f $_.Class, $_.Tests, $_.Failures, $_.Errors) }
if ($replacementChars -ne 0 -or $replacementCounts -ne 0) { throw 'encoding guard failed: replacement characters found' }
if ($exit -ne 0) { throw "GREEN run was supposed to succeed but exited $exit" }
if ($successLines.Count -ne 1) { throw "expected exactly one BUILD SUCCESSFUL line, found $($successLines.Count)" }
if ($rows.Count -eq 0) { throw 'no JUnit XML was found; an empty report set proves nothing' }
if ($totalFailures -ne 0 -or $totalErrors -ne 0) { throw "the run left $totalFailures failure(s) and $totalErrors error(s)" }