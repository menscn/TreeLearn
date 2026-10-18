# Parse-check every sibling .ps1 and this file's own directory (AGENTS.md tooling rule: a script
# that cannot be parsed never reaches its first line, so the guard has to live outside it).
# Pure ASCII. Exit code 0 = every file parsed, 1 = at least one parse error.
$ErrorActionPreference = 'Continue'
$here = $PSScriptRoot
$files = Get-ChildItem -LiteralPath $here -Recurse -Filter *.ps1 -File
$files = $files + (Get-ChildItem -LiteralPath (Join-Path $here '..\..\..\android\scripts') -Recurse -Filter *.ps1 -File -ErrorAction SilentlyContinue)
$bad = 0
$checked = 0
foreach ($file in $files) {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref] $tokens, [ref] $errors) | Out-Null
    $checked = $checked + 1
    if ($errors -and $errors.Count -gt 0) {
        $bad = $bad + 1
        foreach ($err in $errors) {
            Write-Output ('PARSE-ERROR ' + $file.FullName + ':' + $err.Extent.StartLineNumber + ': ' + $err.Message)
        }
    }
}
Write-Output ('checked=' + $checked + ' parse-errors=' + $bad)
if ($bad -gt 0) { Write-Output 'PARSE-VERDICT: FAIL'; exit 1 }
Write-Output 'PARSE-VERDICT: PASS'
exit 0
