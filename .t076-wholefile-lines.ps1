# T-076: whole-file non-blank, non-comment line counts (for reference).
$ErrorActionPreference = 'Stop'
function Count-Code([string]$path) {
    $n = 0
    $inBlock = $false
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        $t = $line.Trim()
        if ($inBlock) {
            if ($t.Contains('*/') -or $t.Contains('-->')) { $inBlock = $false }
            continue
        }
        if ($t -eq '') { continue }
        if ($t.StartsWith('//')) { continue }
        if ($t.StartsWith('/*') -or $t.StartsWith('<!--')) {
            if (-not ($t.Contains('*/')) -and -not ($t.Contains('-->'))) { $inBlock = $true }
            continue
        }
        if ($t.StartsWith('<?')) { continue }
        $n++
    }
    return $n
}
$base = 'd:\Project\Android\Treelearn\android\app\src\main'
$files = @(
    "$base\java\com\treelearn\app\ui\ChatDraftStore.java",
    "$base\java\com\treelearn\app\wiring\SharedPreferencesChatDraftStore.java",
    "$base\res\drawable\ic_chat_stop.xml",
    "$base\java\com\treelearn\app\ui\ChatViewState.java",
    "$base\java\com\treelearn\app\ui\ChatPresenter.java",
    "$base\java\com\treelearn\app\screen\ChatScreen.java",
    "$base\java\com\treelearn\app\AppContainer.java",
    "$base\java\com\treelearn\app\MainActivity.java",
    "$base\res\layout\screen_chat_body.xml",
    "$base\res\values\strings.xml"
)
$out = @()
foreach ($f in $files) {
    $out += ("{0} : {1}" -f (Split-Path $f -Leaf), (Count-Code $f))
}
$out -join "`n" | Set-Content 'd:\Project\Android\Treelearn\.t076_ls.txt' -Encoding UTF8
