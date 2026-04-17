@echo off
setlocal

cd /d "%~dp0"

set "JAVAC_CMD=javac"
set "JAVA_CMD=java"

if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe"
)
if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe"
)
if exist "C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe" if /I "%JAVAC_CMD%"=="javac" (
    set "JAVAC_CMD=C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe"
)

echo Compiling StockHistoryWebServer.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib\jsoup-1.15.1.jar;lib\json-simple-1.1.1.jar" -d bin -sourcepath src src\stock\StockHistoryWebServer.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    if not defined STOCK_ANALYSIS_NO_PAUSE pause
    exit /b 1
)

echo.
set "DISPLAY_HOST=%STOCK_SERVER_HOST%"
if "%DISPLAY_HOST%"=="" set "DISPLAY_HOST=0.0.0.0"
set "DISPLAY_PORT=%STOCK_SERVER_PORT%"
if "%DISPLAY_PORT%"=="" set "DISPLAY_PORT=8788"

if not "%~1"=="" (
    echo %~1| findstr /r "^[0-9][0-9]*$" >nul
    if not errorlevel 1 set "DISPLAY_PORT=%~1"
    if errorlevel 1 set "DISPLAY_HOST=%~1"
)
if not "%~2"=="" set "DISPLAY_HOST=%~2"

echo Starting dashboard server on %DISPLAY_HOST%:%DISPLAY_PORT%
echo Dashboard:   http://%DISPLAY_HOST%:%DISPLAY_PORT%/
echo Old view:    http://%DISPLAY_HOST%:%DISPLAY_PORT%/old
echo Latest API:  http://%DISPLAY_HOST%:%DISPLAY_PORT%/api/latest
echo History API: http://%DISPLAY_HOST%:%DISPLAY_PORT%/api/history
echo Press Ctrl+C to stop.
echo.
"%JAVA_CMD%" -cp "bin;lib\jsoup-1.15.1.jar;lib\json-simple-1.1.1.jar" stock.StockHistoryWebServer %*
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo Server exited with code %EXIT_CODE%.
)

if not defined STOCK_ANALYSIS_NO_PAUSE pause
exit /b %EXIT_CODE%

