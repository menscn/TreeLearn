# T-038 README current-state rescan (deliberately ASCII-only, so a Windows PowerShell 5.1 host
# reads it correctly regardless of the console code page).
#
# Backs the acceptance claim that the old "the canvas has no construction point / is unreachable"
# current-state wording is gone from android/README.md. Two directions, both fail-closed:
#   - the OLD phrasings must be absent (count 0), and
#   - the NEW section that replaced them must be present (exactly 1), so a reverted or deleted
#     section fails the scan instead of quietly reporting "0 old hits".
#
# The Chinese search terms are reconstructed from code points instead of being written literally.
# That is what keeps this file ASCII-only, and the new-section direction doubles as a positive
# control: if the reconstruction were wrong, its count would be 0 and the scan would FAIL.
#
# Prints VERDICT: PASS and exits 0, or VERDICT: FAIL with the reasons and exits 1.

$ErrorActionPreference = 'Continue'
$repo = 'D:\Project\Android\Treelearn'
$readme = Join-Path $repo 'android\README.md'
$fails = @()

if (-not (Test-Path -LiteralPath $readme)) {
  Write-Output ('MISSING ' + $readme)
  Write-Output '=== VERDICT ==='
  Write-Output 'VERDICT: FAIL'
  Write-Output '  - android/README.md not found'
  exit 1
}

$oldPhrases = @(
  (-join (@(0x753B,0x5E03,0x65E0,0x4EFB,0x4F55,0x6784,0x9020,0x70B9) | ForEach-Object { [char]$_ })),
  (-join (@(0x753B,0x5E03,0x65E0,0x6784,0x9020,0x70B9) | ForEach-Object { [char]$_ })),
  (-join (@(0x6811,0x753B,0x5E03,0x4E0D,0x53EF,0x8FBE) | ForEach-Object { [char]$_ }))
)
$newPhrase = (-join (@(0x6811,0x753B,0x5E03,0x7684,0x751F,0x4EA7,0x88C5,0x914D,0x4E0E,0x5165,0x53E3,0xFF08,0x54,0x2D,0x30,0x33,0x38,0xFF09) | ForEach-Object { [char]$_ }))

$lines = @(Get-Content -LiteralPath $readme -Encoding utf8)
Write-Output ('README lines=' + $lines.Count)

Write-Output '=== old current-state phrasings (each must be 0) ==='
foreach ($old in $oldPhrases) {
  $n = @($lines | Where-Object { $_.Contains($old) }).Count
  Write-Output ('  hits=' + $n + '  ' + $old)
  if ($n -ne 0) { $fails += ('old phrasing still present (' + $n + ' line(s)): ' + $old) }
}

Write-Output '=== the section that replaced them (must be exactly 1) ==='
$newHits = @($lines | Where-Object { $_.Contains($newPhrase) })
Write-Output ('  hits=' + $newHits.Count + '  ' + $newPhrase)
foreach ($h in $newHits) { Write-Output ('    ' + $h.Trim()) }
if ($newHits.Count -ne 1) { $fails += ('expected exactly 1 replacement section header, found ' + $newHits.Count) }

Write-Output '=== VERDICT ==='
if ($fails.Count -eq 0) {
  Write-Output 'VERDICT: PASS'
  exit 0
}
Write-Output 'VERDICT: FAIL'
foreach ($f in $fails) { Write-Output ('  - ' + $f) }
exit 1
