$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$siteDir = Join-Path $repoRoot "site"

if (Test-Path -LiteralPath $siteDir) {
    Remove-Item -LiteralPath $siteDir -Recurse -Force
}

New-Item -ItemType Directory -Path $siteDir | Out-Null
New-Item -ItemType Directory -Path (Join-Path $siteDir "daily") | Out-Null

$historyDashboard = Join-Path $repoRoot "history_dashboard.html"
$datedDashboards = Get-ChildItem -Path $repoRoot -File -Filter "stock_dashboard_*.html" |
    Sort-Object Name

$selectedDashboard = $null
if (Test-Path -LiteralPath $historyDashboard) {
    $selectedDashboard = Get-Item -LiteralPath $historyDashboard
} elseif ($datedDashboards.Count -gt 0) {
    $selectedDashboard = $datedDashboards[-1]
}

if ($null -ne $selectedDashboard) {
    Copy-Item -LiteralPath $selectedDashboard.FullName -Destination (Join-Path $siteDir "index.html")
    Copy-Item -LiteralPath $selectedDashboard.FullName -Destination (Join-Path $siteDir "old.html")
} else {
    @"
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Stock Dashboard</title>
  <style>
    body { font-family: "Segoe UI", sans-serif; margin: 0; background: #f7f3ea; color: #2c2a28; }
    main { max-width: 840px; margin: 0 auto; padding: 48px 20px; }
    h1 { margin: 0 0 16px; }
    p { line-height: 1.7; }
    .card { background: #fffdf8; border: 1px solid #e3dacb; border-radius: 16px; padding: 20px; }
    code { background: #f1e8da; padding: 2px 6px; border-radius: 6px; }
  </style>
</head>
<body>
  <main>
    <div class="card">
      <h1>Stock Dashboard</h1>
      <p>目前 repo 內還沒有可發佈的 dashboard HTML。先執行 <code>run_stock_analysis.bat</code>，產生 <code>history_dashboard.html</code> 或最新的 <code>stock_dashboard_YYYYMMDD.html</code> 之後，再重新觸發 Pages workflow。</p>
    </div>
  </main>
</body>
</html>
"@ | Set-Content -LiteralPath (Join-Path $siteDir "index.html") -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $siteDir "index.html") -Destination (Join-Path $siteDir "old.html")
}

foreach ($dashboard in $datedDashboards) {
    Copy-Item -LiteralPath $dashboard.FullName -Destination (Join-Path $siteDir "daily" $dashboard.Name)
}

if (Test-Path -LiteralPath $historyDashboard) {
    Copy-Item -LiteralPath $historyDashboard -Destination (Join-Path $siteDir "history_dashboard.html")
}

@"
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url=/">
  <title>Redirecting</title>
</head>
<body>
  <p>Redirecting to dashboard...</p>
</body>
</html>
"@ | Set-Content -LiteralPath (Join-Path $siteDir "404.html") -Encoding UTF8

New-Item -ItemType File -Path (Join-Path $siteDir ".nojekyll") | Out-Null

@"
# Stock Dashboard Pages Output

- `index.html`: latest dashboard page
- `old.html`: alias to the same dashboard page for compatibility
- `history_dashboard.html`: included when available
- `daily/`: dated dashboard archives copied from repo root
"@ | Set-Content -LiteralPath (Join-Path $siteDir "README.md") -Encoding UTF8
