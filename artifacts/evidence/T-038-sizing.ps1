# T-038 sizing scan (ASCII only).
#
# Counts the production files this task creates, so the "new production lines" figure in the task
# file can be traced to a command instead of being recalled. Modified files (AppContainer,
# MainActivity, the chat entry, resources) are deliberately NOT counted here: their working copies
# also carry earlier uncommitted tasks, so a per-task diff for them is not obtainable from git and
# any number would be a guess.
#
# Every file is asserted to exist and to be non-empty, so a rename fails the scan rather than
# printing a confident zero.
#
# Prints VERDICT: PASS and exits 0, or VERDICT: FAIL with the reasons and exits 1.

$ErrorActionPreference = 'Continue'
$repo = 'D:\Project\Android\Treelearn'
$files = @(
  'android\app\src\main\java\com\treelearn\app\wiring\TreeCanvasStore.java',
  'android\app\src\main\java\com\treelearn\app\wiring\SqliteTreeCanvasStore.java',
  'android\app\src\main\java\com\treelearn\app\wiring\TreeCanvasWiring.java'
)

$fails = @()
$totalPhysical = 0
$totalCode = 0
Write-Output '=== new production files (T-038) ==='
foreach ($rel in $files) {
  $full = Join-Path $repo $rel
  if (-not (Test-Path -LiteralPath $full)) {
    Write-Output ('  MISSING ' + $rel)
    $fails += ('missing file: ' + $rel)
    continue
  }
  $lines = @(Get-Content -LiteralPath $full -Encoding utf8)
  $physical = $lines.Count
  $code = @($lines | Where-Object {
    $t = $_.Trim()
    $t -ne '' -and -not $t.StartsWith('*') -and -not $t.StartsWith('/*') -and -not $t.StartsWith('//')
  }).Count
  $totalPhysical += $physical
  $totalCode += $code
  Write-Output ('  physical=' + $physical + ' nonblank-noncomment=' + $code + '  ' + $rel)
  if ($physical -le 0) { $fails += ('empty file: ' + $rel) }
  if ($code -le 0) { $fails += ('no code lines: ' + $rel) }
}
Write-Output ('TOTAL files=' + $files.Count + ' physical=' + $totalPhysical + ' nonblank-noncomment=' + $totalCode)
Write-Output ('estimate in the task file: estimated_production_lines=240 (soft limit 200, hard budget 500)')

Write-Output '=== VERDICT ==='
if ($fails.Count -eq 0) {
  Write-Output 'VERDICT: PASS'
  exit 0
}
Write-Output 'VERDICT: FAIL'
foreach ($f in $fails) { Write-Output ('  - ' + $f) }
exit 1
