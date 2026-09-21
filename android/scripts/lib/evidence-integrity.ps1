#requires -Version 5.1
<#
.SYNOPSIS
    Pure evidence-integrity helpers shared by the Java-only guard and its self-test.

.DESCRIPTION
    These functions live in one file on purpose, and the reason is a defect this
    repository actually shipped: a round-6-independent audit found that
    guard-selftest.ps1's "unreplaced placeholder" case was asserting a function
    that existed ONLY inside the self-test. The guard had no such assertion at
    all, so the case certified a property of the wrong artifact and printed PASS
    for a check nobody ran.

    Sharing the definition fixes that class: the guard imports these helpers and
    uses them on its own output, and the self-test imports the SAME file and fires
    them on synthetic input. Testing a copy of a rule is not testing the rule.

    Keep this file ASCII-only so it stays readable under Windows PowerShell 5.1
    regardless of the file's encoding.
#>

<#
.SYNOPSIS
    Normalises captured child-process output to an array of single lines.

.DESCRIPTION
    Both helpers below iterate "line by line", but the two producers of that text
    hand it over in DIFFERENT shapes:

      * no-kotlin-check.ps1 captures a child with `| Out-String`, which yields ONE
        multi-line string;
      * guard-selftest.ps1 synthesises arrays of lines.

    `foreach ($line in @($Text))` iterates once over a String, so the guard's real
    path was treated as a single line. Round 6 measured the cost in the guard's own
    output: a five-line `verifyNoKotlinDex` result was reported as
    "1 judgement line(s)", one OK line carried the entire blob (so the proof line
    was unreadable and its count meaningless), and the blob matched 'NOTICE' as a
    substring, which made the guard print a spurious NOTICE.

    Normalising here rather than at each call site is deliberate: the self-test
    exercises these helpers directly, and a fix applied only in the guard would
    leave the self-test certifying a shape the production path never uses.
#>
function ConvertTo-EvidenceLines {
    param([object] $Text)

    if ($null -eq $Text) { return , @() }
    if ($Text -is [string]) { return , @($Text -split "`r?`n") }

    $lines = @()
    foreach ($item in @($Text)) {
        foreach ($part in @([string] $item -split "`r?`n")) { $lines += $part }
    }
    return , $lines
}

<#
.SYNOPSIS
    Returns the guard OK lines that still carry a literal {N} format placeholder.

.DESCRIPTION
    The guard's OK lines are the only place where the SCALE of what was verified is
    recorded (how many variant classpaths, how many report lines, how many APKs).
    In PowerShell `-f` binds tighter than `+`, so `"a {0}" + "b {2}" -f $x, $y, $z`
    formats only the second operand: the first half keeps its literal {0}/{1} and
    the evidence silently stops supporting the claim it was written for.
    Measured: `'a{0}' + 'b{2}' -f 1,2,3` yields "a{0}b3". A run whose proof line is
    un-substituted is a false witness, so callers must treat a non-empty result as
    a hard failure rather than a cosmetic warning.
#>
function Get-UnreplacedPlaceholderLine {
    param([object] $Text)

    $found = @()
    foreach ($line in (ConvertTo-EvidenceLines -Text $Text)) {
        $text = [string] $line
        if ($text.TrimStart().StartsWith('OK   ') -and $text -match '\{\d+\}') {
            $found += $text.Trim()
        }
    }
    return , $found
}

<#
.SYNOPSIS
    Judges whether the dex stage's evidence is sufficient to call it PASS.

.DESCRIPTION
    The dex stage shells out to `gradlew verifyNoKotlinDex` and used to decide on
    the child's EXIT CODE alone. A round-6-independent audit showed that is not
    enough: an up-to-date task, a skipped task or a renamed task all exit 0 while
    printing nothing, and the stage then certified "no built APK contains Kotlin
    runtime bytecode" over artifacts it never looked at.

    This function therefore asserts the EVIDENCE, not the exit code:
      * at least one 'verifyNoKotlinDex:' judgement line must be present;
      * the final 'verifyNoKotlinDex: PASS (N APK(s) ...)' verdict line must be present;
      * N must be parseable and at least MinApk, because "0 APK(s) inspected" means
        the discovery pattern went stale and nothing was scanned.

.PARAMETER Text
    The child process output, as a single string or an array of lines.

.PARAMETER ExitCode
    The child process exit code.

.PARAMETER MinApk
    The smallest number of inspected APKs that counts as evidence. Defaults to 1.

.OUTPUTS
    A PSCustomObject with:
      JudgementLines : every 'verifyNoKotlinDex:' line, trimmed
      PassLine       : the final PASS verdict line, or $null
      ApkCount       : the APK count parsed out of the PASS line, or -1
      Problems       : an array of problem strings; empty means the stage may be PASS
#>
function Get-ApkDexStageEvidence {
    param(
        [object] $Text,
        [int] $ExitCode,
        [int] $MinApk = 1
    )

    $judgementLines = @()
    foreach ($line in (ConvertTo-EvidenceLines -Text $Text)) {
        $text = ([string] $line).Trim()
        if ($text -match '^verifyNoKotlinDex:') { $judgementLines += $text }
    }

    $passLine = $null
    foreach ($line in $judgementLines) {
        if ($line -match 'verifyNoKotlinDex:\s*PASS') { $passLine = $line }
    }

    $apkCount = -1
    if ($passLine -and $passLine -match 'PASS \((\d+)\s+APK') { $apkCount = [int] $Matches[1] }

    $problems = @()
    if ($ExitCode -ne 0) {
        $problems += "verifyNoKotlinDex failed (exit $ExitCode)"
    }
    elseif ($judgementLines.Count -eq 0) {
        $problems += ('verifyNoKotlinDex exited 0 but printed no judgement line at all, so nothing ' +
            'shows that any APK was inspected (an up-to-date, skipped or renamed task also exits 0); ' +
            'this stage cannot be reported as PASS without evidence')
    }
    elseif (-not $passLine) {
        $problems += ("verifyNoKotlinDex printed $($judgementLines.Count) judgement line(s) but none " +
            "is its final 'verifyNoKotlinDex: PASS (N APK(s) ...)' verdict, so the per-APK lines " +
            'alone do not show that the scan completed')
    }
    elseif ($apkCount -lt 0) {
        $problems += ("verifyNoKotlinDex printed a PASS line without a parseable APK count, so the " +
            "number of inspected artifacts cannot be audited: $passLine")
    }
    elseif ($apkCount -lt $MinApk) {
        $problems += ("verifyNoKotlinDex inspected $apkCount APK(s), fewer than the required " +
            "$MinApk; a scan of nothing is not a pass, which means the discovery pattern is stale " +
            'or the artifacts were not built')
    }

    return [pscustomobject]@{
        JudgementLines = $judgementLines
        PassLine       = $passLine
        ApkCount       = $apkCount
        Problems       = $problems
    }
}
