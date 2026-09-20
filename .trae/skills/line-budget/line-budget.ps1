<#
    line-budget.ps1 -- 500 行硬预算检查
    统计未提交(或相对指定基点)的业务变更行，排除生成/迁移/文档目录。
    自定位 git 仓库根，不硬编码绝对路径，可随本技能目录整体搬运。

    用法:
        powershell -NoProfile -ExecutionPolicy Bypass -File line-budget.ps1                # 默认比较 HEAD
        powershell -NoProfile -ExecutionPolicy Bypass -File line-budget.ps1 <rev>          # 比较指定基点

    退出码:
        0  -> PASS  (变更 <= 200)
        1  -> WARN  (200 < 变更 <= 500)
        2  -> FAIL  (变更 > 500 或脚本/仓库错误)
#>
$ErrorActionPreference = "Stop"

# --- 定位 git 仓库根 ---
$repoRoot = git rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -ne 0 -or -not $repoRoot) {
    Write-Host "[line-budget] 错误: 当前目录不在 git 仓库内" -ForegroundColor Red
    exit 2
}
$repoRoot = $repoRoot.Trim()
Push-Location $repoRoot
try {
    # --- 基点 ---
    $base = if ($args.Count -gt 0) { $args[0] } else { "HEAD" }

    # --- 提 NumStat:  added  deleted  path (Tab 分隔) ---
    $rows = git diff --numstat "$base" -- 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[line-budget] 错误: git diff 失败 (基点: $base)" -ForegroundColor Red
        exit 2
    }

    # --- 排除规则（生成 / 迁移 / 文档 / 锁与压缩源码）---
    $excludedDir  = '^(\.trae|\.github|build|out|dist|target|\.gradle|\.idea|node_modules|generated|migrations?|docs?)\/'
    $excludedFile = '\.(lock|min\.js|min\.css|map|md|mdx)$'

    $businessLines = 0
    $scannedFiles  = 0
    $excludedLines = 0

    foreach ($row in $rows) {
        if (-not $row) { continue }
        $f = $row -split "`t"
        if ($f.Count -lt 3) { continue }
        $added   = [int]$f[0]
        $deleted = [int]$f[1]
        $path    = ($f[2..($f.Count-1)] -join "`t").Trim()

        $isExcluded = $path -match $excludedDir -or $path -match $excludedFile
        if ($isExcluded) {
            $excludedLines += ($added + $deleted)
            continue
        }
        $businessLines += ($added + $deleted)
        $scannedFiles++
    }

    Write-Host ("[line-budget] 基点: $base") -ForegroundColor Cyan
    Write-Host "业务变更行: $businessLines (新增+删除，已排除生成/迁移/文档 $excludedLines 行，扫描业务文件 $scannedFiles 个)"

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