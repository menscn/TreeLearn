$ErrorActionPreference = "Continue"
$doc = "Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md"
$docText = [IO.File]::ReadAllText($doc, [Text.Encoding]::UTF8)
# Build set of all 14-char sliding windows over the doc that consist only of CJK-ish chars
function Is-Cjk([char]$c) {
  $i = [int]$c
  return (($i -ge 0x4E00 -and $i -le 0x9FFF) -or ($i -ge 0x3000 -and $i -le 0x303F) -or ($i -ge 0xFF00 -and $i -le 0xFFEF))
}
$windows = New-Object 'System.Collections.Generic.HashSet[string]'
$runs = [regex]::Matches($docText, '[\u4E00-\u9FFF\u3000-\u303F\uFF00-\uFFEF]{10,}')
foreach ($m in $runs) {
  $v = $m.Value
  for ($i = 0; $i + 14 -le $v.Length; $i++) { [void]$windows.Add($v.Substring($i,14)) }
}
Write-Output ("doc CJK runs>=10: " + $runs.Count + " ; distinct 14-char windows: " + $windows.Count)
$exclude = @("VERIFY2-T020-leakscan.txt")
$total = 0
Get-ChildItem artifacts/evidence -File | Where-Object { $exclude -notcontains $_.Name } | ForEach-Object {
  $t = [IO.File]::ReadAllText($_.FullName, [Text.Encoding]::UTF8)
  $mr = [regex]::Matches($t, '[\u4E00-\u9FFF\u3000-\u303F\uFF00-\uFFEF]{14,}')
  $hits = @()
  foreach ($m in $mr) {
    $v = $m.Value
    for ($i = 0; $i + 14 -le $v.Length; $i++) {
      if ($windows.Contains($v.Substring($i,14))) { $hits += $v.Length; break }
    }
  }
  if ($hits.Count -gt 0) { $total += $hits.Count; Write-Output ("  HIT {0}  matchingRuns={1} maxRunLen={2}" -f $_.Name, $hits.Count, ($hits | Measure-Object -Maximum).Maximum) }
}
Write-Output ("TOTAL evidence files with a 14-char window shared with the prompt-body doc: " + $total)