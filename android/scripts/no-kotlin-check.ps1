#requires -Version 5.1
<#
.SYNOPSIS
    Treelearn Java-only guard.

.DESCRIPTION
    Fails when the Treelearn Android tree contains Kotlin source files, Kotlin
    Gradle plugin references, Kotlin version-catalog entries, or dependencies /
    built APKs that are Kotlin or ship Kotlin runtime bytecode.

    Exit codes:
      0 = PASS
      1 = FAIL (Kotlin detected, or the guard could not be executed in full)
      2 = PARTIAL (a requested stage was skipped: -SkipDependencyStage and/or
          -SkipApkStage). PARTIAL is NEVER a pass: a caller that reports it as one
          is claiming a guarantee the guard did not check.

    Design notes (all of these come from reproduced audit findings):
      * A clean POM is NOT proof of a Kotlin-free artifact. androidx.test:monitor
        1.6.1 declares no Kotlin dependency yet ships Kotlin bytecode; the
        androidTest APK then dies on device with
        ClassNotFoundException: kotlin.jvm.internal.Lambda. The dependency stage
        therefore delegates to Gradle task `verifyNoKotlinDex`, which scans the
        dex string tables of the APKs that actually get installed.
      * Native commands MUST NOT be run with $ErrorActionPreference='Stop'.
        Gradle writes harmless javac notes ("uses or overrides a deprecated
        API") to stderr; with 'Stop' plus 2>&1 that becomes a terminating
        NativeCommandError and the guard aborts with exit 1 and no verdict.
        All external processes go through Invoke-NativeCaptured below.
      * Comments are stripped with a Groovy lexer state machine covering ' " '''
        """ and /slashy/ strings plus // and /* */ comments. A line-start-only
        heuristic desynchronises on a string containing "/*" and then silently
        skips real dependency lines.

        ROUND-3 AUDIT FINDING (reproduced, fixed here): the slashy-string branch
        was unreachable. `'=(,:[' -contains $prev` is a COLLECTION containment
        test in PowerShell, not a substring test, so it evaluated to False for
        every character and the lexer never entered the slashy state. A legal
        Groovy line such as `def re = /a\/*b/` then desynchronised at `\/*`,
        swallowed every following line, and the guard reported PASS on a file
        containing `releaseImplementation 'org.jetbrains.kotlin:...'`.
        Fixed by using .IndexOf() on the character set, and hardened by the two
        fail-closed measures below so that ANY future lexer desync is reported
        instead of silently hiding lines.

      * Fail-closed measure 1 (lexer trust): if the lexer has not returned to
        the code state (or a plain line comment) at end of file, the scan of
        that file is not trusted and the guard FAILS.
      * Fail-closed measure 2 (independent raw scan): every dependency-ish line
        carrying a quoted Kotlin coordinate or a -ktx artifact is detected by a
        regex that runs on the RAW text and never consults the comment lexer.
        Comment stripping can therefore not hide a Kotlin dependency again.
        A raw hit is only downgraded to NOTICE when the raw line itself starts
        with `//`, which no lexer state can spoof.

        ROUND-5 AUDIT FINDING (reproduced, fixed here): the downgrade used to be
        justified by "the stripped line is empty AND the lexer finished clean",
        i.e. the lexer was asked to vouch for its own miss. Legal Groovy beats
        that -- a `$/ /* /$` dollar-slashy puts the lexer into block-comment
        state and a later `$/ */$` resynchronises it, so a real
        `compileOnly 'org.jetbrains.kotlin:...'` line between them stripped to
        empty and the guard printed NOTICE + VERDICT: PASS.

      * Version catalogs (gradle/libs.versions.toml) are scanned: they are a
        Kotlin plugin entry point (alias(libs.plugins.kotlin.android)) that an
        extension filter of .gradle/.properties alone misses.
      * Declarations that REMOVE Kotlin (exclude group: '<kotlin group>') are
        legitimate Java-only measures and are reported as NOTICE, not as FAIL.

    SCOPE HONESTY (do not over-trust stage 2):
      Stage 2 is a fast TEXT PRE-FILTER, not a proof. A round-4 audit showed
      that legal Groovy can build a Kotlin coordinate that no line-based text
      scan can see: unicode escapes ('org.jetbrains.ko\u0074lin:...'), GString
      interpolation ("org.jetbrains.${'kotlin'}:..."), string concatenation
      ('org.jetbrains.' + 'kotlin:...'), coordinates split across statements,
      and a `$/.../$` dollar-slashy that desynchronises the comment lexer and
      later resynchronises on a `*/`.
      The AUTHORITATIVE Java-only proof is therefore:
        - stage 3  : every VARIANT CLASSPATH configuration of every module
                     (CompileClasspath / RuntimeClasspath /
                     AnnotationProcessorClasspath -- selected by pattern so a new
                     build type or flavour is covered automatically, with
                     build-tooling configurations excluded by name because AGP and
                     Gradle are themselves written in Kotlin), and
        - stage 3b : the dex string tables of EVERY APK anywhere under the APK
                     output tree (recursive discovery, plus an assertion that the
                     three expected artifacts still exist).
      Both are derived from what Gradle actually resolved and produced, which is
      why no text trick can evade them. Two separate blind spots came from
      hand-maintained lists instead of patterns (round 5: a hand-picked list of
      three runtime classpaths left `compileOnly` unverified; a hand-picked list
      of three APK directories never scanned a staged APK). Both stages now
      select by pattern AND assert that the selection was non-empty, so a stale
      pattern fails loudly instead of verifying nothing.
      Never treat a stage-2 PASS as sufficient, and never run this script with
      -SkipDependencyStage and report the result as a PASS (it exits 2, PARTIAL).

    EVIDENCE INTEGRITY (round 6):
      Stage 3b used to judge `gradlew verifyNoKotlinDex` by its EXIT CODE alone,
      and a probe showed the stage could therefore print PASS having inspected
      nothing: an up-to-date, skipped or renamed task also exits 0 while printing
      no judgement line. The stage now asserts the EVIDENCE (see
      Get-ApkDexStageEvidence in lib/evidence-integrity.ps1). The verdict section
      additionally re-reads every OK line and refuses to certify a run whose proof
      lines still carry an un-substituted {N} placeholder, because such a line
      reads as proof while carrying no number.
      Both helpers live in scripts/lib/evidence-integrity.ps1 and are imported
      here rather than re-implemented, so that guard-selftest.ps1 fires the SAME
      functions: a round-6 audit found the self-test was exercising a copy that
      existed only inside the self-test, which made that case a false witness.

    SCRIPT PARSE GATE (round 6):
      Stage 0 parses every sibling .ps1 before anything else runs, because a
      script cannot verify its own syntax. See the comment above stage 0 for the
      measured failure that caused it: a misplaced continuation `+` made this
      guard, the self-test and the shared library unparseable in one keystroke,
      and the symptom was no output at all. Stage 0 covers the siblings;
      guard-selftest.ps1 covers this file; the self-test asserts stage 0 ran.

    NOTE: this script intentionally contains ASCII-only text so it stays
    readable under Windows PowerShell 5.1 regardless of file encoding.
#>
[CmdletBinding()]
param(
    [string] $AndroidRoot = '',
    [switch] $SkipDependencyStage,
    # Test-only affordance: lets the self-test probe exercise the dependency stage
    # on its own. Like -SkipDependencyStage it can never yield PASS (the verdict
    # becomes PARTIAL), so it cannot be used to claim a clean bill of health.
    [switch] $SkipApkStage
)

$ErrorActionPreference = 'Stop'

# Evidence-integrity helpers shared with the self-test. They are imported from a
# file rather than defined here so the self-test fires the SAME functions this
# guard runs: a round-6-independent audit found the self-test was exercising a copy
# that existed only inside the self-test, which made it a false witness.
. (Join-Path $PSScriptRoot 'lib\evidence-integrity.ps1')

if ([string]::IsNullOrWhiteSpace($AndroidRoot)) {
    $AndroidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}
else {
    $AndroidRoot = (Resolve-Path $AndroidRoot).Path
}

$problems = New-Object System.Collections.Generic.List[string]
$notices = New-Object System.Collections.Generic.List[string]

# Every OK line is a proof line, so they are recorded as well as printed: the
# verdict section re-reads them and refuses to certify a run whose proof lines
# still carry an un-substituted {N} placeholder.
$okLines = New-Object System.Collections.Generic.List[string]

function Write-Section([string] $Text) {
    Write-Host ''
    Write-Host "== $Text =="
}

function Write-Ok([string] $Text) {
    $line = "OK   $Text"
    Write-Host $line
    $okLines.Add($line)
}

# Run an external command without letting its stderr become a terminating error.
function Invoke-NativeCaptured([string] $Executable, [string[]] $CommandArguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Executable @CommandArguments 2>&1
        return @{ ExitCode = $LASTEXITCODE; Text = ($output | Out-String) }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

# Keep a failing child process auditable: surface a short stderr-ish excerpt so
# a build lock or a missing SDK never looks like an unexplained "exit 1".
function Get-FailureExcerpt([string] $Text, [int] $MaxLines = 6) {
    $lines = @($Text -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 })
    if ($lines.Count -eq 0) { return '(no output captured)' }
    $tail = @($lines | Select-Object -Last $MaxLines)
    return (($tail | ForEach-Object { '        ' + $_.Trim() }) -join [Environment]::NewLine)
}

# ---------------------------------------------------------------------------
# Groovy comment stripping that preserves line numbering.
# ---------------------------------------------------------------------------
function Remove-GroovyComments {
    param(
        [string] $Text,
        [ref]    $FinalState
    )

    $sb = New-Object System.Text.StringBuilder
    $i = 0
    $n = $Text.Length
    $state = 'code'

    # Characters that may legally precede a Groovy slashy string.
    # NOTE: must be tested with .IndexOf(), never with the -contains operator.
    $slashyPreceders = '=(,:;[{~!&|?<>'

    while ($i -lt $n) {
        $c = $Text[$i]
        $n1 = if (($i + 1) -lt $n) { $Text[$i + 1] } else { [char]0 }
        $n2 = if (($i + 2) -lt $n) { $Text[$i + 2] } else { [char]0 }
        switch ($state) {
            'code' {
                if (($c -eq '/') -and ($n1 -eq '/')) { $state = 'line'; $i += 2; continue }
                # A block comment wins over a slashy string: Groovy itself treats
                # "/*" as a comment opener even after "=" or "(".
                if (($c -eq '/') -and ($n1 -eq '*')) { $state = 'block'; $i += 2; continue }
                if (($c -eq "'") -and ($n1 -eq "'") -and ($n2 -eq "'")) { $state = 'tsq'; $i += 3; continue }
                if (($c -eq '"') -and ($n1 -eq '"') -and ($n2 -eq '"')) { $state = 'tdq'; $i += 3; continue }
                if ($c -eq "'") { $state = 'sq'; [void]$sb.Append($c); $i++; continue }
                if ($c -eq '"') { $state = 'dq'; [void]$sb.Append($c); $i++; continue }
                if ($c -eq '/') {
                    # Groovy slashy string: only after an operator or delimiter,
                    # never after a value (a / b is plain division).
                    $k = $i - 1
                    while ($k -ge 0 -and [char]::IsWhiteSpace($Text[$k])) { $k-- }
                    if ($k -lt 0) {
                        $state = 'slashy'; $i++; continue
                    }
                    $prev = $Text[$k]
                    if ($slashyPreceders.IndexOf($prev) -ge 0) {
                        $state = 'slashy'; $i++; continue
                    }
                }
                [void]$sb.Append($c); $i++
            }
            'line' {
                if ($c -eq "`n") { $state = 'code'; [void]$sb.Append("`n") }
                $i++
            }
            'block' {
                if (($c -eq '*') -and ($n1 -eq '/')) { $state = 'code'; $i += 2; continue }
                if ($c -eq "`n") { [void]$sb.Append("`n") }
                $i++
            }
            'sq' {
                if ($c -eq '\') { [void]$sb.Append('  '); $i += 2; continue }
                if ($c -eq "'") { $state = 'code' }
                [void]$sb.Append($c); $i++
            }
            'dq' {
                if ($c -eq '\') { [void]$sb.Append('  '); $i += 2; continue }
                if ($c -eq '"') { $state = 'code' }
                [void]$sb.Append($c); $i++
            }
            'tsq' {
                if (($c -eq "'") -and ($n1 -eq "'") -and ($n2 -eq "'")) { $state = 'code'; $i += 3; continue }
                [void]$sb.Append($c); $i++
            }
            'tdq' {
                if (($c -eq '"') -and ($n1 -eq '"') -and ($n2 -eq '"')) { $state = 'code'; $i += 3; continue }
                [void]$sb.Append($c); $i++
            }
            'slashy' {
                if ($c -eq '\') { [void]$sb.Append('  '); $i += 2; continue }
                if ($c -eq '/') { $state = 'code'; $i++; continue }
                [void]$sb.Append($c); $i++
            }
        }
    }

    $FinalState.Value = $state
    return $sb.ToString()
}

Write-Host 'Treelearn Java-only guard'
Write-Host "Android root: $AndroidRoot"

# ---------------------------------------------------------------------------
# 0. Sibling scripts must parse.
#
#    A script cannot verify its own syntax: an unparseable script never reaches
#    its first statement. So this stage covers the SIBLINGS only, and
#    scripts/dev/guard-selftest.ps1 covers this file. The pair is the gate;
#    neither half is sufficient, and the self-test asserts that this stage ran
#    (by re-reading a guard run's output, not by grepping this source).
#
#    Measured, not theorised: a round-6 fix moved a `+` from the end of a
#    continuation line to the start of the next one inside an open '(' and every
#    .ps1 under scripts/ stopped parsing at once. In PowerShell an expression that
#    is already complete ends the statement at the newline, so the next line
#    beginning with '+' became a new statement and the '(' was never closed. The
#    guard then died with a ParseException and printed no evidence at all, while
#    everything around it looked finished.
# ---------------------------------------------------------------------------
Write-Section 'Script parse check'
$siblingScripts = @(
    Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -File -Filter '*.ps1' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -ne $PSCommandPath }
)
if ($siblingScripts.Count -eq 0) {
    $problems.Add("no sibling .ps1 found under $PSScriptRoot, so the parse check verified nothing; " +
        "either the scripts moved or this stage has gone stale")
}
else {
    $unparsedScripts = 0
    foreach ($sibling in $siblingScripts) {
        $siblingTokens = $null
        $siblingErrors = $null
        [void][System.Management.Automation.Language.Parser]::ParseFile(
            $sibling.FullName, [ref]$siblingTokens, [ref]$siblingErrors)
        foreach ($siblingError in $siblingErrors) {
            $unparsedScripts++
            $problems.Add("$($sibling.Name) does not parse, so it cannot run at all: line " +
                "$($siblingError.Extent.StartLineNumber) col " +
                "$($siblingError.Extent.StartColumnNumber): $($siblingError.Message)")
        }
    }
    if ($unparsedScripts -eq 0) {
        Write-Ok ("$($siblingScripts.Count) sibling script(s) parse cleanly " +
            "(this file excluded: an unparseable guard never reaches this line)")
    }
}

# ---------------------------------------------------------------------------
# 1. Kotlin source files.
# ---------------------------------------------------------------------------
Write-Section 'Kotlin source files'
$kotlinFiles = @(
    Get-ChildItem -LiteralPath $AndroidRoot -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Extension -in '.kt', '.kts' -and
            $_.FullName -notmatch '[\\/]build[\\/]' -and
            $_.FullName -notmatch '[\\/]\.gradle[\\/]'
        }
)
if ($kotlinFiles.Count -eq 0) {
    Write-Ok 'no .kt / .kts / .gradle.kts source files'
}
else {
    foreach ($f in $kotlinFiles) { $problems.Add("Kotlin source file: $($f.FullName)") }
}

# ---------------------------------------------------------------------------
# 2. Kotlin declarations in Gradle scripts and version catalogs.
# ---------------------------------------------------------------------------
Write-Section 'Gradle scripts and version catalogs'

$kotlinGroupId = 'org' + '.' + 'jetbrains' + '.' + 'kotlin'
$kotlinGroupRe = [regex]::Escape($kotlinGroupId)

# Patterns that indicate an actual Kotlin declaration or Kotlin-only tooling.
$kotlinDeclarationPatterns = @(
    $kotlinGroupRe,                                            # org.jetbrains.kotlin
    '\bkotlin-(android|kapt|parcelize|platform|stdlib|test)\b', # kotlin-android, kotlin-kapt, ...
    'id\s*\(?\s*["'']kotlin',                                  # id 'kotlin-...' / id("kotlin")
    '(?<![\w.])kotlin\s*\(',                                   # plugins { kotlin("android") }
    'apply\s+plugin\s*:\s*["'']kotlin',
    '\bkapt\b',
    '\bksp\b',
    '\bkotlin\s*\{',                                           # kotlin { ... } extension
    '\bkotlinOptions\b',
    '\blibs(\.[A-Za-z0-9_]+)*\.kotlin',
    '\bkotlin\s+version\b',
    '\bkotlinVersion\b',
    '^\s*kotlin\.'                                             # kotlin.code.style etc.
)
# A line that REMOVES Kotlin names the group as the value of group:/module:.
# Anything else carrying the group string is a real coordinate, not a removal.
$kotlinRemovalRe = '(?i)(?:group|module)\s*[:=]\s*[''"]' + $kotlinGroupRe + '[''"]'
$kotlinCoordinateRe = '[''"]' + $kotlinGroupRe + ':[^''"]+[''"]'
# A Kotlin extension artifact carried by any dependency-ish declaration.
$ktxDependencyRe = '(?i)(implementation|api|compileOnly|compileOnlyApi|runtimeOnly|annotationProcessor|classpath|testImplementation|androidTestImplementation|debugImplementation|releaseImplementation)\b.*-ktx'

# Fail-closed measure 2: raw-text scan, independent of the comment lexer.
# Matches a quoted coordinate containing "kotlin" or a "-ktx" artifact on a
# dependency-declaration line. Deliberately does NOT consult Remove-GroovyComments,
# so a lexer desync can never hide a Kotlin dependency again.
$rawKotlinDependencyRe = '(?i)(?:implementation|api|compileOnly|compileOnlyApi|runtimeOnly|classpath|annotationProcessor|testImplementation|testCompileOnly|testRuntimeOnly|androidTestImplementation|androidTestCompileOnly|androidTestRuntimeOnly|debugImplementation|releaseImplementation)\s*\(?\s*["''][^"'']*(?:' + $kotlinGroupRe + '|-ktx)[^"'']*["'']'

$gradleScripts = @(
    Get-ChildItem -LiteralPath $AndroidRoot -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in '.gradle', '.properties', '.toml' } |
        Where-Object { $_.FullName -notmatch '[\\/]build[\\/]' -and $_.FullName -notmatch '[\\/]\.gradle[\\/]' }
)

if ($gradleScripts.Count -eq 0) {
    $problems.Add("No Gradle build scripts found under $AndroidRoot")
}
else {
    foreach ($script in $gradleScripts) {
        $raw = [System.IO.File]::ReadAllText($script.FullName)
        $flaggedLines = New-Object System.Collections.Generic.HashSet[int]

        if ($script.Extension -eq '.gradle') {
            $finalState = 'code'
            $stripped = Remove-GroovyComments -Text $raw -FinalState ([ref]$finalState)
            if ($finalState -notin @('code', 'line')) {
                # Fail-closed measure 1: an unterminated comment/string means the
                # lexer never resynchronised, so later lines may be hidden.
                $problems.Add("Groovy lexer did not return to the code state for $($script.Name) (final state: $finalState); the stripped scan of this file is not trustworthy")
            }
        }
        else {
            # .properties / .toml use '#' comments.
            $stripped = (($raw -split "`r?`n") | ForEach-Object { $_ -replace '#.*$', '' }) -join "`n"
        }

        $lines = $stripped -split "`r?`n"
        for ($idx = 0; $idx -lt $lines.Count; $idx++) {
            $line = $lines[$idx]
            $lineNo = $idx + 1
            $trimmed = $line.Trim()
            if ($trimmed.Length -eq 0) { continue }

            $hit = $null
            foreach ($pattern in $kotlinDeclarationPatterns) {
                if ($line -match $pattern) { $hit = $pattern; break }
            }
            if ($null -ne $hit) {
                # A genuine removal rule names the group as the value of
                # group:/module: and carries NO Kotlin coordinate. A line that
                # only mentions "exclude" while actually declaring a Kotlin
                # coordinate is a real dependency (round-4 audit finding).
                $isRemovalOnly = ($line -match $kotlinRemovalRe) -and ($line -notmatch $kotlinCoordinateRe)
                if ($isRemovalOnly) {
                    $notices.Add("Kotlin removal rule (allowed): $($script.Name):$lineNo -> $trimmed")
                }
                else {
                    $problems.Add("Kotlin declaration: $($script.Name):$lineNo -> $trimmed")
                    [void]$flaggedLines.Add($idx)
                }
            }
            if ($line -match $ktxDependencyRe) {
                $problems.Add("Kotlin extension dependency: $($script.Name):$lineNo -> $trimmed")
                [void]$flaggedLines.Add($idx)
            }
        }

        # Fail-closed measure 2: re-scan the RAW text for quoted Kotlin coordinates.
        if ($script.Extension -eq '.gradle') {
            $rawLines = $raw -split "`r?`n"
            for ($idx = 0; $idx -lt $rawLines.Count; $idx++) {
                if ($flaggedLines.Contains($idx)) { continue }
                $rawLine = $rawLines[$idx]
                if ($rawLine -match $rawKotlinDependencyRe) {
                    $lineNo = $idx + 1
                    $trimmedRaw = $rawLine.Trim()
                    if (($rawLine -match $kotlinRemovalRe) -and ($rawLine -notmatch $kotlinCoordinateRe)) {
                        $notices.Add("Kotlin removal rule (allowed, raw scan): $($script.Name):$lineNo -> $trimmedRaw")
                    }
                    elseif ($rawLine.TrimStart().StartsWith('//')) {
                        # ONLY a raw line comment may be downgraded, and the
                        # criterion must not consult the lexer at all.
                        #
                        # ROUND-5 AUDIT FINDING (reproduced, fixed here): the
                        # previous criterion was `stripped line is empty AND the
                        # lexer finished in state code`, i.e. the lexer was asked
                        # to vouch for its own possible miss. Legal Groovy defeats
                        # it:
                        #     dependencies {
                        #         def s = $/ /* /$          // lexer enters block comment
                        #         compileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'
                        #         def t = $/ */$            // lexer resynchronises
                        #     }
                        # The real dependency line strips to empty, the final state
                        # is 'code', and the guard printed NOTICE + VERDICT: PASS.
                        # A line whose raw text starts with `//` is a Groovy line
                        # comment no matter what any lexer believes, and the rest of
                        # that line can never be an active dependency -- so this
                        # criterion cannot be spoofed.
                        #
                        # Deliberate consequence: a Kotlin coordinate commented out
                        # inside a /* ... */ block now FAILS. That is the
                        # fail-closed direction on purpose; delete such lines.
                        $notices.Add("Kotlin coordinate inside a line comment (not a dependency): $($script.Name):$lineNo -> $trimmedRaw")
                    }
                    else {
                        $problems.Add("Kotlin dependency coordinate (raw scan, lexer-independent): $($script.Name):$lineNo -> $trimmedRaw")
                    }
                }
            }
        }
    }
    Write-Ok "scanned $($gradleScripts.Count) Gradle script/catalog file(s), comments excluded"
    Write-Ok 'raw-text dependency scan completed (lexer-independent)'
    Write-Ok 'lexer trust check completed (fail-closed on desync)'
}

# ---------------------------------------------------------------------------
# 3a. Resolved dependency report: no Kotlin coordinates may appear.
# ---------------------------------------------------------------------------
$skipStage = $SkipDependencyStage.IsPresent
if (-not $skipStage) {
    Write-Section 'Resolved dependency report'
    $gradlew = Join-Path $AndroidRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradlew)) {
        $problems.Add("Gradle wrapper not found at $gradlew ; cannot verify resolved dependencies")
    }
    else {
        # ROUND-5 AUDIT FINDING (reproduced, fixed here), two halves:
        #
        # (a) This stage used to run `:app:dependencies --configuration <one of 3
        #     runtime classpaths>`. That left every non-runtime classpath
        #     unverified -- an independent audit shipped
        #         compileOnly "org.jetbrains.${'kotlin'}:kotlin-stdlib:2.0.0"
        #     (a GString, so the static stage cannot see it) and the complete
        #     guard still printed VERDICT: PASS, because compileOnly never
        #     appears on a runtime classpath.
        # (b) The first fix for (a) scanned EVERY configuration, which is also
        #     wrong: Gradle's own build-tooling configurations
        #     (`_internal-unified-test-platform-*`, the buildscript `classpath`,
        #     `detachedConfiguration*`) legitimately contain Kotlin, because AGP
        #     itself is written in Kotlin. Scanning them reported 822 "violations"
        #     for a tree that is in fact Kotlin-free.
        #
        # So the scan selects configurations by PATTERN, not by a hand-written
        # list: a variant classpath ends in CompileClasspath / RuntimeClasspath /
        # AnnotationProcessorClasspath, and build tooling is excluded by name.
        # A new build type or product flavour is therefore picked up
        # automatically, while `_internal-*` / buildscript / detached tooling is
        # never mistaken for app code. To stop the pattern from silently going
        # stale, the stage FAILS if it matched no configuration at all.
        $appClasspathRe = '(Compile|Runtime|AnnotationProcessor)Classpath$'
        $buildToolingRe = '^(_internal|detachedConfiguration|classpath$)'

        $modules = @(':app', ':core', ':data', ':ai', ':ai-protocol', ':tree-layout')
        $dependencyTasks = @($modules | ForEach-Object { "$($_):dependencies" })
        $result = Invoke-NativeCaptured $gradlew (@(
                '-p', $AndroidRoot, '--no-daemon', '--console=plain', '-q') + $dependencyTasks)
        if ($result.ExitCode -ne 0) {
            $problems.Add("Full dependency report failed (exit $($result.ExitCode)); excerpt:")
            $problems.Add((Get-FailureExcerpt $result.Text))
        }
        else {
            # The report is grouped as:
            #     <configurationName> - <description>
            #         +--- group:artifact:version
            #         |    \--- transitive
            #         \--- project :core
            # Track the current project and configuration, and only match on TREE
            # lines, so a configuration whose NAME contains "kotlin" cannot cause
            # a false hit.
            $projectHeaderRe = '^Project '':([A-Za-z0-9_.-]+)''$'
            $configHeaderRe = '^([A-Za-z_][A-Za-z0-9_.-]*)\s+-\s'
            $treeLineRe = '^\s*(?:\|\s+)*(?:\+---|\\---)\s*(.+)$'
            $currentModule = ''
            $currentConfiguration = ''
            $scannedConfigurations = [ordered]@{}
            $skippedToolingConfigurations = [ordered]@{}
            $hitsByConfiguration = [ordered]@{}
            $reportLines = 0
            foreach ($row in ($result.Text -split "`r?`n")) {
                $line = "$row".TrimEnd()
                $reportLines++
                if ($line -match $projectHeaderRe) { $currentModule = $matches[1]; continue }
                if ($line -match $configHeaderRe) {
                    $candidate = $matches[1]
                    if ($candidate -match $buildToolingRe) {
                        $currentConfiguration = ''
                        $skippedToolingConfigurations["$currentModule / $candidate"] = $true
                    }
                    elseif ($candidate -match $appClasspathRe) {
                        $currentConfiguration = $candidate
                        $scannedConfigurations["$currentModule / $candidate"] = $true
                    }
                    else {
                        $currentConfiguration = ''
                    }
                    continue
                }
                if ($currentConfiguration.Length -eq 0) { continue }
                if ($line -match $treeLineRe) {
                    $coordinate = $matches[1].Trim()
                    if ($coordinate -match $kotlinGroupRe -or $coordinate -match '-ktx:') {
                        $key = "$currentModule / $currentConfiguration"
                        if (-not $hitsByConfiguration.Contains($key)) {
                            $hitsByConfiguration[$key] = New-Object System.Collections.Generic.List[string]
                        }
                        $hitsByConfiguration[$key].Add($coordinate)
                    }
                }
            }
            if ($reportLines -lt 50) {
                # A report this short means the task did not actually run.
                $problems.Add("Full dependency report looks empty ($reportLines line(s)); the dependency stage cannot be trusted")
            }
            elseif ($scannedConfigurations.Count -eq 0) {
                $problems.Add("No variant classpath configuration matched '$appClasspathRe' across $($modules -join ', '); " +
                        "the classpath pattern is stale and the dependency stage verified nothing")
            }
            elseif ($hitsByConfiguration.Count -eq 0) {
                # TRAP (measured, not theorised): in PowerShell `-f` binds TIGHTER than
                # `+`, so `"a {0}" + "b {2}" -f $x, $y, $z` formats only the second
                # operand. The first half then keeps its {0}/{1} placeholders and the
                # evidence silently loses the numbers it exists to prove. Verified by
                # running `'a{0}' + 'b{2}' -f 1,2,3` -> "a{0}b3". Always interpolate
                # with $(...) instead of mixing `+` with `-f`.
                # Write-Ok, not Write-Host, because these are proof lines like any
                # other. Written with Write-Host they were counted in nothing: the
                # final "VERDICT: PASS (N OK line(s))" understated the verified
                # scale, and the verdict section's {N} placeholder check never saw
                # them. A proof line that no integrity check can reach is the same
                # defect class as a PASS judged on an exit code.
                Write-Ok ("no Kotlin coordinates on $($scannedConfigurations.Count) " +
                        "variant classpath configuration(s) across $($modules -join ', ') " +
                        "(build tooling configurations excluded, $reportLines report lines)")
                Write-Ok ("excluded $($skippedToolingConfigurations.Count) build-tooling configuration(s) " +
                        "(AGP/Gradle are Kotlin themselves; they do not ship in the app)")
            }
            else {
                foreach ($key in $hitsByConfiguration.Keys) {
                    $problems.Add("Kotlin coordinates on ${key}:")
                    foreach ($h in $hitsByConfiguration[$key]) { $problems.Add("    $h") }
                }
            }
        }

        # -------------------------------------------------------------------
        # 3b. Final artifacts: built APKs must contain no Kotlin bytecode.
        # -------------------------------------------------------------------
        if ($SkipApkStage.IsPresent) {
            Write-Section 'Built APK dex tables'
            Write-Host 'SKIP verifyNoKotlinDex requested (-SkipApkStage); the verdict will be PARTIAL, never PASS'
        }
        else {
            Write-Section 'Built APK dex tables'
            $result = Invoke-NativeCaptured $gradlew @(
                '-p', $AndroidRoot, '--no-daemon', '--console=plain', '-q', 'verifyNoKotlinDex')
            # The evidence, not the exit code, is what gets judged here. See
            # Get-ApkDexStageEvidence in lib/evidence-integrity.ps1: `-q` plus an up-to-date,
            # skipped or renamed task all exit 0 while inspecting nothing, and a stage that
            # certifies "no built APK contains Kotlin runtime bytecode" on evidence it never saw is
            # the exact defect class AGENTS.md records three times. The same function is fired by
            # the self-test on synthetic input, so this branch cannot regress unnoticed.
            $evidence = Get-ApkDexStageEvidence -Text $result.Text -ExitCode $result.ExitCode
            foreach ($line in $evidence.JudgementLines) {
                Write-Ok $line
                if ($line -match 'NOTICE') { $notices.Add($line) }
            }
            foreach ($problem in $evidence.Problems) {
                $problems.Add($problem)
                if ($result.ExitCode -ne 0) {
                    $problems.Add((Get-FailureExcerpt $result.Text))
                }
            }
            if ($evidence.Problems.Count -eq 0) {
                Write-Ok ("no built APK contains Kotlin runtime bytecode " +
                    "($($evidence.ApkCount) APK(s) inspected, " +
                    "$($evidence.JudgementLines.Count) judgement line(s))")
            }
        }
    }
}

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
Write-Section 'Result'

# Evidence integrity, applied to this run's own proof lines. The OK lines are the
# only record of the SCALE of what was verified (variant classpath count, report
# line count, APK count), and an OK line left with a literal {N} placeholder is a
# false witness: it reads as proof while carrying no number. The helper is shared
# with the self-test, which fires it on synthetic input, so this branch is known to
# be able to fire rather than assumed to be working.
$unreplaced = Get-UnreplacedPlaceholderLine -Text $okLines
if ($unreplaced.Count -gt 0) {
    foreach ($line in $unreplaced) {
        $problems.Add("an OK line still carries an un-substituted format placeholder, so it proves " +
            "no number: $line")
    }
}

foreach ($n in $notices) { Write-Host "NOTICE $n" }

if ($problems.Count -gt 0) {
    foreach ($p in $problems) { Write-Host "FAIL $p" }
    Write-Host ''
    Write-Host "VERDICT: FAIL ($($problems.Count) problem(s))"
    exit 1
}

if ($skipStage -or $SkipApkStage.IsPresent) {
    $skipped = @()
    if ($skipStage) { $skipped += 'dependency report' }
    if ($SkipApkStage.IsPresent) { $skipped += 'APK dex scan' }
    Write-Host "VERDICT: PARTIAL (skipped by request: $($skipped -join ', '))"
    exit 2
}

Write-Host "VERDICT: PASS ($($okLines.Count) OK line(s))"
exit 0
