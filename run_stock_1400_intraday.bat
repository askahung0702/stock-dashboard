@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"
set "LOG_DIR=%~dp0logs"
set "LOG_FILE=%LOG_DIR%\stock1400_last.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
> "%LOG_FILE%" echo [14:00] Started %DATE% %TIME%

echo [14:00] Running intraday stock technical snapshot...
>> "%LOG_FILE%" echo [14:00] Running intraday stock technical snapshot...
set "STOCK_SKIP_EXPORT_REQUEST=1"
call "%~dp0run_stock_analysis.bat" intraday-close >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
    >> "%LOG_FILE%" echo [14:00] intraday-close failed at %DATE% %TIME%
    exit /b 1
)

echo.
echo [14:00] Running Taiwan index futures price update and final export...
>> "%LOG_FILE%" echo [14:00] Running Taiwan index futures price update and final export...
set "STOCK_SKIP_EXPORT_REQUEST="
call "%~dp0run_stock_analysis.bat" market-futures >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"
>> "%LOG_FILE%" echo [14:00] Finished with code %EXIT_CODE% at %DATE% %TIME%
exit /b %EXIT_CODE%
