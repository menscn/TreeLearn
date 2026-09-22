$ErrorActionPreference = 'Stop'
$files = @(
  'app\src\main\java\com\treelearn\app\screen\ChatMotion.java',
  'app\src\main\java\com\treelearn\app\screen\ChatScreen.java',
  'app\src\main\java\com\treelearn\app\ui\ChatViewState.java',
  'app\src\main\java\com\treelearn\app\ui\ChatPresenter.java',
  'app\src\main\java\com\treelearn\app\ui\ChatActions.java',
  'app\src\main\res\layout\screen_chat_body.xml',
  'app\src\main\res\layout\item_chat_message.xml',
  'app\src\main\res\values\strings.xml',
  'app\src\main\res\values\dimens.xml'
)
$total = 0
foreach ($f in $files) {
  $path = Join-Path 'D:\Project\Android\Treelearn\android' $f
  $lines = Get-Content -LiteralPath $path
  $count = 0; $inBlock = $false
  foreach ($raw in $lines) {
    $t = $raw.Trim()
    if ($inBlock) { if ($t -match '\*/\s*$' -or $t -match '-->\s*$') { $inBlock = $false }; continue }
    if ($t -eq '') { continue }
    if ($t.StartsWith('/*') -or $t.StartsWith('<!--')) {
      if (-not (($t.StartsWith('/*') -and $t.Contains('*/')) -or ($t.StartsWith('<!--') -and $t.Contains('-->')))) { $inBlock = $true }
      continue
    }
    if ($t.StartsWith('//') -or $t.StartsWith('*') -or $t.StartsWith('*/')) { continue }
    $count++
  }
  $total += $count
  "{0}`t{1}" -f $f, $count
}
"TOTAL`t$total"
