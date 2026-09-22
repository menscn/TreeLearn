#requires -Version 5.1
<#
    Generator for T-007's mutation evidence (artifacts/evidence/T-007-mutation.txt).

    Why this file exists: two of T-007's assertions are the ONLY evidence for a claim its acceptance
    criteria make -- "a stored snapshot whose digest does not match is refused" and "a schema version
    this build cannot read is refused". An assertion that is the sole evidence for a claim must be
    shown to be falsifiable (AGENTS.md failure library, [test] entry of 2026-09-21): a test that
    cannot go red proves nothing. This script breaks one behaviour at a time and shows that exactly
    the intended test turns red while every other test stays green.

    Method: each mutation is applied to a TEMP COPY of the :core domain package. The working tree is
    never written to -- the repository is shared with other lanes, and a mutated :core would break
    their builds while the mutation is in place. Pristine bytes are re-copied from the working tree
    between rounds, and the working tree is fingerprinted before and after to show it never moved.

    House rules honoured on purpose (see the failure library in AGENTS.md):
      * child output is captured line by line ([string] $_), never through Out-String, whose
        host-width re-wrapping truncates proof lines;
      * every line this script reports goes through [Console]::Out.WriteLine, never through the
        PowerShell pipeline, because a function's pipeline output is what the caller assigns -- the
        first version of this file captured its own log into a variable and printed nothing at all;
      * external commands run with $ErrorActionPreference = Continue and are judged by
        $LASTEXITCODE, so a javac note cannot become a terminating NativeCommandError;
      * dependency jars are resolved by pattern with an assertion that the match set is non-empty and
        unique, never by a hand-written list of paths;
      * the temp copy holds sources only (no build outputs), and the longest resulting path is
        measured and asserted against the Windows limit;
      * this script is deliberately pure ASCII, so PowerShell 5.1 cannot mis-decode it whatever the
        system code page is (the alternative is UTF-8 with BOM);
      * exit codes are separated: 0 = every assertion held, 1 = a mutation did not behave as
        required, 3 = this harness could not conclude at all. "Did not finish" and "finished and
        found it broken" must never share a code.

    Run from the repository root:
      powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-t007-mutation.ps1 2>&1 |
          Out-File -LiteralPath artifacts/evidence/T-007-mutation.txt -Encoding utf8 -Append
#>
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$root    = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$srcIn   = Join-Path $root 'android\core\src\main\java\com\treelearn\core\domain'
$tstIn   = Join-Path $root 'android\core\src\test\java\com\treelearn\core\domain'
$rel     = 'com\treelearn\core\domain'
$work    = Join-Path $env:TEMP 'tl-t007-mutation'
$workSrc = Join-Path $work "src\$rel"
$workTst = Join-Path $work "tst\$rel"
$classes = Join-Path $work 'classes'
$javac   = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java    = Join-Path $env:JAVA_HOME 'bin\java.exe'
$testClasses = @('com.treelearn.core.domain.ContextSnapshotTest',
                 'com.treelearn.core.domain.BuildContextPlanUseCaseTest')
$expectedTests = 22

$script:junit = $null
$script:hamcrest = $null
$script:conclusive = $true
$script:mutationOk = $true

function Say([object] $text) {
    [Console]::Out.WriteLine([string] $text)
}

function Get-Jar([string] $pattern) {
    $found = @(Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1') `
        -Recurse -Filter $pattern -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    if ($found.Count -ne 1) {
        throw ("expected exactly one $pattern in the Gradle cache, found " + $found.Count)
    }
    return $found[0]
}

function Invoke-Captured([string] $exe, [string[]] $arguments) {
    Say ('$ ' + $exe + ' ' + ($arguments -join ' '))
    $captured = @(& $exe @arguments 2>&1 | ForEach-Object { [string] $_ })
    foreach ($line in $captured) { Say $line }
    Say ('EXITCODE=' + $LASTEXITCODE)
    return @{ output = $captured; exit = [int] $LASTEXITCODE }
}

function Get-DomainFingerprint {
    @(Get-ChildItem -LiteralPath $srcIn -Filter *.java | Sort-Object Name | ForEach-Object {
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash + '  ' + $_.Name
    })
}

function Reset-WorkCopy {
    if (Test-Path -LiteralPath $work) {
        Remove-Item -LiteralPath $work -Recurse -Force
    }
    New-Item -ItemType Directory -Path $workSrc, $workTst, $classes -Force | Out-Null
    Copy-Item -Path (Join-Path $srcIn '*.java') -Destination $workSrc -Force
    foreach ($test in $testClasses) {
        $leaf = ($test -split '\.')[-1] + '.java'
        Copy-Item -LiteralPath (Join-Path $tstIn $leaf) -Destination $workTst -Force
    }
}

function Apply-Mutation([string] $file, [string] $find, [string] $replace) {
    $path = Join-Path $workSrc $file
    $text = [System.IO.File]::ReadAllText($path)
    $hits = ([regex]::Matches($text, [regex]::Escape($find))).Count
    if ($hits -ne 1) {
        throw ("the mutation anchor was expected exactly once in $file, found " + $hits)
    }
    [System.IO.File]::WriteAllText($path, $text.Replace($find, $replace),
        (New-Object System.Text.UTF8Encoding($false)))
    Say ('MUTATED ' + $file + ' : ' + $find + '  ->  ' + $replace)
}

function Invoke-Round([string] $label) {
    Say ''
    Say ('--- ' + $label + ' ---')
    foreach ($file in @('ContextSnapshot.java', 'ContextSnapshotCodec.java')) {
        $path = Join-Path $workSrc $file
        Say ($file + ' SHA-256: ' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)
    }
    Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath $workSrc -Filter *.java | Sort-Object FullName |
        Select-Object -ExpandProperty FullName)
    $sources += @(Get-ChildItem -LiteralPath $workTst -Filter *.java | Sort-Object FullName |
        Select-Object -ExpandProperty FullName)
    $compile = Invoke-Captured $javac (@('-J-Duser.language=en', '-encoding', 'UTF-8', '-nowarn',
        '-d', $classes, '-cp', ($script:junit + ';' + $script:hamcrest)) + $sources)
    if ($compile.exit -ne 0) {
        $script:conclusive = $false
        throw 'the copy under test did not compile, so this round proves nothing'
    }
    $run = Invoke-Captured $java (@('-Duser.language=en', '-Dfile.encoding=UTF-8',
        '-cp', ($classes + ';' + $script:junit + ';' + $script:hamcrest),
        'org.junit.runner.JUnitCore') + $testClasses)
    $joined = $run.output -join "`n"
    $okMatch = [regex]::Match($joined, 'OK \((\d+) tests?\)')
    $summaryMatch = [regex]::Match($joined, 'Tests run: (\d+),  Failures: (\d+)')
    $tests = -1
    $failures = @()
    if ($okMatch.Success) {
        $tests = [int] $okMatch.Groups[1].Value
    } elseif ($summaryMatch.Success) {
        $tests = [int] $summaryMatch.Groups[1].Value
        $failures = @($run.output | ForEach-Object {
            $m = [regex]::Match($_, '^\d+\) ([A-Za-z0-9_]+)\(([A-Za-z0-9_.]+)\)$')
            if ($m.Success) { $m.Groups[2].Value + '.' + $m.Groups[1].Value }
        })
    } else {
        $script:conclusive = $false
        Say 'the run printed neither OK nor a JUnit summary line; this round cannot be read'
    }
    return @{ exit = $run.exit; tests = $tests; failures = $failures }
}

function Assert-Round([string] $what, [hashtable] $round, [int] $expectedExit,
        [int] $expectedFailures, [string[]] $expectedNames) {
    $problems = @()
    if ($round.exit -ne $expectedExit) {
        $problems += ('exit code was ' + $round.exit + ', expected ' + $expectedExit)
    }
    if ($round.tests -ne $expectedTests) {
        $problems += ('ran ' + $round.tests + ' tests, expected ' + $expectedTests)
    }
    if ($round.failures.Count -ne $expectedFailures) {
        $problems += ('reported ' + $round.failures.Count + ' failing tests, expected ' + $expectedFailures)
    }
    foreach ($name in $expectedNames) {
        if ($round.failures -notcontains $name) {
            $problems += ('the expected failing test ' + $name + ' did not fail')
        }
    }
    foreach ($name in $round.failures) {
        if ($expectedNames -notcontains $name) {
            $problems += ('an unrelated test also failed: ' + $name)
        }
    }
    if ($problems.Count -eq 0) {
        Say ('ASSERTION HELD: ' + $what)
    } else {
        Say ('ASSERTION FAILED: ' + $what)
        foreach ($problem in $problems) { Say ('  - ' + $problem) }
        $script:mutationOk = $false
    }
}

Say '# T-007 mutation evidence - are the two load-bearing refusals really falsifiable?'
Say ('# captured-at: ' + (Get-Date).ToString('o'))
Say '# command: powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-t007-mutation.ps1 2>&1 | Out-File -LiteralPath artifacts/evidence/T-007-mutation.txt -Encoding utf8 -Append'
Say '# mode: full (baseline + two single-defect mutations + restore, all on a temp copy)'
Say ('# java: ' + $java)
Say ('# working tree under test: ' + $srcIn)
Say ''

try {
    $script:junit = Get-Jar 'junit-4.13.2.jar'
    $script:hamcrest = Get-Jar 'hamcrest-core-1.3.jar'
    Say ('junit    : ' + $script:junit)
    Say ('hamcrest : ' + $script:hamcrest)
    $fingerprintBefore = Get-DomainFingerprint
    Reset-WorkCopy
    $longest = (Get-ChildItem -LiteralPath $work -Recurse -File |
        Sort-Object { $_.FullName.Length } -Descending | Select-Object -First 1).FullName.Length
    Say ('longest path inside the temp copy: ' + $longest + ' characters (Windows limit 259)')
    if ($longest -ge 250) {
        throw ('the temp copy exceeds the safe path length: ' + $longest)
    }

    $baseline = Invoke-Round 'Baseline - pristine :core domain sources in the temp copy'
    Assert-Round 'the pristine copy passes every test' $baseline 0 0 @()

    Apply-Mutation 'ContextSnapshot.java' 'if (!recomputed.equals(digest)) {' 'if (false) {'
    $digest = Invoke-Round 'Mutation A - fromStored never compares the stored digest'
    Assert-Round 'removing the digest comparison turns exactly the digest test red' $digest 1 1 `
        @('com.treelearn.core.domain.ContextSnapshotTest.aPayloadWhoseDigestDoesNotMatchIsRefused')

    Reset-WorkCopy
    Apply-Mutation 'ContextSnapshotCodec.java' 'if (schemaVersion != ContextSnapshot.SCHEMA_VERSION) {' 'if (false) {'
    $version = Invoke-Round 'Mutation B - decode accepts every stored schema version'
    Assert-Round 'removing the version gate turns exactly the version test red' $version 1 1 `
        @('com.treelearn.core.domain.ContextSnapshotTest.aSchemaVersionThisBuildCannotReadIsRefused')

    Reset-WorkCopy
    $restored = Invoke-Round 'Restore - the pristine bytes copied back from the working tree'
    Assert-Round 'the restored copy is green again' $restored 0 0 @()

    Say ''
    Say '--- working tree ---'
    $after = Get-DomainFingerprint
    $drift = @(Compare-Object -ReferenceObject $fingerprintBefore -DifferenceObject $after)
    Say ('domain source files fingerprinted before and after: ' + $fingerprintBefore.Count)
    if ($drift.Count -eq 0) {
        Say 'ASSERTION HELD: the working tree is byte-identical to what it was before this run'
    } else {
        Say 'ASSERTION FAILED: the working tree changed during this run'
        foreach ($line in $drift) { Say ('  ' + $line) }
        $script:mutationOk = $false
    }
} catch {
    Say ('HARNESS ERROR: ' + $_.Exception.Message)
    $script:conclusive = $false
} finally {
    if (Test-Path -LiteralPath $work) {
        try {
            Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
        } catch {
            Say ('WARNING: the temp copy could not be removed: ' + $_.Exception.Message)
        }
    }
}

Say ''
if (-not $script:conclusive) {
    Say 'VERDICT: INCONCLUSIVE (exit 3) - this harness could not finish; no claim is made either way'
    exit 3
}
if (-not $script:mutationOk) {
    Say 'VERDICT: FAIL (exit 1) - a mutation did not break what it was supposed to break'
    exit 1
}
Say 'VERDICT: PASS (exit 0) - each removed behaviour turned exactly its own test red; the restore is green; the working tree never moved'
exit 0