#requires -Version 5.1
<#
.SYNOPSIS
    Treelearn shared-JVM module structure guard (task T-2026-10-10-005).

.DESCRIPTION
    Proves the single-implementation contract for the pure-JVM modules that
    both the Android build and the web build consume:

      1. Sources live under web/shared/domain (:core),
         web/shared/tree-layout (:tree-layout) and web/shared/ai-protocol
         (:ai-protocol), each with a build file and at least one .java file.
      2. android/settings.gradle includes ':core', ':tree-layout' and
         ':ai-protocol' and points their projectDir at web/shared/...
         (a reference, never a copy).
      3. No duplicate source copy remains at android/core or android/tree-layout,
         and every class relocated into :ai-protocol (task T-2026-10-10-017)
         lives only under web/shared/ai-protocol and not back under android/ai
         (android/ai legitimately keeps ProviderJson, JsonChatRequestBody and
         DeepSeekGatewayFactory, so a blanket "no sources under android/ai"
         check is not used there).
      4. No Kotlin source file and no Kotlin declaration under web/shared
         (plus web/backend when present): the Android guard only scans
         android/**, so the shared tree needs its own Java-only gate. .kt files
         are especially dangerous here because neither Gradle build applies a
         Kotlin plugin - such files are silently ignored, not compiled.
         Scope honesty: the declaration scan is a line-based text pre-filter,
         not a lexer. The authoritative Kotlin proof for the shared tree is the
         absence of any Kotlin plugin/coordinate plus the .kt file check above.
      5. When web/backend/settings.gradle exists it must consume the same
         shared directories (verified, not assumed; NOTICE while it is absent,
         because task T-2026-10-10-003 owns that file).

    Exit codes:
      0 = PASS
      1 = FAIL (structure or Kotlin violation; a check that found nothing to
          verify is also FAIL - a vacuous check must never print PASS)
      3 = INCONCLUSIVE (the -Selftest harness itself could not run: fixture
          not clean, or fixture setup failed). Distinct from 1 on purpose:
          "did not run" and "ran and found violations" are different verdicts.

    -Selftest: builds synthetic fixtures and asserts that every violation
    branch, when injected, turns the verdict to FAIL. A branch that cannot fail
    is a dead branch (AGENTS.md failure library, guard selftest requirement).

    ASCII-only on purpose: Windows PowerShell 5.1 decodes a BOM-less .ps1 with
    the system ANSI code page; non-ASCII text can then break parsing entirely
    and the script never reaches its first statement.
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = '',
    [switch] $Selftest
)

$ErrorActionPreference = 'Stop'

function Invoke-SharedStructureCheck {
    param([Parameter(Mandatory = $true)][string] $Root)

    $problems = New-Object System.Collections.Generic.List[string]
    $oks = New-Object System.Collections.Generic.List[string]
    $notices = New-Object System.Collections.Generic.List[string]

    $modules = @(
        @{ Name = ':core';        DirName = 'domain';      Dir = 'web/shared/domain' },
        @{ Name = ':tree-layout'; DirName = 'tree-layout'; Dir = 'web/shared/tree-layout' },
        @{ Name = ':ai-protocol'; DirName = 'ai-protocol'; Dir = 'web/shared/ai-protocol' }
    )

    # --- check 1/2: module sources and build files exist and are non-empty ---
    $before = $problems.Count
    foreach ($m in $modules) {
        $moduleDir = Join-Path $Root $m.Dir
        $src = Join-Path (Join-Path $moduleDir 'src') 'main/java'
        if (-not (Test-Path -LiteralPath $src)) {
            $problems.Add("$($m.Name): missing source root '$($m.Dir)/src/main/java'")
        }
        else {
            $java = @(Get-ChildItem -LiteralPath $src -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue)
            if ($java.Count -eq 0) {
                $problems.Add("$($m.Name): '$($m.Dir)/src/main/java' contains no .java file, so this check verified nothing")
            }
            else {
                $oks.Add("$($m.Name): $($java.Count) .java file(s) live under $($m.Dir)")
            }
        }
        $buildFile = Join-Path $moduleDir 'build.gradle'
        if (-not (Test-Path -LiteralPath $buildFile)) {
            $problems.Add("$($m.Name): missing build file '$($m.Dir)/build.gradle'")
        }
    }

    # --- check 3: the Android build references the shared dirs ---
    $before = $problems.Count
    $settingsPath = Join-Path $Root 'android/settings.gradle'
    if (-not (Test-Path -LiteralPath $settingsPath)) {
        $problems.Add("android/settings.gradle not found under $Root")
    }
    else {
        $text = [System.IO.File]::ReadAllText($settingsPath)
        foreach ($m in $modules) {
            if (-not $text.Contains("include '$($m.Name)'")) {
                $problems.Add("android/settings.gradle does not include $($m.Name)")
            }
            if (-not $text.Contains("web/shared/$($m.DirName)")) {
                $problems.Add("android/settings.gradle does not point $($m.Name) at web/shared/$($m.DirName)")
            }
        }
    }
    if ($problems.Count -eq $before) {
        $oks.Add('android/settings.gradle references web/shared for every shared module')
    }

    # --- check 4a: no duplicate source copy ---
    $before = $problems.Count
    foreach ($legacy in @('android/core', 'android/tree-layout')) {
        $legacySrc = Join-Path $Root "$legacy/src/main/java"
        if (Test-Path -LiteralPath $legacySrc) {
            $problems.Add("duplicate source copy still present: $legacySrc")
        }
    }
    if ($problems.Count -eq $before) {
        $oks.Add('no duplicate source copy remains under android/core or android/tree-layout')
    }

    # --- check 4a-2: the ai-protocol classes were relocated, never copied ---
    # android/ai legitimately keeps the platform-coupled files (ProviderJson, JsonChatRequestBody,
    # DeepSeekGatewayFactory), so the generic "no sources under android/..." check above does not
    # apply. Instead each relocated class must live under web/shared/ai-protocol and must NOT
    # remain under android/ai: a re-introduced copy would silently diverge from the shared single
    # implementation (task T-2026-10-10-017).
    $before = $problems.Count
    $aiProtocolSharedSrc = Join-Path $Root 'web/shared/ai-protocol/src/main/java'
    $aiProtocolAndroidSrc = Join-Path $Root 'android/ai/src/main/java'
    $relocatedAiProtocol = @(
        'com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
        'com/treelearn/ai/prompt/DeepSeekMessage.java',
        'com/treelearn/ai/prompt/DeepSeekPromptRegistry.java',
        'com/treelearn/ai/prompt/DeepSeekRequest.java',
        'com/treelearn/ai/prompt/DeepSeekRequestAssembler.java',
        'com/treelearn/ai/prompt/JsonObjectText.java',
        'com/treelearn/ai/prompt/PromptBodiesV1.java',
        'com/treelearn/ai/prompt/PromptBudget.java',
        'com/treelearn/ai/prompt/PromptBuildException.java',
        'com/treelearn/ai/gateway/AiGateway.java',
        'com/treelearn/ai/gateway/AiGatewayError.java',
        'com/treelearn/ai/gateway/GatewayPorts.java',
        'com/treelearn/ai/gateway/ProviderEndpoint.java',
        'com/treelearn/ai/gateway/HttpsTransport.java',
        'com/treelearn/ai/gateway/SseFrameParser.java',
        'com/treelearn/ai/gateway/StreamOutcome.java',
        'com/treelearn/ai/gateway/GatewayEndpoints.java',
        'com/treelearn/ai/gateway/GenerationSink.java'
    )
    foreach ($rel in $relocatedAiProtocol) {
        $relNative = $rel -replace '/', [System.IO.Path]::DirectorySeparatorChar
        $sharedFile = Join-Path $aiProtocolSharedSrc $relNative
        if (-not (Test-Path -LiteralPath $sharedFile)) {
            $problems.Add(":ai-protocol: relocated class missing at web/shared/ai-protocol/src/main/java/$rel")
        }
        $copy = Join-Path $aiProtocolAndroidSrc $relNative
        if (Test-Path -LiteralPath $copy) {
            $problems.Add("duplicate ai-protocol source copy still present: android/ai/src/main/java/$rel")
        }
    }
    if ($problems.Count -eq $before) {
        $oks.Add("$($relocatedAiProtocol.Count) ai-protocol class(es) live only under web/shared/ai-protocol (no android/ai copy)")
    }

    # --- check 4b: no Kotlin in the shared Java tree ---
    $before = $problems.Count
    $scanRoots = @((Join-Path $Root 'web/shared'))
    $webBackend = Join-Path $Root 'web/backend'
    if (Test-Path -LiteralPath $webBackend) { $scanRoots += $webBackend }

    foreach ($scanRoot in $scanRoots) {
        $kotlinFiles = @(Get-ChildItem -LiteralPath $scanRoot -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Extension -in '.kt', '.kts' -and
                $_.FullName -notmatch '[\\/]build[\\/]'
            })
        foreach ($f in $kotlinFiles) {
            $problems.Add("Kotlin source file in the shared Java tree: $($f.FullName)")
        }
    }

    $gradleFiles = @()
    foreach ($scanRoot in $scanRoots) {
        $gradleFiles += @(Get-ChildItem -LiteralPath $scanRoot -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Extension -in '.gradle', '.properties', '.toml' -and
                $_.FullName -notmatch '[\\/]build[\\/]'
            })
    }
    if ($gradleFiles.Count -eq 0) {
        $problems.Add('no Gradle build/catalog file found under the shared tree, so the Kotlin declaration scan verified nothing')
    }
    else {
        $kotlinDeclarationPatterns = @(
            'org\.jetbrains\.kotlin',
            '\bkotlin-(android|kapt|parcelize|platform|stdlib|test)\b',
            '(?<![\w.])kotlin\s*\(',
            '\bkotlin\s*\{',
            '\bkapt\b',
            '\bksp\b',
            '\bkotlinOptions\b'
        )
        foreach ($gf in $gradleFiles) {
            $raw = [System.IO.File]::ReadAllText($gf.FullName)
            $lines = $raw -split "`r?`n"
            for ($i = 0; $i -lt $lines.Count; $i++) {
                $line = $lines[$i].Trim()
                if ($line.Length -eq 0 -or $line.StartsWith('//') -or $line.StartsWith('#')) { continue }
                foreach ($pattern in $kotlinDeclarationPatterns) {
                    if ($line -match $pattern) {
                        $problems.Add("Kotlin declaration in the shared tree: $($gf.Name):$($i + 1) -> $line")
                        break
                    }
                }
            }
        }
    }
    if ($problems.Count -eq $before) {
        $oks.Add("no Kotlin source file and no Kotlin declaration under the shared Java tree ($($gradleFiles.Count) build/catalog file(s) scanned)")
    }

    # --- check 5: web build consumes the shared dirs when it exists ---
    $before = $problems.Count
    $webSettings = Join-Path $Root 'web/backend/settings.gradle'
    if (Test-Path -LiteralPath $webSettings) {
        $text = [System.IO.File]::ReadAllText($webSettings)
        foreach ($m in $modules) {
            if (-not $text.Contains("shared/$($m.DirName)")) {
                $problems.Add("web/backend/settings.gradle does not reference shared/$($m.DirName)")
            }
        }
        if ($problems.Count -eq $before) {
            $oks.Add('web/backend/settings.gradle references the shared modules')
        }
    }
    else {
        $notices.Add('web/backend/settings.gradle not present yet (T-2026-10-10-003 owns it); the web-side reference check is deferred to that task')
    }

    return @{ Problems = $problems; Oks = $oks; Notices = $notices }
}

if ($Selftest) {
    $fixture = Join-Path $env:TEMP ("tl-shared-guard-" + (Get-Date -Format 'HHmmssfff'))
    $harnessProblems = New-Object System.Collections.Generic.List[string]
    $deadBranches = New-Object System.Collections.Generic.List[string]
    $caseCount = 0

    function Set-FixtureFile {
        param([string] $RelativePath, [string[]] $Lines)
        $full = Join-Path $fixture $RelativePath
        $dir = Split-Path -Parent $full
        if (-not (Test-Path -LiteralPath $dir)) {
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
        }
        Set-Content -LiteralPath $full -Encoding ascii -Value ($Lines -join "`n")
    }

    function Invoke-FixtureCase {
        param([string] $Name, [scriptblock] $Mutate, [scriptblock] $Restore)
        $script:caseCount = $script:caseCount + 1
        $before = (Invoke-SharedStructureCheck -Root $fixture).Problems.Count
        if ($before -ne 0) {
            $harnessProblems.Add("fixture not clean before case '$Name' ($before problem(s))")
            return
        }
        & $Mutate
        $after = (Invoke-SharedStructureCheck -Root $fixture).Problems.Count
        & $Restore
        if ($after -eq 0) {
            $deadBranches.Add("case '$Name' did not FAIL on its injected violation")
        }
        else {
            Write-Host "SELFTEST CASE OK: $Name ($after problem(s))"
        }
    }

    function Set-AiProtocolFixture {
        Set-FixtureFile 'web/shared/ai-protocol/build.gradle' @("plugins { id 'java-library' }")
        foreach ($rel in @(
            'prompt/DeepSeekContextEncoder.java',
            'prompt/DeepSeekMessage.java',
            'prompt/DeepSeekPromptRegistry.java',
            'prompt/DeepSeekRequest.java',
            'prompt/DeepSeekRequestAssembler.java',
            'prompt/JsonObjectText.java',
            'prompt/PromptBodiesV1.java',
            'prompt/PromptBudget.java',
            'prompt/PromptBuildException.java',
            'gateway/AiGateway.java',
            'gateway/AiGatewayError.java',
            'gateway/GatewayPorts.java',
            'gateway/ProviderEndpoint.java',
            'gateway/HttpsTransport.java',
            'gateway/SseFrameParser.java',
            'gateway/StreamOutcome.java',
            'gateway/GatewayEndpoints.java',
            'gateway/GenerationSink.java'
        )) {
            Set-FixtureFile "web/shared/ai-protocol/src/main/java/com/treelearn/ai/$rel" @('package com.treelearn.ai;')
        }
    }

    try {
        $okSettings = @(
            "include ':core'",
            "include ':tree-layout'",
            "include ':ai-protocol'",
            "project(':core').projectDir = file('../web/shared/domain')",
            "project(':tree-layout').projectDir = file('../web/shared/tree-layout')",
            "project(':ai-protocol').projectDir = file('../web/shared/ai-protocol')"
        )
        Set-FixtureFile 'web/shared/domain/src/main/java/A.java' @('class A {}')
        Set-FixtureFile 'web/shared/tree-layout/src/main/java/B.java' @('class B {}')
        Set-FixtureFile 'web/shared/domain/build.gradle' @("plugins { id 'java-library' }")
        Set-FixtureFile 'web/shared/tree-layout/build.gradle' @("plugins { id 'java-library' }")
        Set-AiProtocolFixture
        Set-FixtureFile 'android/settings.gradle' $okSettings

        $clean = Invoke-SharedStructureCheck -Root $fixture
        if ($clean.Problems.Count -ne 0) {
            $harnessProblems.Add("clean fixture must PASS but reported $($clean.Problems.Count) problem(s): $($clean.Problems -join ' | ')")
        }
        else {
            Write-Host "SELFTEST CASE OK: clean fixture passes ($($clean.Oks.Count) OK line(s))"
            $script:caseCount = $script:caseCount + 1
        }

        Invoke-FixtureCase 'missing domain source root' `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/shared/domain/src') -Recurse -Force } `
            { Set-FixtureFile 'web/shared/domain/src/main/java/A.java' @('class A {}') }

        Invoke-FixtureCase 'android settings without projectDir reference' `
            { Set-FixtureFile 'android/settings.gradle' @("include ':core'", "include ':tree-layout'") } `
            { Set-FixtureFile 'android/settings.gradle' $okSettings }

        Invoke-FixtureCase 'duplicate copy under android/core' `
            { Set-FixtureFile 'android/core/src/main/java/Dup.java' @('class Dup {}') } `
            { Remove-Item -LiteralPath (Join-Path $fixture 'android/core') -Recurse -Force }

        Invoke-FixtureCase 'Kotlin source file in shared module' `
            { Set-FixtureFile 'web/shared/domain/src/main/java/X.kt' @('class X') } `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/shared/domain/src/main/java/X.kt') -Force }

        Invoke-FixtureCase 'Kotlin coordinate in shared build file' `
            { Set-FixtureFile 'web/shared/domain/build.gradle' @("plugins { id 'java-library' }", "dependencies { implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0' }") } `
            { Set-FixtureFile 'web/shared/domain/build.gradle' @("plugins { id 'java-library' }") }

        Invoke-FixtureCase 'web backend settings missing shared reference' `
            { Set-FixtureFile 'web/backend/settings.gradle' @("rootProject.name = 'probe'") } `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/backend') -Recurse -Force }

        Invoke-FixtureCase 'missing ai-protocol source root' `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/shared/ai-protocol/src') -Recurse -Force } `
            { Set-AiProtocolFixture }

        Invoke-FixtureCase 'android settings without ai-protocol reference' `
            { Set-FixtureFile 'android/settings.gradle' @("include ':core'", "include ':tree-layout'", "project(':core').projectDir = file('../web/shared/domain')", "project(':tree-layout').projectDir = file('../web/shared/tree-layout')") } `
            { Set-FixtureFile 'android/settings.gradle' $okSettings }

        Invoke-FixtureCase 'relocated ai-protocol class missing from the shared tree' `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/shared/ai-protocol/src/main/java/com/treelearn/ai/gateway/AiGateway.java') -Force } `
            { Set-AiProtocolFixture }

        Invoke-FixtureCase 'relocated ai-protocol class copied back into android/ai' `
            { Set-FixtureFile 'android/ai/src/main/java/com/treelearn/ai/gateway/AiGateway.java' @('package com.treelearn.ai.gateway;') } `
            { Remove-Item -LiteralPath (Join-Path $fixture 'android/ai') -Recurse -Force }

        Invoke-FixtureCase 'empty Gradle file selection in shared tree' `
            { Remove-Item -LiteralPath (Join-Path $fixture 'web/shared/domain/build.gradle'),(Join-Path $fixture 'web/shared/tree-layout/build.gradle'),(Join-Path $fixture 'web/shared/ai-protocol/build.gradle') -Force } `
            { Set-FixtureFile 'web/shared/domain/build.gradle' @("plugins { id 'java-library' }"); Set-FixtureFile 'web/shared/tree-layout/build.gradle' @("plugins { id 'java-library' }"); Set-FixtureFile 'web/shared/ai-protocol/build.gradle' @("plugins { id 'java-library' }") }
    }
    catch {
        $harnessProblems.Add("selftest harness error: $($_.Exception.Message)")
    }
    finally {
        if (Test-Path -LiteralPath $fixture) {
            Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    foreach ($p in $harnessProblems) { Write-Host "SELFTEST HARNESS PROBLEM: $p" }
    foreach ($d in $deadBranches) { Write-Host "SELFTEST DEAD BRANCH: $d" }
    if ($harnessProblems.Count -gt 0) {
        Write-Host "SELFTEST VERDICT: INCONCLUSIVE ($($harnessProblems.Count) harness problem(s), $caseCount case(s) attempted)"
        exit 3
    }
    if ($deadBranches.Count -gt 0) {
        Write-Host "SELFTEST VERDICT: FAIL ($($deadBranches.Count) dead branch(es) out of $caseCount case(s))"
        exit 1
    }
    Write-Host "SELFTEST VERDICT: PASS ($caseCount case(s), every violation branch turned red)"
    exit 0
}

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
else {
    $RepoRoot = (Resolve-Path $RepoRoot).Path
}

Write-Host 'Treelearn shared-module structure guard'
Write-Host "Repo root: $RepoRoot"

$result = Invoke-SharedStructureCheck -Root $RepoRoot
foreach ($notice in $result.Notices) { Write-Host "NOTICE $notice" }
foreach ($problem in $result.Problems) { Write-Host "FAIL $problem" }
if ($result.Problems.Count -gt 0) {
    Write-Host ''
    Write-Host "VERDICT: FAIL ($($result.Problems.Count) problem(s))"
    exit 1
}
foreach ($line in $result.Oks) { Write-Host "OK   $line" }
Write-Host ''
Write-Host "VERDICT: PASS ($($result.Oks.Count) OK line(s))"
exit 0