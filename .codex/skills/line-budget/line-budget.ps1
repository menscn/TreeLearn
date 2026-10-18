<#
    line-budget.ps1 -- 500 行硬预算检查
    统计未提交(或相对指定基点)的业务变更行，排除生成/迁移/文档/证据目录。
    自定位 git 仓库根，不硬编码绝对路径，可随本技能目录整体搬运。

    用法:
        powershell -NoProfile -ExecutionPolicy Bypass -File line-budget.ps1                    # 默认比较 HEAD
        powershell -NoProfile -ExecutionPolicy Bypass -File line-budget.ps1 <rev>              # 比较指定基点
        powershell -NoProfile -ExecutionPolicy Bypass -File line-budget.ps1 <rev> <pathspec>   # 只统计某个子树

    退出码:
        0  -> PASS  (变更 <= 200)
        1  -> WARN  (200 < 变更 <= 500)
        2  -> FAIL  (变更 > 500 或脚本/仓库错误)

    ---------------------------------------------------------------------------
    维护须知（全部是实测踩过的坑，改这个脚本前请先读）
    ---------------------------------------------------------------------------
    1. 本文件必须保存为 **UTF-8 with BOM**。
       Windows PowerShell 5.1 对无 BOM 的 .ps1 按系统 ANSI 代码页(简体中文机器上是 GBK)
       解码，文件里的中文串直接变乱码并报「表达式中缺少右")"」——不是运行时失败，是
       整个脚本根本执行不了，连退出码都拿不到。BOM 掉了就补：
           python -c "p=r'<path>';d=open(p,'rb').read();open(p,'wb').write(b'\xef\xbb\xbf'+d)"

    2. 调 git 绝不能裸调。$ErrorActionPreference='Stop' 加 2>&1 会把 git 写到 stderr 的
       **无害警告**(如 'LF will be replaced by CRLF')升级成终止性 NativeCommandError，
       脚本在拿到 --numstat 之前就中断，输出里没有预算结论。统一走 Invoke-NativeCaptured。

    3. 必须带 -c core.quotepath=false。git 默认对含非 ASCII 的路径加双引号并做八进制转义
       (如 "Docs/04-ux-design/\347\237\245\350\212\275...html")，前导引号会让排除正则的 ^ 锚点
       失配，本该排除的文档目录会被**算进业务变更行**(实测虚增 2088 行)。

    4. 必须显式统计**未跟踪文件**。git diff 只看已跟踪路径，而「新任务新建的文件」天然
       全是未跟踪的——只跑 git diff 会得到「业务变更 2000 行却只扫描到 1 个文件」这种
       结果，对最常见的变更形态系统性失明。未跟踪文件走
       git ls-files --others --exclude-standard 后逐文件计行。

    5. --numstat 输出里可能混进上述警告行，因此每行都要先用正则确认前两列是纯数字再
       [int] 转换；直接 [int]$f[0] 会在警告行上抛异常。
#>
$ErrorActionPreference = "Stop"

# 调外部命令而不让它的 stderr 变成终止性错误；返回 @{ ExitCode; Text }。
# 与 android/scripts/no-kotlin-check.ps1 的 Invoke-NativeCaptured 同一模式。
function Invoke-NativeCaptured([string] $Executable, [string[]] $CommandArguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Executable @CommandArguments 2>&1
        return @{ ExitCode = $LASTEXITCODE; Text = @($output) }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

# 所有 git 调用都带上，避免非 ASCII 路径被引号 + 八进制转义包裹。
function Invoke-Git([string[]] $GitArguments) {
    return Invoke-NativeCaptured 'git' (@('-c', 'core.quotepath=false') + $GitArguments)
}

# 从 git 输出里挑出仓库根路径：忽略 stderr 警告行与空行，取最后一个像路径的行。
function Select-RepoPath([object[]] $Lines) {
    $candidates = @($Lines |
        ForEach-Object { "$_" } |
        Where-Object { $_.Trim().Length -gt 0 -and $_ -notmatch '^\s*(warning|error|fatal):' })
    if ($candidates.Count -eq 0) { return '' }
    return $candidates[$candidates.Count - 1].Trim()
}

# 统计一个未跟踪文本文件的行数；二进制文件返回 0(不计入行预算)。
function Get-TextFileLineCount([string] $FullPath) {
    try {
        $bytes = [System.IO.File]::ReadAllBytes($FullPath)
    }
    catch {
        return 0
    }
    foreach ($b in $bytes) {
        if ($b -eq 0) { return 0 }   # NUL -> binary
    }
    if ($bytes.Length -eq 0) { return 0 }
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    # 计行口径必须与 git --numstat 一致：numstat 把空行也算作 added 行，
    # 所以这里同样统计全部行，只去掉结尾换行产生的那一个空元素。
    $lines = $text -split "`n"
    $count = $lines.Count
    if ($lines[$count - 1].Length -eq 0) { $count-- }
    return $count
}

# --- 定位 git 仓库根 ---
$probe = Invoke-Git @('rev-parse', '--show-toplevel')
$repoRoot = Select-RepoPath $probe.Text
if ($probe.ExitCode -ne 0 -or -not $repoRoot) {
    Write-Host "[line-budget] 错误: 当前目录不在 git 仓库内" -ForegroundColor Red
    foreach ($line in $probe.Text) { Write-Host "    $line" -ForegroundColor DarkGray }
    exit 2
}
Push-Location $repoRoot
try {
    # --- 基点与可选路径过滤 ---
    $base     = if ($args.Count -gt 0) { $args[0] } else { "HEAD" }
    $pathspec = if ($args.Count -gt 1) { $args[1] } else { "" }

    # --- 排除规则（生成 / 迁移 / 文档 / 证据 / 头脑风暴素材）---
    $excludedDir  = '^(\.codex|\.github|\.superpowers|build|out|dist|target|\.gradle|\.idea|node_modules|generated|migrations?|docs?|artifacts)\/'
    $excludedFile = '\.(lock|min\.js|min\.css|map|md|mdx|png|jpg|jpeg|gif|webp|apk|jar|so|dex)$'

    $businessLines = 0
    $scannedFiles  = 0
    $excludedLines = 0
    $skippedRows   = 0

    # --- 已跟踪路径的变更行 ---
    $diffArgs = @('diff', '--numstat', $base, '--')
    if ($pathspec) { $diffArgs += $pathspec }
    $diff = Invoke-Git $diffArgs
    if ($diff.ExitCode -ne 0) {
        Write-Host "[line-budget] 错误: git diff 失败 (基点: $base)" -ForegroundColor Red
        foreach ($line in $diff.Text) { Write-Host "    $line" -ForegroundColor DarkGray }
        exit 2
    }
    foreach ($row in $diff.Text) {
        $text = "$row"
        if ($text.Trim().Length -eq 0) { continue }
        $f = $text -split "`t"
        # 前两列必须是纯数字，否则这行是 git 的 stderr 警告而不是 numstat 记录。
        if ($f.Count -lt 3 -or $f[0] -notmatch '^\d+$' -or $f[1] -notmatch '^\d+$') {
            $skippedRows++
            continue
        }
        $lineCount = [int]$f[0] + [int]$f[1]
        $path = ($f[2..($f.Count - 1)] -join "`t").Trim()
        if (($path -match $excludedDir) -or ($path -match $excludedFile)) { $excludedLines += $lineCount; continue }
        $businessLines += $lineCount
        $scannedFiles++
    }

    # --- 未跟踪文件（新任务新建的文件天然全在此列，git diff 看不见）---
    $othersArgs = @('ls-files', '--others', '--exclude-standard')
    if ($pathspec) { $othersArgs += @('--', $pathspec) }
    $others = Invoke-Git $othersArgs
    if ($others.ExitCode -ne 0) {
        Write-Host "[line-budget] 错误: git ls-files 失败" -ForegroundColor Red
        foreach ($line in $others.Text) { Write-Host "    $line" -ForegroundColor DarkGray }
        exit 2
    }
    $untrackedFiles = 0
    foreach ($row in $others.Text) {
        $path = "$row".Trim()
        if ($path.Length -eq 0 -or $path -match '^\s*(warning|error|fatal):') { continue }
        $untrackedFiles++
        $full = Join-Path $repoRoot $path
        $n = Get-TextFileLineCount $full
        if (($path -match $excludedDir) -or ($path -match $excludedFile)) { $excludedLines += $n; continue }
        $businessLines += $n
        $scannedFiles++
    }

    $scope = if ($pathspec) { $pathspec } else { '(whole repository)' }
    Write-Host ("[line-budget] 基点: $base ; 范围: $scope") -ForegroundColor Cyan
    Write-Host "业务变更行: $businessLines (已跟踪 新增+删除 与 未跟踪 全部行；已排除生成/迁移/文档/证据 $excludedLines 行；扫描业务文件 $scannedFiles 个，其中未跟踪 $untrackedFiles 个)"
    if ($skippedRows -gt 0) {
        Write-Host "[line-budget] 提示: 跳过 $skippedRows 行非 numstat 输出(git 警告)" -ForegroundColor DarkGray
    }

    if ($businessLines -le 200) {
        Write-Host "[line-budget] PASS: 预算内" -ForegroundColor Green
        exit 0
    }
    elseif ($businessLines -le 500) {
        Write-Host "[line-budget] WARN: 变更偏大(>200)，建议自查是否可拆小，本次可提交" -ForegroundColor Yellow
        exit 1
    }
    else {
        Write-Host "[line-budget] FAIL: 业务变更 >500 行，超出硬预算，须拆 task" -ForegroundColor Red
        exit 2
    }
}
finally {
    Pop-Location
}
