<#
.SYNOPSIS
    Builds a single handoff file for context transfer to a new chat session.
    ASCII-only on purpose: avoids PS 5.1 Cyrillic/encoding parser issues.
.EXAMPLE
    .\handoff.ps1
    .\handoff.ps1 -ProgressTail 100
#>
param(
    [string]$Root         = $PSScriptRoot,
    [int]   $ProgressTail = 300,
    [int[]] $Ports        = @(8080,8081,8082,8090,8091,8092,8093,8094,8095,8096,8097,8098)
)
$ErrorActionPreference = "Continue"

if (-not $Root) { $Root = (Get-Location).Path }
if (-not (Test-Path (Join-Path $Root "pom.xml"))) {
    Write-Host "pom.xml not found in $Root. Put handoff.ps1 at repo root." -ForegroundColor Red
    exit 1
}
Set-Location $Root

$ts    = Get-Date -Format "yyyy-MM-dd-HHmm"
$out   = Join-Path $Root "handoff-$ts.md"
$enc   = New-Object System.Text.UTF8Encoding $false
$utf8  = [System.Text.Encoding]::UTF8
$sb    = New-Object System.Text.StringBuilder
function L([string]$s = "") { [void]$sb.AppendLine($s) }

function Get-Utf8Lines([string]$path) {
    if (Test-Path $path) { [System.IO.File]::ReadAllLines($path, $utf8) } else { @() }
}
function Get-HandoffBlock([string]$progressPath) {
    $lines = Get-Utf8Lines $progressPath
    if (-not $lines -or $lines.Count -eq 0) { return @("(PROGRESS.md empty)") }
    $start = -1; $end = $lines.Count
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($start -lt 0 -and $lines[$i] -match '^##\s.*HANDOFF') { $start = $i; continue }
        if ($start -ge 0 -and $i -gt $start -and $lines[$i] -match '^##\s') { $end = $i; break }
    }
    if ($start -ge 0) { return $lines[$start..($end - 1)] }
    return @("(HANDOFF block not found in PROGRESS.md)")
}

L "# HANDOFF iktk-complex - $ts"
L ""
L "> Single snapshot for context recovery. Attach this file + bootstrap-prompt."
L ""

L "## 1. PROJECT.md (constitution)"
L ""
$projectPath = Join-Path $Root "PROJECT.md"
if (Test-Path $projectPath) {
    L '```markdown'
    Get-Utf8Lines $projectPath | ForEach-Object { L $_ }
    L '```'
} else { L "(PROJECT.md not found)" }
L ""

L "## 2. HANDOFF block from PROGRESS.md"
L ""
L '```markdown'
Get-HandoffBlock (Join-Path $Root "PROGRESS.md") | ForEach-Object { L $_ }
L '```'
L ""

L "## 3. Git"
L '```'
$b = (& git rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
$h = (& git rev-parse --short HEAD      2>$null | Out-String).Trim()
if ($b) { L "branch: $b" }
if ($h) { L "HEAD:   $h" }
L ""
L "# git status --short"
& git status --short 2>$null | ForEach-Object { L $_ }
L ""
L "# git log --oneline -15"
& git log --oneline -15 2>$null | ForEach-Object { L $_ }
L '```'
L ""

L "## 4. Ports"
L '```'
foreach ($port in $Ports) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($c) {
        $ownPid = ($c | Select-Object -First 1).OwningProcess
        $proc   = Get-Process -Id $ownPid -ErrorAction SilentlyContinue
        $pname  = if ($proc) { $proc.ProcessName } else { "?" }
        L ("{0,5}  LISTEN  pid={1,-6} {2}" -f $port, $ownPid, $pname)
    } else {
        L ("{0,5}  -" -f $port)
    }
}
L '```'
L ""

L "## 5. *.log (top-30, newest first)"
L '```'
$logs = Get-ChildItem -Path $Root -Filter *.log -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 30
if ($logs) {
    $logs | ForEach-Object {
        L ("{0:yyyy-MM-dd HH:mm}  {1,10}  {2}" -f $_.LastWriteTime, $_.Length, $_.FullName.Replace($Root + '\', ''))
    }
} else { L "(no *.log)" }
L '```'
L ""

L "## 6. Tail of build logs (last 5 each)"
$seen = @{}
foreach ($pat in @("*compile*.log", "*verify*.log")) {
    Get-ChildItem -Path $Root -Filter $pat -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 5 |
        ForEach-Object {
            if ($seen[$_.FullName]) { return }
            $seen[$_.FullName] = $true
            L ""
            L "### $($_.FullName.Replace($Root + '\', ''))   ($($_.LastWriteTime))"
            L '```'
            Get-Content $_.FullName -Tail 15 -Encoding UTF8 -ErrorAction SilentlyContinue | ForEach-Object { L $_ }
            L '```'
        }
}
L ""

L "## 7. Modules (root pom.xml)"
L '```'
$pom = Join-Path $Root "pom.xml"
if (Test-Path $pom) {
    try {
        [xml]$x = [System.IO.File]::ReadAllText($pom, $utf8)
        @($x.project.modules.module) | Where-Object { $_ } | ForEach-Object { L "- $_" }
    } catch { L "cannot parse pom.xml: $_" }
} else { L "(pom.xml not found)" }
L '```'
L ""

L "## 8. PROGRESS.md tail ($ProgressTail lines)"
L '```markdown'
$progressPath = Join-Path $Root "PROGRESS.md"
if (Test-Path $progressPath) {
    $plines = Get-Utf8Lines $progressPath
    $start  = [Math]::Max(0, $plines.Count - $ProgressTail)
    $plines[$start..($plines.Count - 1)] | ForEach-Object { L $_ }
} else { L "(PROGRESS.md not found)" }
L '```'

[System.IO.File]::WriteAllText($out, $sb.ToString(), $enc)

$size = (Get-Item $out).Length
Write-Host ""
Write-Host "OK: $out" -ForegroundColor Green
Write-Host ("Size: {0:N0} bytes" -f $size)
if ($size -gt 150KB) { Write-Host "WARN: > 150KB. Reduce -ProgressTail." -ForegroundColor Yellow }
if ($size -gt 300KB) { Write-Host "CRITICAL: > 300KB. Reduce -ProgressTail to 100." -ForegroundColor Red }