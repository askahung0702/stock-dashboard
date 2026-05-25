@echo off
setlocal

cd /d "%~dp0"
set "STOCK_ANALYSIS_NO_PAUSE=1"
set "STOCK_SKIP_EXPORT_REQUEST=1"
set "LOG_DIR=%~dp0logs"
set "LOG_FILE=%LOG_DIR%\stock_shareholder_insider_last.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
> "%LOG_FILE%" echo [shareholder-insider] Started %DATE% %TIME%

echo [shareholder-insider] Updating TDCC shareholder distribution and MOPS insider transfer data...
>> "%LOG_FILE%" echo [shareholder-insider] Updating TDCC shareholder distribution and MOPS insider transfer data...
call "%~dp0run_stock_analysis.bat" shareholder-insider >> "%LOG_FILE%" 2>&1
set "EXIT_CODE=%ERRORLEVEL%"

>> "%LOG_FILE%" echo [shareholder-insider] Finished with code %EXIT_CODE% at %DATE% %TIME%
exit /b %EXIT_CODE%
