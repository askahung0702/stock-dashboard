param(
    [ValidateSet("analysis", "full", "close", "news-only", "news-event", "export")]
    [string]$Mode = "full"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot

if ($env:STOCK_SKIP_AUTO_PUSH -eq "1") {
    Write-Host "STOCK_SKIP_AUTO_PUSH=1, skip auto push."
    exit 0
}

if (-not (Test-Path -LiteralPath (Join-Path $repoRoot ".git"))) {
    Write-Host "No git repository found, skip auto push."
    exit 0
}

$trackedPaths = @(
    "history_dashboard.html",
    "web/index.html",
    "web/data/latest.json",
    "web/data/history.json",
    "web/data/snapshot_status.json",
    "web/data/close_full_diff_summary.json",
    "web/early_breakout",
    "web/performance",
    "config/theme_baskets_auto.csv"
)

$resolvedTrackedPaths = @()
foreach ($path in $trackedPaths) {
    if ($path.Contains("*") -or $path.Contains("?")) {
        $matches = Get-ChildItem -Path $path -ErrorAction SilentlyContinue
        foreach ($match in $matches) {
            $resolvedTrackedPaths += $match.FullName
        }
    } elseif (Test-Path -LiteralPath $path) {
        $resolvedTrackedPaths += $path
    }
}

if ($resolvedTrackedPaths.Count -eq 0) {
    Write-Host "No site paths exist to push."
    exit 0
}

& git add -- $resolvedTrackedPaths
if ($LASTEXITCODE -ne 0) {
    throw "git add failed."
}

& git diff --cached --quiet -- $resolvedTrackedPaths
if ($LASTEXITCODE -eq 0) {
    Write-Host "No tracked site changes to push."
    exit 0
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$message = if ($Mode -eq "news-only" -or $Mode -eq "news-event") {
    "Auto update site after news-only run $timestamp"
} elseif ($Mode -eq "close") {
    "Auto update site after close-stage run $timestamp"
} elseif ($Mode -eq "export") {
    "Auto export staged site update $timestamp"
} else {
    "Auto update site after full analysis $timestamp"
}

& git commit -m $message -- $resolvedTrackedPaths
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed."
}

& git push origin main
if ($LASTEXITCODE -ne 0) {
    throw "git push failed."
}

Write-Host "GitHub auto push completed."
