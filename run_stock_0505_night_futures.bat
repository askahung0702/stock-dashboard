@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"
set "LOG_DIR=%~dp0logs"
set "LOG_FILE=%LOG_DIR%\stock0505_night_last.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
> "%LOG_FILE%" echo [05:05] Started %DATE% %TIME%

echo [05:05] Updating Taiwan index futures night-session close...
>> "%LOG_FILE%" echo [05:05] Updating Taiwan index futures night-session close...
call "%~dp0run_stock_analysis.bat" market-futures-night >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"

>> "%LOG_FILE%" echo [05:05] Finished with code %EXIT_CODE% at %DATE% %TIME%
exit /b %EXIT_CODE%
