#requires -Version 5.1
<#
.SYNOPSIS
    Two-way self-test probe for the Treelearn Java-only guard.

.DESCRIPTION
    A guard whose detection branches are never exercised is worthless: the
    round-3 audit found that an entire slashy-string branch in
    no-kotlin-check.ps1 had been dead code for two review rounds because
    `'=(,:[' -contains $prev` is a PowerShell COLLECTION test, not a substring
    test. The rule adopted afterwards (and recorded in AGENTS.md) is that every
    detection branch must ship with a probe proving that an injected violation
    is actually caught.

    This script IS that probe, kept in the repo so it can be re-run instead of
    being re-typed and then lost. It never touches the working tree: it works on
    a scratch copy of android/ under the system temp directory.

    For each case it injects one pattern into the copy, runs the guard, and
    compares the exit code with the expected one:
        expected 1 = the injection MUST be caught (FAIL)
        expected 2 = a legitimate pattern MUST NOT be reported (static stage only)
        expected 0 = the untouched copy MUST pass

    Exit codes of the guard under test:
        0 = PASS, 1 = FAIL, 2 = PARTIAL (dependency/APK stage skipped)

    Exit codes of THIS self-test:
        0 = every case matched
        1 = one or more cases mismatched (the detection branches are suspect)
        3 = INCONCLUSIVE: the harness itself aborted (scratch copy, cleanup, or a
            path that crossed the Windows limit). Deliberately distinct from 1.
            Both used to be 1, so a scratch-copy cleanup failure read exactly like
            "a detection branch is broken" and sent the reader hunting for a bug in
            the guard instead of in the harness. A run that did not finish must
            never be citable as evidence in either direction.

.PARAMETER Fast
    Skip the two cases that need a real Gradle run (stage 3 / stage 3b).

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\dev\guard-selftest.ps1
#>
[CmdletBinding()]
param(
    [switch] $Fast
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Harness-abort channel.
#
# A terminating error from here on means the harness could not finish (scratch copy
# failed, the scratch tree could not be cleaned, a path crossed the Windows limit).
# That says nothing about the guard, so it must not travel on the same channel as a
# mismatched case. Exit 3 = INCONCLUSIVE.
# ---------------------------------------------------------------------------
trap {
    Write-Host ''
    Write-Host ('=' * 70)
    Write-Host 'OVERALL = SELFTEST INCONCLUSIVE (harness aborted; no verdict on the guard)' -ForegroundColor Red
    Write-Host ("  {0}" -f $_.Exception.Message) -ForegroundColor Red
    Write-Host '  This is NOT a detection-branch failure and NOT a pass. Fix the harness first.'
    exit 3
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$guard = Join-Path $repoRoot 'android\scripts\no-kotlin-check.ps1'
if (-not (Test-Path -LiteralPath $guard)) {
    throw "guard script not found: $guard"
}

# The helpers under test are imported from the SAME file the guard imports. A
# round-6-independent audit found CASE 9 was asserting a function that existed only
# inside this self-test: the guard had no placeholder check at all, so that case
# certified a property of the wrong artifact. Testing a copy of a rule is not
# testing the rule, so the definition is now shared and the wiring is asserted.
$sharedLibrary = Join-Path $repoRoot 'android\scripts\lib\evidence-integrity.ps1'
if (-not (Test-Path -LiteralPath $sharedLibrary)) {
    throw "shared evidence-integrity library not found: $sharedLibrary"
}
. $sharedLibrary

# ---------------------------------------------------------------------------
# Provenance header. Written before any case runs, so a captured evidence file
# identifies what produced it: which guard revision, which library, which command.
# Without this a captured log cannot be told apart from one produced by an older
# revision of the guard, which is how a stale evidence file stayed in the repo.
# ---------------------------------------------------------------------------
function Get-FileFingerprint([string] $Path) {
    $hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    $lines = (Get-Content -LiteralPath $Path).Count
    return "SHA-256 $hash lines $lines"
}

Write-Host '=== Treelearn Java-only guard self-test ==='
Write-Host ("Generated : {0:yyyy-MM-ddTHH:mm:sszzz}" -f (Get-Date))
Write-Host ("Command   : powershell -NoProfile -ExecutionPolicy Bypass -File {0}{1}" -f `
        $PSCommandPath, $(if ($Fast) { ' -Fast' } else { '' }))
Write-Host ("Guard     : {0}  [{1}]" -f $guard, (Get-FileFingerprint $guard))
Write-Host ("Library   : {0}  [{1}]" -f $sharedLibrary, (Get-FileFingerprint $sharedLibrary))
Write-Host ''

# ---------------------------------------------------------------------------
# Scratch tree.
#
# The copy deliberately carries the repo's build/ outputs so the Gradle-backed
# cases stay incremental, and a per-test logcat artifact name (device name + test
# class + test method) already reaches ~240 characters inside the repo. Every
# character the scratch prefix adds is another chance to cross the Windows
# 259-character limit, where robocopy and Remove-Item fail with DirectoryNotFound
# and the run dies while the verdict still reads like a broken detection branch.
#
# Measured on 2026-09-21: with the old %TEMP%\tl-guard-selftest prefix the copied
# logcat path for one test was exactly 260 characters and Remove-Item killed the
# run at CASE 1. Fixes, in order of importance: (1) the deep per-test artifact trees
# are no longer copied at all (see New-GuardCopy), which removes the long paths
# rather than trying to outrun them; (2) a shorter scratch prefix; (3) a cleanup that
# cannot abort the verdict; (4) an explicit length assertion whose measured number is
# printed every run, so the margin is visible.
# ---------------------------------------------------------------------------
function Get-ScratchRoot {
    $candidates = @(
        (Join-Path $env:TEMP 'tls'),
        (Join-Path $env:TEMP 'tl-guard-selftest')
    )
    $short = $candidates | Where-Object { $_.Length -le 40 } | Select-Object -First 1
    if ($short) { return $short }
    return $candidates[0]
}

function Remove-ScratchRoot {
    if (-not (Test-Path -LiteralPath $scratchRoot)) { return $true }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        Remove-Item -LiteralPath $scratchRoot -Recurse -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $scratchRoot) {
            & cmd /c rd /s /q "$scratchRoot" 2>&1 | Out-Null
        }
    }
    finally {
        $ErrorActionPreference = $previous
    }
    if (Test-Path -LiteralPath $scratchRoot) {
        Write-Host ("  CLEANUP INCOMPLETE: {0} survived removal; left in place for inspection." -f $scratchRoot) -ForegroundColor Yellow
        return $false
    }
    return $true
}

function Assert-ScratchPathsAreAddressable {
    # Only the deep per-test artifact trees can realistically cross the limit
    # (device name + test name + logcat prefix), so those are measured rather than
    # the whole tree. The measured number is printed so the margin is visible and a
    # regression shows up as a trend instead of as a crash.
    $deepest = ''
    foreach ($relative in @('app\build\outputs', 'app\build\reports', 'app\build\intermediates')) {
        $candidateRoot = Join-Path $copy $relative
        if (-not (Test-Path -LiteralPath $candidateRoot)) { continue }
        foreach ($file in (Get-ChildItem -LiteralPath $candidateRoot -Recurse -File -ErrorAction SilentlyContinue)) {
            if ($file.FullName.Length -gt $deepest.Length) { $deepest = $file.FullName }
        }
    }
    if ($deepest.Length -ge 259) {
        throw ("the scratch copy contains a {0}-character path (Windows limit 259): {1}" -f $deepest.Length, $deepest)
    }
    Write-Host ("  scratch root '{0}'; longest deep artifact path {1} character(s)" -f $scratchRoot, $deepest.Length)
}

$scratchRoot = Get-ScratchRoot
$copy = Join-Path $scratchRoot 'android'

function Invoke-NativeCaptured([string] $Executable, [string[]] $CommandArguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Executable @CommandArguments 2>&1
        return @{ ExitCode = $LASTEXITCODE; Text = @($output) }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function New-GuardCopy {
    [void](Remove-ScratchRoot)
    New-Item -ItemType Directory -Path $scratchRoot -Force | Out-Null
    # Keep build/ so the Gradle work is incremental; drop the per-project caches
    # (.gradle / .kotlin) that hold absolute paths, and drop the deep per-test artifact
    # trees (androidTest-results / reports / test-results / logs). Nothing in this
    # self-test reads them, and a device name plus a test method name already makes a
    # logcat filename ~240 characters, so copying them is what pushed the scratch copy
    # to exactly 260 characters and made cleanup fail. The APK outputs the guard's dex
    # stage needs live in build/outputs/apk and are kept.
    $source = Join-Path $repoRoot 'android'
    & robocopy $source $copy /E /NFL /NDL /NJH /NJS /NP /MT:16 `
        /XD (Join-Path $source '.gradle') (Join-Path $source '.kotlin') `
        (Join-Path $source 'app\build\outputs\androidTest-results') `
        (Join-Path $source 'app\build\reports') `
        (Join-Path $source 'app\build\test-results') `
        (Join-Path $source 'app\build\outputs\logs') | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "robocopy failed with exit code $LASTEXITCODE"
    }
    Assert-ScratchPathsAreAddressable
}

function Add-GradleLine([string] $RelativeScript, [string] $Line) {
    $path = Join-Path $copy $RelativeScript
    Add-Content -LiteralPath $path -Value $Line -Encoding UTF8
}

function Invoke-GuardUnderTest([string] $AndroidRoot, [switch] $StaticOnly, [switch] $SkipApkStage) {
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $guard, '-AndroidRoot', $AndroidRoot)
    if ($StaticOnly) { $arguments += '-SkipDependencyStage' }
    if ($SkipApkStage) { $arguments += '-SkipApkStage' }
    return Invoke-NativeCaptured 'powershell' $arguments
}

$results = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]

# Get-UnreplacedPlaceholderLine now comes from the shared library imported above
# (scripts/lib/evidence-integrity.ps1), so this probe fires the same definition the
# guard runs. The wiring is asserted here rather than assumed: if the guard ever
# stops importing the library, CASE 9 silently becomes a test of nothing again.
$guardImportsLibrary = (Select-String -LiteralPath $guard -Pattern 'lib\\evidence-integrity\.ps1').Count -gt 0

function Assert-GuardCase {
    param(
        [string] $Name,
        [int] $Expected,
        [object] $Run
    )
    $ok = ($Run.ExitCode -eq $Expected)

    # Evidence-integrity assertion, applied to every case.
    #
    # Rationale: the guard's OK lines are the only place where the *scale* of what
    # was verified is recorded (how many variant classpaths, how many report lines).
    # A `"a {0}" + "b {2}" -f ...` expression formats only the second operand because
    # `-f` binds tighter than `+`, so the first half keeps its literal {0}/{1} and the
    # evidence silently stops supporting the claim it was written for. That actually
    # shipped once (see AGENTS.md failure log; measured: 'a{0}' + 'b{2}' -f 1,2,3
    # yields "a{0}b3"). A case that "passes" while its proof line is un-substituted is
    # a false witness, so this is a hard mismatch, not a warning.
    $unreplaced = Get-UnreplacedPlaceholderLine -Text $Run.Text
    if ($unreplaced.Count -gt 0) {
        $ok = $false
        Write-Host "  MISMATCH $Name : guard OK line(s) contain unreplaced format placeholders" -ForegroundColor Red
        foreach ($u in $unreplaced) { Write-Host "      $u" -ForegroundColor DarkGray }
    }

    $verdict = if ($ok) { 'OK       ' } else { 'MISMATCH ' }
    Write-Host ("  {0}{1} (expected {2}, got {3})" -f $verdict, $Name, $Expected, $Run.ExitCode)
    $results.Add([pscustomobject]@{ Name = $Name; Expected = $Expected; Actual = $Run.ExitCode; Ok = $ok })
    if (-not $ok) {
        $failures.Add("$Name : expected $Expected, got $($Run.ExitCode)")
        foreach ($line in ($Run.Text | Select-Object -Last 12)) { Write-Host "      $line" -ForegroundColor DarkGray }
    }
}

function Assert-Bool {
    param([string] $Name, [bool] $Condition)
    $verdict = if ($Condition) { 'OK       ' } else { 'MISMATCH ' }
    Write-Host ("  {0}{1}" -f $verdict, $Name)
    $results.Add([pscustomobject]@{ Name = $Name; Expected = $null; Actual = $null; Ok = $Condition })
    if (-not $Condition) { $failures.Add("$Name : condition was false") }
}

# ---------------------------------------------------------------------------
# CASE 0 -- untouched copy must pass (guards against a probe that always fails).
# ---------------------------------------------------------------------------
Write-Host 'CASE 0  untouched copy'
New-GuardCopy
# The run is kept, not just asserted: CASE 11 re-reads its OUTPUT to prove the
# guard actually executed its sibling parse gate. Grepping the guard's source for
# the gate would also pass on a comment that merely mentions it, and "the branch
# exists" is not the same claim as "the branch ran".
if (-not $Fast) {
    $baselineRun = Invoke-GuardUnderTest $copy
    Assert-GuardCase -Name 'untouched copy => PASS' -Expected 0 -Run $baselineRun
}
else {
    $baselineRun = Invoke-GuardUnderTest $copy -StaticOnly
    Assert-GuardCase -Name 'untouched copy, static stage => no problem' -Expected 2 -Run $baselineRun
}

# ---------------------------------------------------------------------------
# CASE 1-2 -- the round-3 bypass class: legal Groovy that desynchronised the
# old lexer. Regression of the `-contains` bug.
# ---------------------------------------------------------------------------
Write-Host 'CASE 1  slashy string swallowing following lines (round-3 bypass)'
New-GuardCopy
Add-GradleLine 'app\build.gradle' 'def re = /a\/*b/'
Add-GradleLine 'app\build.gradle' "releaseImplementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'"
Assert-GuardCase -Name 'slashy + releaseImplementation kotlin => FAIL' -Expected 1 -Run (Invoke-GuardUnderTest $copy -StaticOnly)

Write-Host 'CASE 2  slashy string + Kotlin Gradle plugin'
New-GuardCopy
Add-GradleLine 'app\build.gradle' 'def re = /a\/*b/'
Add-GradleLine 'app\build.gradle' 'plugins { id("kotlin") }'
Assert-GuardCase -Name 'slashy + id("kotlin") plugin => FAIL' -Expected 1 -Run (Invoke-GuardUnderTest $copy -StaticOnly)

# ---------------------------------------------------------------------------
# CASE 3 -- injection matrix: every stage-2 detection branch must fire.
# ---------------------------------------------------------------------------
Write-Host 'CASE 3  injection matrix (each must be caught by the static stage)'
$injections = @(
    @{ n = 'implementation';    l = "implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'" },
    @{ n = 'compileOnly';       l = "compileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'" },
    @{ n = 'androidTestImpl';   l = "androidTestImplementation 'org.jetbrains.kotlin:kotlin-test:2.0.0'" },
    @{ n = 'kapt plugin';       l = "apply plugin: 'kotlin-kapt'" },
    @{ n = 'kapt block';        l = 'kapt { correctErrorTypes = true }' },
    @{ n = 'ksp plugin';        l = "id 'com.google.devtools.ksp'" },
    @{ n = 'kotlinOptions';     l = 'kotlinOptions { jvmTarget = "17" }' },
    @{ n = 'kotlin.ext';        l = 'kotlin { jvmToolchain(17) }' },
    @{ n = 'kotlinVersion';     l = 'kotlinVersion = "2.0.0"' },
    @{ n = 'libs.plugins';      l = 'alias(libs.plugins.kotlin.android)' },
    @{ n = 'code.style';        l = 'kotlin.code.style=official' },
    @{ n = '-ktx artifact';     l = "implementation 'androidx.core:core-ktx:1.13.1'" }
)
foreach ($case in $injections) {
    New-GuardCopy
    if ($case.l -match '=' -and $case.n -eq 'code.style') {
        Add-GradleLine 'gradle.properties' $case.l
    }
    else {
        Add-GradleLine 'app\build.gradle' $case.l
    }
    Assert-GuardCase -Name "injected $($case.n) => FAIL" -Expected 1 -Run (Invoke-GuardUnderTest $copy -StaticOnly)
}

# ---------------------------------------------------------------------------
# CASE 4 -- legitimate Java-only patterns must NOT be reported (false-positive
# guard: a probe that only ever fails would pass CASE 0-3 for the wrong reason).
# ---------------------------------------------------------------------------
Write-Host 'CASE 4  legitimate patterns (must NOT fail the static stage)'
$legitimate = @(
    @{ n = 'exclude kotlin group'; l = "implementation('com.example:lib:1.0') { exclude group: 'org.jetbrains.kotlin' }" },
    @{ n = 'line comment';         l = "// implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'" },
    @{ n = 'plain java dep';       l = "implementation 'com.squareup:javapoet:1.13.0'" }
)
foreach ($case in $legitimate) {
    New-GuardCopy
    Add-GradleLine 'app\build.gradle' $case.l
    Assert-GuardCase -Name "legitimate $($case.n) => no problem" -Expected 2 -Run (Invoke-GuardUnderTest $copy -StaticOnly)
}

# ---------------------------------------------------------------------------
# CASE 5 -- fail-closed measure 1: an unterminated comment must distrust the scan
# rather than silently skipping the rest of the file.
# ---------------------------------------------------------------------------
Write-Host 'CASE 5  lexer desync (fail-closed measure 1)'
New-GuardCopy
Add-GradleLine 'app\build.gradle' '/* unterminated block comment'
Add-GradleLine 'app\build.gradle' "implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'"
Assert-GuardCase -Name 'unterminated comment => FAIL' -Expected 1 -Run (Invoke-GuardUnderTest $copy -StaticOnly)

# ---------------------------------------------------------------------------
# CASE 6 -- round-5 finding: the NOTICE downgrade must not be spoofable by a
# dollar-slashy that puts the lexer into block-comment state and resynchronises.
# ---------------------------------------------------------------------------
Write-Host 'CASE 6  dollar-slashy hiding a compileOnly coordinate (round-5 fix)'
New-GuardCopy
Add-GradleLine 'app\build.gradle' 'def s = $/ /* /$'
Add-GradleLine 'app\build.gradle' "compileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'"
Add-GradleLine 'app\build.gradle' 'def t = $/ */$'
Assert-GuardCase -Name 'dollar-slashy + compileOnly => FAIL' -Expected 1 -Run (Invoke-GuardUnderTest $copy -StaticOnly)

if ($Fast) {
    Write-Host ''
    Write-Host 'CASE 7-8 skipped (-Fast: they need a real Gradle run)'
}
else {
    # -----------------------------------------------------------------------
    # CASE 7 -- round-5 finding: a GString coordinate is invisible to every text
    # scan, so only the resolved dependency report can catch it. The old stage 3
    # looked at three RUNTIME classpaths only, so compileOnly slipped through.
    # -----------------------------------------------------------------------
    Write-Host 'CASE 7  GString coordinate on compileOnly (round-5 fix: stage 3 pattern)'
    New-GuardCopy
    Add-GradleLine 'app\build.gradle' 'dependencies {'
    Add-GradleLine 'app\build.gradle' "    compileOnly `"org.jetbrains.`${'kotlin'}:kotlin-stdlib:2.0.0`""
    Add-GradleLine 'app\build.gradle' '}'
    Assert-GuardCase -Name 'GString compileOnly kotlin => FAIL (stage 3)' -Expected 1 -Run (Invoke-GuardUnderTest $copy -SkipApkStage)

    # -----------------------------------------------------------------------
    # CASE 8 -- round-5 finding: the dex stage used to glob three hardcoded
    # directories, so an APK anywhere else was never scanned. The androidTest APK
    # carries Kotlin-compiled annotations and is only tolerated on an
    # androidTest path, so dropping it elsewhere must be a violation.
    #
    # Round 6: a missing prerequisite used to be recorded as Ok = $true, so a run
    # that could not execute the case at all still reported "SELFTEST PASS (25
    # case(s))". A skipped probe is an unverified branch, not a passing one, so the
    # APK is now built on demand and a still-missing artifact is a MISMATCH.
    # -----------------------------------------------------------------------
    Write-Host 'CASE 8  Kotlin-carrying APK under a non-standard directory (round-5 fix: stage 3b)'
    New-GuardCopy
    $sourceApk = Join-Path $repoRoot 'android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (-not (Test-Path -LiteralPath $sourceApk)) {
        Write-Host '  building the androidTest APK this case needs (:app:assembleDebugAndroidTest)'
        $build = Invoke-NativeCaptured (Join-Path $repoRoot 'android\gradlew.bat') @(
            '-p', (Join-Path $repoRoot 'android'), '--no-daemon', '--console=plain',
            '-q', ':app:assembleDebugAndroidTest')
        if ($build.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $sourceApk)) {
            Assert-Bool -Name 'androidTest APK available for CASE 8 (build failed)' -Condition $false
        }
    }
    if (Test-Path -LiteralPath $sourceApk) {
        foreach ($rel in @('app\build\outputs\apk\debug', 'app\build\outputs\apk\release',
                'app\build\outputs\apk\androidTest\debug', 'app\build\outputs\apk\staging')) {
            New-Item -ItemType Directory -Path (Join-Path $copy $rel) -Force | Out-Null
        }
        Copy-Item -LiteralPath $sourceApk -Destination (Join-Path $copy 'app\build\outputs\apk\staging\staged.apk')
        Copy-Item -LiteralPath $sourceApk -Destination (Join-Path $copy 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
        Copy-Item -LiteralPath (Join-Path $repoRoot 'android\app\build\outputs\apk\debug\app-debug.apk') `
            -Destination (Join-Path $copy 'app\build\outputs\apk\debug\app-debug.apk')
        Copy-Item -LiteralPath (Join-Path $repoRoot 'android\app\build\outputs\apk\release\app-release-unsigned.apk') `
            -Destination (Join-Path $copy 'app\build\outputs\apk\release\app-release-unsigned.apk')
        Assert-GuardCase -Name 'Kotlin-carrying APK in app/build/outputs/apk/staging => FAIL' -Expected 1 -Run (Invoke-GuardUnderTest $copy)
    }
}

# ---------------------------------------------------------------------------
# CASE 9 -- probe the evidence-integrity assertions themselves. A check that has
# never been observed to fire is indistinguishable from dead code (the round-3
# lesson), so both directions are exercised with synthetic guard output.
#
# Round 6 note: these cases used to be a false witness. CASE 9 asserted a function
# defined inside this very file, and the guard had no equivalent check at all, so
# "unreplaced placeholders are detected" was a statement about the self-test. Both
# helpers now live in scripts/lib/evidence-integrity.ps1, the guard imports that
# file (asserted above), and these cases fire the same object the guard runs.
# ---------------------------------------------------------------------------
Write-Host 'CASE 9  evidence-integrity assertions (must fire, and must not misfire)'
Assert-Bool -Name 'guard imports the shared evidence-integrity library (else CASE 9/10 test a copy)' `
    -Condition $guardImportsLibrary

$syntheticBad = @('OK   no Kotlin coordinates on {0} variant classpath configuration(s) across {1} (x, {2} report lines)')
$syntheticGood = @('OK   no Kotlin coordinates on 53 variant classpath configuration(s) across :app, :core (x, 4201 report lines)')
Assert-Bool -Name 'unreplaced {0}/{1} placeholders are detected' `
    -Condition ((Get-UnreplacedPlaceholderLine -Text $syntheticBad).Count -eq 1)
Assert-Bool -Name 'fully substituted OK line is not flagged' `
    -Condition ((Get-UnreplacedPlaceholderLine -Text $syntheticGood).Count -eq 0)
$precedenceProbe = 'a{0}' + 'b{2}' -f 1, 2, 3
Assert-Bool -Name "-f binds tighter than + (measured: [$precedenceProbe])" `
    -Condition ($precedenceProbe -eq 'a{0}b3')

# ---------------------------------------------------------------------------
# CASE 10 -- round-6 finding: stage 3b judged the dex scan by exit code alone, so
# it could print PASS after inspecting nothing. Each direction is fired here on
# synthetic input, including the two shapes that used to be certified as clean.
# ---------------------------------------------------------------------------
Write-Host 'CASE 10  dex-stage evidence must exist and must be non-empty (round-6 fix: stage 3b)'

$noEvidence = Get-ApkDexStageEvidence -Text @('') -ExitCode 0
Assert-Bool -Name 'exit 0 with zero judgement lines => FAIL (task up-to-date/skipped)' `
    -Condition ($noEvidence.Problems.Count -ge 1)

$perApkOnly = Get-ApkDexStageEvidence -ExitCode 0 -Text @(
    'verifyNoKotlinDex: [app/build/outputs/apk/debug/app-debug.apk] shipped APK = 70966 bytes, 3 dex file(s)')
Assert-Bool -Name 'per-APK lines but no final PASS verdict => FAIL' `
    -Condition ($perApkOnly.Problems.Count -ge 1)

$zeroApks = Get-ApkDexStageEvidence -ExitCode 0 -Text @(
    'verifyNoKotlinDex: PASS (0 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)')
Assert-Bool -Name 'PASS over 0 APK(s) => FAIL (stale discovery pattern)' `
    -Condition ($zeroApks.Problems.Count -ge 1)

$uncounted = Get-ApkDexStageEvidence -ExitCode 0 -Text @(
    'verifyNoKotlinDex: PASS (no Kotlin runtime bytecode)')
Assert-Bool -Name 'PASS line without an APK count => FAIL (scale not auditable)' `
    -Condition ($uncounted.Problems.Count -ge 1)

$failedChild = Get-ApkDexStageEvidence -ExitCode 1 -Text @('verifyNoKotlinDex: 1 APK(s) contain Kotlin')
Assert-Bool -Name 'child exit 1 => FAIL even with judgement output' `
    -Condition ($failedChild.Problems.Count -ge 1)

$healthy = Get-ApkDexStageEvidence -ExitCode 0 -Text @(
    'verifyNoKotlinDex: [app/build/outputs/apk/debug/app-debug.apk] shipped APK = 70966 bytes, 3 dex file(s)',
    'verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)')
Assert-Bool -Name 'judgement lines + PASS (3 APK(s)) => no problem' `
    -Condition ($healthy.Problems.Count -eq 0)
Assert-Bool -Name 'APK count is parsed for the OK line (measured: 3)' `
    -Condition ($healthy.ApkCount -eq 3)

# The shape each producer actually uses. no-kotlin-check.ps1 captures the child
# with `| Out-String`, which yields ONE multi-line string, while this file
# synthesises arrays -- and `foreach ($line in @($Text))` iterates once over a
# String. The guard's real path was therefore judged as a single line: round 6
# measured a five-line result reported as "1 judgement line(s)", with one OK line
# carrying the whole blob (which also matched 'NOTICE' as a substring, producing a
# spurious notice). Both shapes are fired here and must agree, otherwise the
# assertions above only ever cover the shape the guard does not use.
$dexLines = @(
    'verifyNoKotlinDex: [app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk] test process = 749885 bytes, 3 dex file(s)',
    'verifyNoKotlinDex: [app/build/outputs/apk/debug/app-debug.apk] shipped APK = 70966 bytes, 3 dex file(s)',
    'verifyNoKotlinDex: [app/build/outputs/apk/release/app-release-unsigned.apk] shipped APK = 70000 bytes, 3 dex file(s)',
    'verifyNoKotlinDex: NOTICE (accepted) [androidTest] annotation classes without runtime linkage',
    'verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)')
$dexFromArray = Get-ApkDexStageEvidence -Text $dexLines -ExitCode 0
$dexFromBlob = Get-ApkDexStageEvidence -Text ($dexLines -join "`r`n") -ExitCode 0
Assert-Bool -Name ("an Out-String blob yields {0} judgement line(s), not 1" -f $dexFromBlob.JudgementLines.Count) `
    -Condition ($dexFromBlob.JudgementLines.Count -eq $dexLines.Count)
Assert-Bool -Name 'array shape and Out-String shape agree on the problem count' `
    -Condition ($dexFromBlob.Problems.Count -eq $dexFromArray.Problems.Count -and $dexFromBlob.Problems.Count -eq 0)
Assert-Bool -Name 'array shape and Out-String shape agree on the parsed APK count' `
    -Condition ($dexFromBlob.ApkCount -eq $dexFromArray.ApkCount -and $dexFromBlob.ApkCount -eq 3)
$placeholderBlob = @(
    'FAIL nothing here',
    'OK   no Kotlin coordinates on {0} variant classpath configuration(s)')
Assert-Bool -Name 'the placeholder check also reads a blob line by line' `
    -Condition ((Get-UnreplacedPlaceholderLine -Text ($placeholderBlob -join "`r`n")).Count -eq 1)

# ---------------------------------------------------------------------------
# CASE 11 -- the parse gate has two halves, and this is the half the guard cannot
# be. The guard's stage 0 parses its SIBLINGS (this file and scripts/lib/
# evidence-integrity.ps1); this case parses the GUARD. Together they cover every
# .ps1 under scripts/.
#
# Why it exists: a round-6 edit moved a '+' from the end of a continuation line to
# the start of the next one inside an open '(' and made the guard, this self-test
# and the shared library unparseable in one keystroke. The symptom was no output
# at all, which reads like a clean run.
#
# The library is deliberately NOT re-checked here: it is dot-sourced near the top
# of this file, so a broken library aborts this self-test before this line with a
# load error, which is louder than a MISMATCH. Re-asserting it here would be an
# assertion that can never fail.
# ---------------------------------------------------------------------------
Write-Host 'CASE 11  parse gate: the guard, which stage 0 excludes by design'
$guardTokens = $null
$guardParseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile(
    $guard, [ref]$guardTokens, [ref]$guardParseErrors)
foreach ($guardParseError in $guardParseErrors) {
    Write-Host ("      line {0} col {1}: {2}" -f `
            $guardParseError.Extent.StartLineNumber, `
            $guardParseError.Extent.StartColumnNumber, `
            $guardParseError.Message) -ForegroundColor DarkGray
}
Assert-Bool -Name ("the guard parses ({0} error(s))" -f $guardParseErrors.Count) `
    -Condition ($guardParseErrors.Count -eq 0)

$baselineText = [string] ($baselineRun.Text -join "`n")
Assert-Bool -Name 'the guard ran its sibling parse gate (CASE 0 output shows it)' `
    -Condition $baselineText.Contains('Script parse check')
Assert-Bool -Name 'the sibling parse gate reported a non-zero script count' `
    -Condition ([regex]::Matches($baselineText, 'OK   \d+ sibling script\(s\) parse cleanly').Count -eq 1)

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host ('=' * 70)
Write-Host 'SELFTEST SUMMARY'
foreach ($r in $results) {
    Write-Host ("  {0} {1}" -f $(if ($r.Ok) { 'PASS' } else { 'FAIL' }), $r.Name)
}
if ($failures.Count -eq 0) {
    Write-Host ("OVERALL = SELFTEST PASS ({0} case(s))" -f $results.Count)
    exit 0
}
Write-Host ("OVERALL = SELFTEST FAIL ({0} mismatch(es))" -f $failures.Count)
foreach ($f in $failures) { Write-Host "  - $f" }
exit 1
