$ErrorActionPreference = "Continue"
$doc = "Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md"
$docText = [IO.File]::ReadAllText($doc, [Text.Encoding]::UTF8)
$lines = $docText -split "`r?`n"
$inBlock = $false; $blocks = @(); $cur = New-Object System.Collections.Generic.List[string]
foreach ($l in $lines) {
  if ($l -match '^\s*```') { if ($inBlock) { $blocks += ,($cur.ToArray()); $cur.Clear(); $inBlock=$false } else { $inBlock=$true }; continue }
  if ($inBlock) { [void]$cur.Add($l) }
}
# signature = non-empty block line with >=20 chars
$sigs = New-Object System.Collections.Generic.List[string]
$b=0
foreach ($blk in $blocks) {
  $b++
  foreach ($ln in $blk) { $t=$ln.Trim(); if ($t.Length -ge 20) { [void]$sigs.Add($t) } }
}
$sigs = $sigs | Sort-Object -Unique
Write-Output ("distinct signature lines (>=20 chars): " + $sigs.Count)
$sigs | ForEach-Object { Write-Output ("  sig len={0} prefix12={1}" -f $_.Length, $_.Substring(0,12)) }
Write-Output "--- per-file hits (file, matchedLineCount, maxMatchedLen) ---"
$exclude = @("VERIFY2-T020-leakscan.txt")
$grandTotal = 0
Get-ChildItem artifacts/evidence -File | Where-Object { $exclude -notcontains $_.Name } | ForEach-Object {
  $t = [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8)
  $n=0; $max=0
  foreach ($sg in $sigs) { if ($t.Contains($sg)) { $n++; if ($sg.Length -gt $max) { $max=$sg.Length } } }
  if ($n -gt 0) { $grandTotal += $n; Write-Output ("  HIT {0}  matchedSigLines={1} maxLen={2}" -f $_.Name, $n, $max) }
}
Write-Output ("GRAND TOTAL matched signature lines across evidence: " + $grandTotal)