@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"
set "LOG_DIR=%~dp0logs"
set "LOG_FILE=%LOG_DIR%\stock1700_last.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
> "%LOG_FILE%" echo [17:00] Started %DATE% %TIME%

echo [17:00] Updating foreign futures position first...
>> "%LOG_FILE%" echo [17:00] Updating foreign futures position first...
set "STOCK_SKIP_EXPORT_REQUEST=1"
call "%~dp0run_stock_analysis.bat" futures-position >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
    echo [WARN] futures-position failed or unavailable, continuing close run...
    >> "%LOG_FILE%" echo [WARN] futures-position failed or unavailable, continuing close run...
)

echo.
echo [17:00] Running official post-close stock analysis and final export...
>> "%LOG_FILE%" echo [17:00] Running official post-close stock analysis and final export...
set "STOCK_SKIP_EXPORT_REQUEST="
call "%~dp0run_stock_analysis.bat" close >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"
>> "%LOG_FILE%" echo [17:00] Finished with code %EXIT_CODE% at %DATE% %TIME%
exit /b %EXIT_CODE%
