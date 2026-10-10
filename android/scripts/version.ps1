[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('show', 'patch', 'minor', 'major')]
    [string]$Action = 'show',
    [string]$Path
)

if ([string]::IsNullOrWhiteSpace($Path)) {
    $Path = Join-Path $PSScriptRoot '..\version.properties'
}
$resolvedPath = [System.IO.Path]::GetFullPath($Path)
if (-not [System.IO.File]::Exists($resolvedPath)) {
    throw "Version source does not exist: $resolvedPath"
}

$content = [System.IO.File]::ReadAllText($resolvedPath)
$normalized = $content.Trim()
$match = [regex]::Match($normalized, '^TREELEARN_VERSION=(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$')
if (-not $match.Success) {
    throw 'Version source must contain exactly TREELEARN_VERSION=MAJOR.MINOR.PATCH'
}

$major = [int]$match.Groups[1].Value
$minor = [int]$match.Groups[2].Value
$patch = [int]$match.Groups[3].Value
if ($major -gt 999 -or $minor -gt 999 -or $patch -gt 999) {
    throw 'Each version component must be between 0 and 999'
}

switch ($Action) {
    'patch' {
        if ($patch -ge 999) { throw 'Patch component is already 999; bump minor instead' }
        $patch++
    }
    'minor' {
        if ($minor -ge 999) { throw 'Minor component is already 999; bump major instead' }
        $minor++
        $patch = 0
    }
    'major' {
        if ($major -ge 999) { throw 'Major component is already 999' }
        $major++
        $minor = 0
        $patch = 0
    }
}

$nextVersion = "$major.$minor.$patch"
if ($Action -ne 'show') {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($resolvedPath, "TREELEARN_VERSION=$nextVersion`r`n", $utf8NoBom)
}

Write-Output "TREELEARN_VERSION=$nextVersion"
