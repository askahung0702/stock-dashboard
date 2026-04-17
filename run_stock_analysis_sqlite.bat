@echo off
setlocal

cd /d "%~dp0"

set "JAVAC_CMD=javac"
set "JAVA_CMD=java"
set "APP_CLASSPATH=lib\*"

if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe"
)
if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe"
)
if exist "C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe" if /I "%JAVAC_CMD%"=="javac" (
    set "JAVAC_CMD=C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe"
)

echo Compiling StockAnalysis.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%APP_CLASSPATH%" -d bin -sourcepath src src\stock\StockAnalysis.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    if not defined STOCK_ANALYSIS_NO_PAUSE pause
    exit /b 1
)

echo.
echo Running stock.StockAnalysis with SQLite mode %*
"%JAVA_CMD%" -Dstock.history.storage=sqlite -cp "bin;%APP_CLASSPATH%" stock.StockAnalysis %*
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo Program exited with code %EXIT_CODE%.
)

if not defined STOCK_ANALYSIS_NO_PAUSE pause
exit /b %EXIT_CODE%
