$ErrorActionPreference = 'Stop'
$root = 'D:/Project/Android/Treelearn'
Set-Location $root
$out = 'artifacts/evidence/T-090-linecount.txt'

function Test-CommentLine([string]$line) {
    $t = $line.Trim()
    if ($t -eq '') { return $true }
    if ($t.StartsWith('//')) { return $true }
    if ($t.StartsWith('/*') -or $t.StartsWith('*') -or $t.StartsWith('*/')) { return $true }
    if ($t.StartsWith('<!--') -or $t.StartsWith('-->')) { return $true }
    if ($t -match '^<!--.*-->$') { return $true }
    return $false
}

$newFiles = @(
    'android/app/src/main/java/com/treelearn/app/generation/MessageTranslator.java',
    'android/app/src/main/java/com/treelearn/app/wiring/SqliteTranslationRoutes.java',
    'android/app/src/main/res/drawable/ic_action_translate.xml'
)

$modFiles = @(
    'android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java',
    'android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java',
    'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java',
    'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java',
    'android/app/src/main/java/com/treelearn/app/ui/ChatViewState.java',
    'android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java',
    'android/app/src/main/java/com/treelearn/app/ui/ChatActions.java',
    'android/app/src/main/java/com/treelearn/app/ui/ChatMessage.java',
    'android/app/src/main/java/com/treelearn/app/screen/AnswerActionBar.java',
    'android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java',
    'android/app/src/main/java/com/treelearn/app/AppContainer.java',
    'android/app/src/main/java/com/treelearn/app/MainActivity.java',
    'android/app/src/main/res/layout/view_answer_actions.xml',
    'android/app/src/main/res/layout/item_chat_message.xml',
    'android/app/src/main/res/values/strings.xml'
)

$report = New-Object System.Collections.Generic.List[string]
$report.Add('T-090 production line count')
$report.Add('Rule: non-blank, non-comment lines. New files = full file count; modified files = git diff -U0 added lines only. Test files excluded.')
$report.Add('')

$total = 0
$report.Add('== NEW production files (full, non-blank non-comment) ==')
foreach ($f in $newFiles) {
    $lines = [System.IO.File]::ReadAllLines("$root/$f")
    $n = 0
    foreach ($l in $lines) { if (-not (Test-CommentLine $l)) { $n++ } }
    $total += $n
    $report.Add("$n  $f")
}

$report.Add('')
$report.Add('== MODIFIED production files (git diff -U0 added lines, non-blank non-comment) ==')
foreach ($f in $modFiles) {
    $diff = & git diff -U0 -- $f
    $n = 0
    foreach ($l in $diff) {
        if ($l.StartsWith('+') -and -not $l.StartsWith('+++')) {
            $content = $l.Substring(1)
            if (-not (Test-CommentLine $content)) { $n++ }
        }
    }
    $total += $n
    $report.Add("$n  $f")
}

$report.Add('')
$report.Add("TOTAL: $total")
$report.Add("LIMIT: 500 ( exceeded: $($total -gt 500) )")

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines("$root/$out", $report, $utf8)
Write-Output "written $out TOTAL=$total"
