param(
    [string]$Mode = "analysis"
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
    "web/data/latest.json",
    "web/data/history.json"
)

& git add -- $trackedPaths
if ($LASTEXITCODE -ne 0) {
    throw "git add failed."
}

& git diff --cached --quiet -- $trackedPaths
if ($LASTEXITCODE -eq 0) {
    Write-Host "No tracked site changes to push."
    exit 0
}

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$message = if ($Mode -eq "news-only") {
    "Auto update site after news-only run $timestamp"
} else {
    "Auto update site after full analysis $timestamp"
}

& git commit -m $message -- $trackedPaths
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed."
}

& git push origin main
if ($LASTEXITCODE -ne 0) {
    throw "git push failed."
}

Write-Host "GitHub auto push completed."
