@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"

echo [23:00] Refreshing foreign futures position for final overnight view...
set "STOCK_SKIP_EXPORT_REQUEST=1"
call "%~dp0run_stock_analysis.bat" futures-position
if errorlevel 1 echo [WARN] futures-position failed or unavailable, continuing full run...

echo.
echo [23:00] Running full stock analysis, news/event risk, reports, export and push...
set "STOCK_SKIP_EXPORT_REQUEST="
call "%~dp0run_stock_analysis.bat" full
exit /b %ERRORLEVEL%
