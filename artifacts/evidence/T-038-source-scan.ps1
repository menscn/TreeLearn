# T-038 acceptance source scan (ASCII only).
#
# Every match set is asserted to be non-empty where it must be, so a rename or a moved file fails the
# scan instead of silently verifying nothing. Occurrences are counted per match (-AllMatches), not per
# line, because the host asks for both canvas ports on one line.
#
#   A   the canvas is constructed exactly once in :app main, and that one place is MainActivity
#   B   the same for its presenter
#   C   the three screen files are the ones scanned (a rename fails, it does not shrink the scan)
#   D   no screen file reaches a DAO, a SQL statement or the database helper
#   D+  positive control: the same pattern does light up over the wiring package
#   E   the fold appears only in its declared owners, and in no screen file
#   F   exactly one production class implements both canvas ports
#   G   the container exposes the two ports and the host asks for both
#
# Prints VERDICT: PASS and exits 0, or VERDICT: FAIL with the reasons and exits 1.

$ErrorActionPreference = 'Continue'
$repo = 'D:\Project\Android\Treelearn'
$main = Join-Path $repo 'android\app\src\main'
$javaRoot = Join-Path $main 'java'
$screenDir = Join-Path $javaRoot 'com\treelearn\app\screen'
$wiringDir = Join-Path $javaRoot 'com\treelearn\app\wiring'
$allJava = @(Get-ChildItem -LiteralPath $javaRoot -Recurse -File -Filter *.java)
$fails = @()

# Every line mentioning the pattern, with every match on it kept, so the caller can count occurrences.
function Find-All([string]$pattern, [string]$dir, [string]$glob, [switch]$recurse) {
  if ($recurse) {
    $files = @(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter $glob)
  } else {
    $files = @(Get-ChildItem -LiteralPath $dir -File -Filter $glob)
  }
  return ,@($files | Select-String -Pattern $pattern -AllMatches)
}
function Count-All($lines) {
  $n = 0
  foreach ($h in @($lines)) { $n += $h.Matches.Count }
  return $n
}
function Show($lines) {
  foreach ($h in @($lines)) { Write-Output ('  ' + $h.Path.Substring($repo.Length).TrimStart('\') + ':' + $h.LineNumber + ': ' + $h.Line.Trim()) }
}

Write-Output '=== A: the canvas construction point ==='
$screenCtor = Find-All 'new TreeCanvasScreen\(' $javaRoot '*.java' -recurse
Write-Output ('A lines=' + @($screenCtor).Count + ' occurrences=' + (Count-All $screenCtor) + ' (expected 1, in MainActivity)')
Show $screenCtor
if (@($screenCtor).Count -ne 1) { $fails += ('A: expected exactly 1 line constructing TreeCanvasScreen in :app main, found ' + @($screenCtor).Count) }
elseif ($screenCtor[0].Path -notlike '*MainActivity.java') { $fails += 'A: the single construction is not in MainActivity.java' }

Write-Output '=== B: the presenter construction point ==='
$presenterCtor = Find-All 'new TreeCanvasPresenter\(' $javaRoot '*.java' -recurse
Write-Output ('B lines=' + @($presenterCtor).Count + ' occurrences=' + (Count-All $presenterCtor) + ' (expected 1, in MainActivity)')
Show $presenterCtor
if (@($presenterCtor).Count -ne 1) { $fails += ('B: expected exactly 1 line constructing TreeCanvasPresenter in :app main, found ' + @($presenterCtor).Count) }
elseif ($presenterCtor[0].Path -notlike '*MainActivity.java') { $fails += 'B: the single construction is not in MainActivity.java' }

Write-Output '=== C: the screen files under scan ==='
$screenNames = @('TreeCanvasScreen.java', 'TreeCanvasView.java', 'ChatScreen.java')
$screenFiles = @(Get-ChildItem -LiteralPath $screenDir -File -Filter *.java | Where-Object { $screenNames -contains $_.Name })
Write-Output ('C files=' + $screenFiles.Count + ' (expected 3, or the scan shrank without saying so)')
foreach ($f in $screenFiles) { Write-Output ('  ' + $f.FullName.Substring($repo.Length).TrimStart('\')) }
if ($screenFiles.Count -ne 3) { $fails += ('C: expected the 3 named screen files, found ' + $screenFiles.Count) }

Write-Output '=== D: no screen file reaches the database ==='
$dbPat = 'getWritableDatabase|getReadableDatabase|SQLiteDatabase|getDatabaseName|BranchDao|MessageDao|AiArtifactDao|TreeLearnDatabaseHelper|DatabaseTransactionRunner|SQLiteOpenHelper|INSERT INTO|UPDATE |DELETE FROM|SELECT |sqlite'
$dbHits = @($screenFiles | Select-String -Pattern $dbPat -AllMatches)
Write-Output ('D lines=' + $dbHits.Count + ' occurrences=' + (Count-All $dbHits) + ' (expected 0)')
Show $dbHits
if ((Count-All $dbHits) -ne 0) { $fails += ('D: a screen file reaches the database, occurrences=' + (Count-All $dbHits)) }

Write-Output '=== D+: positive control, the same pattern over the wiring package ==='
$controlHits = Find-All $dbPat $wiringDir '*.java'
Write-Output ('D+ lines=' + @($controlHits).Count + ' occurrences=' + (Count-All $controlHits) + ' (expected > 0)')
Write-Output '  the pattern also matches prose, so 0 hits under D means the screens do not even name these types'
foreach ($h in @($controlHits | Select-Object -First 5)) { Write-Output ('  ' + $h.Path.Substring($repo.Length).TrimStart('\') + ':' + $h.LineNumber + ': ' + $h.Line.Trim()) }
if (@($controlHits).Count -lt 1) { $fails += 'D+: the database pattern matched nothing even over the wiring package, so it proves nothing' }

Write-Output '=== E: the fold appears only in its declared owners ==='
$foldOwners = @('TreeCanvasCommands.java', 'TreeCanvasStore.java', 'TreeCanvasWiring.java', 'SqliteTreeCanvasStore.java', 'TreeCanvasPresenter.java')
$foldHits = Find-All 'setCollapsed' $javaRoot '*.java' -recurse
Write-Output ('E lines=' + @($foldHits).Count + ' occurrences=' + (Count-All $foldHits) + ' (expected >= 4, all inside: ' + ($foldOwners -join ', ') + ')')
Show $foldHits
if ((Count-All $foldHits) -lt 4) { $fails += ('E: the fold has fewer than 4 occurrences, so its owners were not all found (' + (Count-All $foldHits) + ')') }
foreach ($h in @($foldHits)) {
  $leaf = Split-Path -Leaf $h.Path
  if ($foldOwners -notcontains $leaf) { $fails += ('E: setCollapsed appears in an undeclared owner: ' + $leaf) }
}
$foldInScreens = @($screenFiles | Select-String -Pattern 'setCollapsed' -AllMatches)
Write-Output ('E2 fold occurrences inside the screen files=' + (Count-All $foldInScreens) + ' (expected 0)')
if ((Count-All $foldInScreens) -ne 0) { $fails += 'E2: a screen file mentions the fold instead of only forwarding the gesture' }

Write-Output '=== F: the production implementation of both canvas ports ==='
$impl = Find-All 'implements TreeCanvasData, TreeCanvasCommands' $javaRoot '*.java' -recurse
Write-Output ('F lines=' + @($impl).Count + ' (expected 1)')
Show $impl
if (@($impl).Count -ne 1) { $fails += ('F: expected exactly 1 production class implementing both canvas ports, found ' + @($impl).Count) }

Write-Output '=== G: the container exposes both ports and the host asks for both ==='
$deadLines = Find-All 'TreeCanvasData treeCanvasData\(\)|TreeCanvasCommands treeCanvasCommands\(\)' $javaRoot '*.java' -recurse
$useLines = Find-All 'container\.treeCanvasData\(\)|container\.treeCanvasCommands\(\)' $javaRoot '*.java' -recurse
Write-Output ('G definitions=' + (Count-All $deadLines) + ' (expected 2), uses=' + (Count-All $useLines) + ' (expected 2, counted per occurrence because both are on one line)')
foreach ($h in @($deadLines)) { Write-Output ('  def ' + $h.Path.Substring($repo.Length).TrimStart('\') + ':' + $h.LineNumber + ': ' + $h.Line.Trim()) }
foreach ($h in @($useLines)) { Write-Output ('  use ' + $h.Path.Substring($repo.Length).TrimStart('\') + ':' + $h.LineNumber + ': ' + $h.Line.Trim()) }
if ((Count-All $deadLines) -ne 2) { $fails += ('G: expected 2 accessor definitions on the container, found ' + (Count-All $deadLines)) }
if ((Count-All $useLines) -ne 2) { $fails += ('G: expected the host to ask for both ports, found ' + (Count-All $useLines)) }

Write-Output '=== H: the app main source set still resolves zero AndroidX coordinates ==='
$androidxLines = Find-All '^import androidx\.' $javaRoot '*.java' -recurse
Write-Output ('H occurrences=' + (Count-All $androidxLines) + ' (expected 0; the app main code depends on platform APIs only)')
Show $androidxLines
if ((Count-All $androidxLines) -ne 0) { $fails += 'H: app main code imports an AndroidX coordinate' }

Write-Output '=== VERDICT ==='
if ($fails.Count -eq 0) {
  Write-Output 'VERDICT: PASS'
  exit 0
}
Write-Output 'VERDICT: FAIL'
foreach ($f in $fails) { Write-Output ('  - ' + $f) }
exit 1
