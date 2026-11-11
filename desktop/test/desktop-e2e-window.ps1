param(
  [Parameter(Mandatory=$true)][ValidateSet('resize','cleanup')][string] $Action,
  [Parameter(Mandatory=$true)][string] $Root,
  [int] $Width,
  [int] $Height
)
$ErrorActionPreference = 'Stop'
$resolvedRoot = [IO.Path]::GetFullPath($Root).TrimEnd('\')
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
if ([IO.Path]::GetDirectoryName($resolvedRoot) -ine $tempRoot -or
    [IO.Path]::GetFileName($resolvedRoot) -notmatch '^treelearn-main-e2e-[a-zA-Z0-9]+$') { throw 'ISOLATED_ROOT_INVALID' }
$profile = Join-Path $resolvedRoot 'profile'
$processes = @(Get-CimInstance Win32_Process | Where-Object {
  $_.ExecutablePath -and $_.CommandLine -and
  $_.ExecutablePath.StartsWith($resolvedRoot + '\temp\', [StringComparison]::OrdinalIgnoreCase) -and
  $_.CommandLine.IndexOf($profile, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
  $_.Name -notmatch '^(pwsh|powershell)\.exe$'
})
if ($Action -eq 'cleanup') {
  foreach ($item in $processes) { Stop-Process -Id $item.ProcessId -Force -ErrorAction Stop }
  Write-Output "ISOLATED_PROCESSES_STOPPED count=$($processes.Count)"
  exit 0
}
$main = @($processes | Where-Object { $_.CommandLine -notmatch '(?:^|\s)--type=' })
if ($main.Count -ne 1) { throw 'ISOLATED_MAIN_PROCESS_NOT_UNIQUE' }
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class BranchTestWindow {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int left, top, right, bottom; }
    [DllImport("user32.dll", SetLastError=true)] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll", SetLastError=true)] public static extern bool GetWindowRect(IntPtr h, out RECT rect);
}
'@
$deadline = [DateTime]::UtcNow.AddSeconds(8)
$window = [IntPtr]::Zero
do {
  $process = Get-Process -Id $main[0].ProcessId -ErrorAction Stop
  $window = $process.MainWindowHandle
  if ($window -eq [IntPtr]::Zero) { Start-Sleep -Milliseconds 50 }
} while ($window -eq [IntPtr]::Zero -and [DateTime]::UtcNow -lt $deadline)
if ($window -eq [IntPtr]::Zero) { throw 'ISOLATED_WINDOW_NOT_FOUND' }
if (-not [BranchTestWindow]::SetWindowPos($window, [IntPtr]::Zero, 0, 0, $Width, $Height, 0x0016)) { throw 'WINDOW_RESIZE_FAILED' }
$rect = New-Object BranchTestWindow+RECT
if (-not [BranchTestWindow]::GetWindowRect($window, [ref]$rect)) { throw 'WINDOW_RECT_FAILED' }
if (($rect.right - $rect.left) -ne $Width -or ($rect.bottom - $rect.top) -ne $Height) {
  throw "WINDOW_RECT_MISMATCH pid=$($main[0].ProcessId) expected=$Width`x$Height actual=$(($rect.right-$rect.left))x$(($rect.bottom-$rect.top))"
}
Write-Output "WINDOW_SIZE_OK pid=$($main[0].ProcessId) width=$Width height=$Height"
