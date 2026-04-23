@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"

echo [14:00] Running intraday stock technical snapshot...
set "STOCK_SKIP_EXPORT_REQUEST=1"
call "%~dp0run_stock_analysis.bat" intraday-close
if errorlevel 1 exit /b %ERRORLEVEL%

echo.
echo [14:00] Running Taiwan index futures price update and final export...
set "STOCK_SKIP_EXPORT_REQUEST="
call "%~dp0run_stock_analysis.bat" market-futures
exit /b %ERRORLEVEL%
