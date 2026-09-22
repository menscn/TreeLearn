# T-076: count non-blank, non-comment lines for the new production files.
$ErrorActionPreference = 'Stop'
function Count-Code([string]$path) {
    $n = 0
    $inBlock = $false
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        $t = $line.Trim()
        if ($inBlock) {
            if ($t -match '\*/\s*$' -or $t -match '\*/') { $inBlock = $false }
            continue
        }
        if ($t -eq '') { continue }
        if ($t.StartsWith('//')) { continue }
        if ($t.StartsWith('/*')) {
            if (-not ($t.Contains('*/'))) { $inBlock = $true }
            continue
        }
        if ($t.StartsWith('<!--')) {
            if (-not ($t.Contains('-->'))) { $inBlock = $true }
            continue
        }
        if ($t.StartsWith('<?')) { continue }
        $n++
    }
    return $n
}
$root = 'd:\Project\Android\Treelearn\android\app\src\main'
$files = @(
    "$root\java\com\treelearn\app\ui\ChatDraftStore.java",
    "$root\java\com\treelearn\app\wiring\SharedPreferencesChatDraftStore.java",
    "$root\res\drawable\ic_chat_stop.xml"
)
$out = @()
foreach ($f in $files) {
    $out += ("{0} : {1}" -f $f, (Count-Code $f))
}
$out -join "`n" | Set-Content 'd:\Project\Android\Treelearn\.t076_ls.txt' -Encoding UTF8
