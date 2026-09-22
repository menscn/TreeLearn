param([Parameter(Mandatory=$true)][string]$Root, [string]$Exclude = "")
function Count-File([string]$path) {
  $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
  $n = $text.Length
  $codeCharsPerLine = New-Object 'System.Collections.Generic.List[bool]'
  $lineHasCode = $false
  $inBlock = $false
  $inLine = $false
  $strCh = [char]0
  $inTextBlock = $false
  $i = 0
  while ($i -lt $n) {
    $c = $text[$i]
    $c2 = if ($i + 1 -lt $n) { $text[$i+1] } else { [char]0 }
    $c3 = if ($i + 2 -lt $n) { $text[$i+2] } else { [char]0 }
    if ($inLine) { if ($c -eq "`n") { $inLine = $false; $codeCharsPerLine.Add($lineHasCode); $lineHasCode = $false } ; $i++; continue }
    if ($inBlock) {
      if ($c -eq '*' -and $c2 -eq '/') { $inBlock = $false; $i += 2 } else { if ($c -eq "`n") { $codeCharsPerLine.Add($lineHasCode); $lineHasCode = $false }; $i++ }
      continue
    }
    if ($inTextBlock) {
      if ($c -eq '"' -and $c2 -eq '"' -and $c3 -eq '"') { $inTextBlock = $false; $i += 3 } else { if ($c -eq "`n") { $codeCharsPerLine.Add($lineHasCode); $lineHasCode = $false }; $i++ }
      continue
    }
    if ($strCh -ne [char]0) {
      if ($c -eq '\') { $i += 2; continue }
      if ($c -eq $strCh) { $strCh = [char]0 }
      $i++; continue
    }
    if ($c -eq '/' -and $c2 -eq '/') { $inLine = $true; $i += 2; continue }
    if ($c -eq '/' -and $c2 -eq '*') { $inBlock = $true; $i += 2; continue }
    if ($c -eq '"' -and $c2 -eq '"' -and $c3 -eq '"') { $inTextBlock = $true; $i += 3; continue }
    if ($c -eq '"' -or $c -eq "'") { $strCh = $c; $i++; continue }
    if ($c -eq "`n") { $codeCharsPerLine.Add($lineHasCode); $lineHasCode = $false; $i++; continue }
    if (-not [char]::IsWhiteSpace($c)) { $lineHasCode = $true }
    $i++
  }
  $codeCharsPerLine.Add($lineHasCode)
  $code = ($codeCharsPerLine | Where-Object { $_ }).Count
  $phys = ([regex]::Matches($text, "`n")).Count + 1
  return @{ code = $code; phys = $phys }
}
$files = Get-ChildItem -Path $Root -Recurse -Filter *.java -File | Where-Object { $Exclude -eq "" -or $_.Name -notmatch $Exclude }
$tc = 0; $tp = 0
foreach ($f in $files) { $r = Count-File $f.FullName; $tc += $r.code; $tp += $r.phys; "  {0,-40} nonEmptyNonComment={1,5} physical={2,5}" -f $f.Name, $r.code, $r.phys }
"files={0} nonEmptyNonComment={1} physical={2}" -f $files.Count, $tc, $tp