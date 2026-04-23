param(
    [string]$Mode = "manual"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot

$lockDir = Join-Path $repoRoot ".export.lock"
$pendingFlag = Join-Path $repoRoot ".export_pending.flag"

if (Test-Path -LiteralPath $lockDir) {
    "pending from $Mode at $(Get-Date -Format o)" | Set-Content -LiteralPath $pendingFlag -Encoding UTF8
    Write-Host "Export is already running. Marked pending export request."
    exit 0
}

New-Item -ItemType Directory -Path $lockDir -ErrorAction Stop | Out-Null
try {
    do {
        if (Test-Path -LiteralPath $pendingFlag) {
            Remove-Item -LiteralPath $pendingFlag -Force
        }

        Write-Host "Running serialized export for request mode: $Mode"
        $env:STOCK_ANALYSIS_NO_PAUSE = "1"
        $env:STOCK_EXPORT_REQUEST_MODE = $Mode
        & cmd.exe /c "`"$repoRoot\run_stock_analysis.bat`" export-now"
        if ($LASTEXITCODE -ne 0) {
            throw "export-now failed with code $LASTEXITCODE"
        }

        $shouldRunAgain = Test-Path -LiteralPath $pendingFlag
        if ($shouldRunAgain) {
            Write-Host "A pending export request arrived during export. Running one more export pass."
        }
    } while ($shouldRunAgain)
} finally {
    if (Test-Path -LiteralPath $lockDir) {
        Remove-Item -LiteralPath $lockDir -Recurse -Force
    }
    Remove-Item Env:\STOCK_EXPORT_REQUEST_MODE -ErrorAction SilentlyContinue
}
