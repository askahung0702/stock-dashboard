@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"

echo [17:00] Updating foreign futures position first...
set "STOCK_SKIP_EXPORT_REQUEST=1"
call "%~dp0run_stock_analysis.bat" futures-position
if errorlevel 1 echo [WARN] futures-position failed or unavailable, continuing close run...

echo.
echo [17:00] Running official post-close stock analysis and final export...
set "STOCK_SKIP_EXPORT_REQUEST="
call "%~dp0run_stock_analysis.bat" close
exit /b %ERRORLEVEL%
