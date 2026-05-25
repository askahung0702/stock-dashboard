@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"
set "LOG_DIR=%~dp0logs"
set "LOG_FILE=%LOG_DIR%\stock2015_last.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
> "%LOG_FILE%" echo [20:15] Started %DATE% %TIME%

echo [20:15] Updating official post-close funding data...
>> "%LOG_FILE%" echo [20:15] Updating official post-close funding data...
call "%~dp0run_stock_analysis.bat" official-chip >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"

>> "%LOG_FILE%" echo [20:15] Finished with code %EXIT_CODE% at %DATE% %TIME%
exit /b %EXIT_CODE%
