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
    throw "No git repository found; site was not published."
}

$branch = & git symbolic-ref --short HEAD
if ($LASTEXITCODE -ne 0 -or $branch -ne "main") {
    throw "Auto publish requires the main branch."
}
& git rev-parse --verify -q MERGE_HEAD *> $null
if ($LASTEXITCODE -eq 0) { throw "An unfinished merge exists; resolve it before publishing." }
# Do not include or disturb changes staged by a person or another task.
& git diff --cached --quiet
if ($LASTEXITCODE -ne 0) { throw "The index already contains staged changes; finish them before publishing." }

# Keep local detail pages current too; Pages also rebuilds these from history.json.
$historyExporter = Join-Path $PSScriptRoot "export_stock_history.py"
if (Test-Path -LiteralPath $historyExporter) {
    $historyPython = if ($env:STOCK_PYTHON) { $env:STOCK_PYTHON } else { "python" }
    & $historyPython $historyExporter
    if ($LASTEXITCODE -ne 0) { throw "Per-stock history export failed; site was not published." }
}

# Publish only the public daily market fields used to confirm chart entry patterns.
$chartExporter = Join-Path $PSScriptRoot "export_chart_ohlcv.py"
if (Test-Path -LiteralPath $chartExporter) {
    $chartPython = if ($env:STOCK_PYTHON) { $env:STOCK_PYTHON } else { "python" }
    & $chartPython $chartExporter
    if ($LASTEXITCODE -ne 0) { throw "Daily chart export failed; site was not published." }
}

$trackedPaths = @(
    "history_dashboard.html",
    "web/index.html",
    "web/technical-overlays.js",
    "web/video-entry.js",
    "web/data/ohlcv",
    "scripts/export_chart_ohlcv.py",
    "web/swing.js",
    "web/swing-ui.js",
    "web/swing.css",
    "web/data/trading_calendar.json",
    "web/data/latest.json",
    "web/data/history.json",
    "web/data/history",
    "scripts/export_stock_history.py",
    "scripts/build_pages_site.ps1",
    "web/data/snapshot_status.json",
    "web/data/data_quality.json",
    "web/data/ohlcv_quality.json",
    "web/data/trading_status.json",
    "web/data/nightly_status.json",
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
$siteDiffExit = $LASTEXITCODE
if ($siteDiffExit -gt 1) { throw "Cannot inspect staged site changes." }

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

if ($siteDiffExit -eq 1) {
    & git commit -m $message -- $resolvedTrackedPaths
    if ($LASTEXITCODE -ne 0) { throw "git commit failed." }
} else {
    Write-Host "No new site changes; checking previously unpushed commits."
}

# Merge rather than rewrite history. Git refuses to overwrite local edits.
# A conflicting merge is aborted, leaving the generated commit available to retry.
& git fetch origin main
if ($LASTEXITCODE -ne 0) { throw "git fetch failed; local commits retained for retry." }
& git merge --no-edit origin/main
if ($LASTEXITCODE -ne 0) {
    & git rev-parse --verify -q MERGE_HEAD *> $null
    if ($LASTEXITCODE -eq 0) {
        & git merge --abort
        if ($LASTEXITCODE -ne 0) { throw "Merge failed and could not be aborted; manual recovery required." }
    }
    throw "Remote changes could not be merged safely. Local edits retained; site was not published."
}

& git push origin main
if ($LASTEXITCODE -ne 0) {
    throw "git push failed."
}

Write-Host "GitHub auto push completed."
