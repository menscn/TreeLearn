<#
T-038 round-1 gate runner (the verifier follow-up round).

WHY THIS EXISTS
---------------
Round 0's green lines were Gradle outputs whose task list read "1 from cache, 53 up-to-date", so a
reader could not tell a real execution from a cache hit. Every Gradle gate below therefore carries
--rerun-tasks --no-build-cache, and this runner streams each gate's output into its own evidence file
as it is printed, so an interrupted run still leaves behind what it had already produced.

EVIDENCE CONVENTION
-------------------
Each evidence file carries captured-at / cwd / command in its header, is written with
"... 2>&1 | Out-File -LiteralPath <f> -Encoding utf8 -Append" (never ">>", never "| Out-String"),
and carries its own EXITCODE= line. The header is written WITHOUT -Append, so re-running a gate
replaces that gate's file instead of stacking on a stale copy.

USAGE (the repository root is resolved from THIS SCRIPT's path, never from the current directory)
  pwsh -NoProfile -NonInteractive -File artifacts/evidence/T-038-r1-gates.ps1 -Gate jvm
  gates: jvm | androidtest | unit | check | kotlin
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('jvm', 'androidtest', 'unit', 'check', 'kotlin')]
    [string]$Gate
)

$ErrorActionPreference = 'Continue'

$evidenceDir = $PSScriptRoot
$repoRoot = Split-Path -Parent (Split-Path -Parent $evidenceDir)
$androidRoot = Join-Path $repoRoot 'android'
$selfSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $PSCommandPath).Hash
$selfLines = @(Get-Content -LiteralPath $PSCommandPath).Count
$producer = '# producer: artifacts/evidence/T-038-r1-gates.ps1  sha256=' + $selfSha + '  lines=' + $selfLines + '  -Gate ' + $Gate

function Write-Header {
    param([string]$Path, [string[]]$Lines)
    $Lines | Out-File -LiteralPath $Path -Encoding utf8
}

function Add-Line {
    param([string]$Path, [string]$Text)
    $Text | Out-File -LiteralPath $Path -Encoding utf8 -Append
}

function Add-RestatedExitCode {
    param([string]$Path, [int]$Code)
    Add-Line -Path $Path -Text '# restated (the same measured value, so the last line of this file carries it too):'
    Add-Line -Path $Path -Text ('EXITCODE=' + $Code)
}

function Invoke-Gradle {
    param([string[]]$Arguments, [string]$Evidence)
    Push-Location -LiteralPath $androidRoot
    try {
        & .\gradlew.bat @Arguments 2>&1 | Out-File -LiteralPath $Evidence -Encoding utf8 -Append
        $exit = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    return $exit
}

function Get-SuiteTotals {
    param([string]$Root, [string]$Filter)
    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -Filter $Filter -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'test-results' })
    $tests = 0
    $failures = 0
    $errors = 0
    $skipped = 0
    $unreadable = 0
    foreach ($file in $files) {
        try {
            [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
        }
        catch {
            $unreadable = $unreadable + 1
            continue
        }
        foreach ($suite in @($document.SelectNodes('//testsuite'))) {
            $tests = $tests + [int]$suite.tests
            $failures = $failures + [int]$suite.failures
            $errors = $errors + [int]$suite.errors
            $skipped = $skipped + [int]$suite.skipped
        }
    }
    return ('files={0} tests={1} failures={2} errors={3} skipped={4} unreadable={5}' -f
        $files.Count, $tests, $failures, $errors, $skipped, $unreadable)
}

function Get-SuiteLine {
    param([string]$Path)
    $match = Select-String -LiteralPath $Path -Pattern 'testsuite ' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $match) { return 'MISSING: no <testsuite> line in ' + $Path }
    return $match.Line.Trim()
}

switch ($Gate) {
    'jvm' {
        $evidence = Join-Path $evidenceDir 'T-038-r1-jvm-treecanvas-uncached.txt'
        $report = Join-Path $androidRoot 'app\build\test-results\testDebugUnitTest\TEST-com.treelearn.app.wiring.TreeCanvasWiringTest.xml'
        $wiringTest = Join-Path $androidRoot 'app\src\test\java\com\treelearn\app\wiring\TreeCanvasWiringTest.java'
        $store = Join-Path $androidRoot 'app\src\main\java\com\treelearn\app\wiring\SqliteTreeCanvasStore.java'
        Write-Header -Path $evidence -Lines @(
            '# T-038 round-1 gate 1/5: the focused JVM canvas suite, re-run with the cache disabled, because'
            "# round 0 served its green line from a cache hit, which is not a witness that the tests ran."
            $producer
            ('# captured-at: ' + (Get-Date -Format o))
            ('# cwd: ' + $androidRoot)
            '# command: .\gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.TreeCanvasWiringTest" --no-daemon --console=plain --rerun-tasks --no-build-cache'
            '# mode: JVM unit test only; --rerun-tasks --no-build-cache so every task is EXECUTED, never FROM-CACHE.'
            ('# sha256 TreeCanvasWiringTest.java  = ' + (Get-FileHash -Algorithm SHA256 -LiteralPath $wiringTest).Hash)
            ('# sha256 SqliteTreeCanvasStore.java = ' + (Get-FileHash -Algorithm SHA256 -LiteralPath $store).Hash + '  (round-1 change: committedRounds javadoc only)')
            '# note: this file does not replace the RED/GREEN pair in the task file; it is the uncached re-witness.'
        )
        $code = Invoke-Gradle -Evidence $evidence -Arguments @(
            ':app:testDebugUnitTest', '--tests', 'com.treelearn.app.wiring.TreeCanvasWiringTest',
            '--no-daemon', '--console=plain', '--rerun-tasks', '--no-build-cache')
        Add-Line -Path $evidence -Text ('EXITCODE=' + $code)
        Add-Line -Path $evidence -Text "# reading-command: Select-String -LiteralPath android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.wiring.TreeCanvasWiringTest.xml -Pattern 'testsuite '"
        Add-Line -Path $evidence -Text ('# reading: ' + (Get-SuiteTotals -Root (Join-Path $androidRoot 'app\build\test-results\testDebugUnitTest') -Filter 'TEST-*.xml'))
        Add-Line -Path $evidence -Text ('# ' + (Get-SuiteLine -Path $report))
        Add-Line -Path $evidence -Text '# note: reading the XML is a cmdlet call and sets no exit code of its own; the code this file witnessed is the Gradle one above.'
        Add-RestatedExitCode -Path $evidence -Code $code
    }
    'androidtest' {
        $evidence = Join-Path $evidenceDir 'T-038-r1-androidtest-compile.txt'
        $source = Join-Path $androidRoot 'app\src\androidTest\java\com\treelearn\app\TreeCanvasStoreInstrumentedTest.java'
        $apk = Join-Path $androidRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
        $started = Get-Date
        Write-Header -Path $evidence -Lines @(
            '# T-038 round-1 gate 2/5: the device-side suite is COMPILED, and deliberately never run - the user'
            '# decided on 2026-09-21 to drop the emulator/device rounds, so this is NOT evidence that the SQL ran.'
            '# status: compiled, never executed - deferred 用户自行装机验证（user decision 2026-09-21）'
            $producer
            ('# captured-at: ' + (Get-Date -Format o))
            ('# cwd: ' + $androidRoot)
            '# command: .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain --rerun-tasks --no-build-cache'
            '# mode: compile only; --rerun-tasks --no-build-cache so the compile is EXECUTED, never FROM-CACHE.'
            ('# sha256 TreeCanvasStoreInstrumentedTest.java = ' + (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash)
        )
        $code = Invoke-Gradle -Evidence $evidence -Arguments @(
            ':app:assembleDebugAndroidTest', '--no-daemon', '--console=plain', '--rerun-tasks', '--no-build-cache')
        Add-Line -Path $evidence -Text ('EXITCODE=' + $code)
        Add-Line -Path $evidence -Text '# evidence-existence check: a compile that produced no fresh artefact must not read as a compile that passed.'
        if (Test-Path -LiteralPath $apk) {
            $info = Get-Item -LiteralPath $apk
            Add-Line -Path $evidence -Text ('# reading: androidTest APK present: bytes=' + $info.Length + ' sha256=' + (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash + ' written=' + $info.LastWriteTime.ToString('o') + ' writtenAfterThisRunStarted=' + ($info.LastWriteTime -ge $started))
        }
        else {
            Add-Line -Path $evidence -Text ('# reading: MISSING androidTest APK at ' + $apk)
        }
        $classes = @(Get-ChildItem -LiteralPath (Join-Path $androidRoot 'app\build') -Recurse -Filter 'TreeCanvasStoreInstrumentedTest*.class' -ErrorAction SilentlyContinue)
        Add-Line -Path $evidence -Text ('# reading: compiled TreeCanvasStoreInstrumentedTest class file(s)=' + $classes.Count)
        foreach ($class in $classes) {
            Add-Line -Path $evidence -Text ('#   ' + $class.FullName.Substring($androidRoot.Length + 1))
        }
        Add-RestatedExitCode -Path $evidence -Code $code
    }
    'unit' {
        $evidence = Join-Path $evidenceDir 'T-038-r1-app-unit-tests.txt'
        Write-Header -Path $evidence -Lines @(
            '# T-038 round-1 gate 3/5: the whole :app JVM unit suite, not only the canvas test.'
            $producer
            ('# captured-at: ' + (Get-Date -Format o))
            ('# cwd: ' + $androidRoot)
            '# command: .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache'
            '# mode: JVM unit tests; no emulator or device this round (user decision 2026-09-21).'
            '# mode: --rerun-tasks --no-build-cache so every test task is EXECUTED, never FROM-CACHE.'
        )
        $code = Invoke-Gradle -Evidence $evidence -Arguments @(
            ':app:testDebugUnitTest', '--no-daemon', '--console=plain', '--rerun-tasks', '--no-build-cache')
        Add-Line -Path $evidence -Text ('EXITCODE=' + $code)
        Add-Line -Path $evidence -Text ('# reading: ' + (Get-SuiteTotals -Root (Join-Path $androidRoot 'app\build\test-results\testDebugUnitTest') -Filter 'TEST-*.xml'))
        Add-Line -Path $evidence -Text '# reading-command: Get-ChildItem android/app/build/test-results/testDebugUnitTest -Filter TEST-*.xml, then sum the tests/failures/errors/skipped attributes of each <testsuite>'
        Add-RestatedExitCode -Path $evidence -Code $code
    }
    'check' {
        $evidence = Join-Path $evidenceDir 'T-038-r1-check.txt'
        Write-Header -Path $evidence -Lines @(
            '# T-038 round-1 gate 4/5: the repository-wide Gradle check from android/ (every module test task plus lint).'
            $producer
            ('# captured-at: ' + (Get-Date -Format o))
            ('# cwd: ' + $androidRoot)
            '# command: .\gradlew.bat check --no-daemon --console=plain --rerun-tasks --no-build-cache'
            '# mode: JVM only; instrumented tests need a device and are deferred (user decision 2026-09-21).'
            '# mode: --rerun-tasks --no-build-cache so no task can be reported UP-TO-DATE or FROM-CACHE.'
        )
        $code = Invoke-Gradle -Evidence $evidence -Arguments @(
            'check', '--no-daemon', '--console=plain', '--rerun-tasks', '--no-build-cache')
        Add-Line -Path $evidence -Text ('EXITCODE=' + $code)
        Add-Line -Path $evidence -Text ('# reading: ' + (Get-SuiteTotals -Root $androidRoot -Filter 'TEST-*.xml'))
        Add-Line -Path $evidence -Text '# reading-command: Get-ChildItem -Recurse android -Filter TEST-*.xml | Where FullName -match test-results, then sum the tests/failures/errors/skipped attributes of each <testsuite>'
        Add-Line -Path $evidence -Text '# note: debug and release unit-test tasks both write results, so the file count is larger than the number of distinct test classes.'
        Add-RestatedExitCode -Path $evidence -Code $code
    }
    'kotlin' {
        $evidence = Join-Path $evidenceDir 'T-038-r1-no-kotlin-guard.txt'
        $guard = Join-Path $repoRoot 'android\scripts\no-kotlin-check.ps1'
        Write-Header -Path $evidence -Lines @(
            '# T-038 round-1 gate 5/5: the Java-only guard (no .kt/.kts, no Kotlin plugin, no Kotlin dependency,'
            '# no Kotlin runtime bytecode in a shipped APK). Re-run because round 1 added an androidTest class.'
            $producer
            ('# captured-at: ' + (Get-Date -Format o))
            ('# cwd: ' + $repoRoot)
            '# command: powershell -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1'
            '# mode: full guard, no -SkipDependencyStage and no -SkipApkStage, so exit 2 (PARTIAL) would not read as a pass.'
            ('# guard-sha256: ' + (Get-FileHash -Algorithm SHA256 -LiteralPath $guard).Hash)
        )
        Push-Location -LiteralPath $repoRoot
        try {
            & powershell -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 2>&1 |
                Out-File -LiteralPath $evidence -Encoding utf8 -Append
            $code = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }
        Add-Line -Path $evidence -Text ('EXITCODE=' + $code)
        Add-Line -Path $evidence -Text '# evidence-existence check: a guard that printed no judgement line must not read as a guard that passed.'
        $verdicts = @(Select-String -LiteralPath $evidence -Pattern '^VERDICT:')
        $judgements = @(Select-String -LiteralPath $evidence -Pattern '^(OK|NOTICE) ')
        Add-Line -Path $evidence -Text ('# reading: verdict line(s)=' + $verdicts.Count + ' judgement line(s)=' + $judgements.Count)
        foreach ($line in $verdicts) {
            Add-Line -Path $evidence -Text ('#   ' + $line.Line.Trim())
        }
        Add-RestatedExitCode -Path $evidence -Code $code
    }
}
