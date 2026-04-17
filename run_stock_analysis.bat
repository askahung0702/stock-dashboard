@echo off
setlocal

cd /d "%~dp0"

set "JAVAC_CMD=javac"
set "JAVA_CMD=java"
set "POWERSHELL_CMD=powershell.exe"

if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe" (
    set "JAVAC_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\javac.exe"
)
if exist "C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe" (
    set "JAVA_CMD=C:\Program Files\OpenLogic\jdk-21.0.6.7-hotspot\bin\java.exe"
)
if exist "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" (
    set "POWERSHELL_CMD=C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe"
)
if exist "C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe" if /I "%JAVAC_CMD%"=="javac" (
    set "JAVAC_CMD=C:\Progra~1\Java\jdk1.8.0_202\bin\javac.exe"
)

set "APP_CLASSPATH=lib\*"

echo Compiling StockAnalysis.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%APP_CLASSPATH%" -d bin -sourcepath src src\stock\StockAnalysis.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    if not defined STOCK_ANALYSIS_NO_PAUSE pause
    exit /b 1
)

echo.
echo Running stock.StockAnalysis %*
"%JAVA_CMD%" -cp "bin;%APP_CLASSPATH%" stock.StockAnalysis %*
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
    echo Program exited with code %EXIT_CODE%.
)

if "%EXIT_CODE%"=="0" (
    if "%~1"=="" (
        if defined STOCK_SKIP_AUTO_PUSH (
            echo Auto push skipped because STOCK_SKIP_AUTO_PUSH is set.
        ) else (
            echo Auto-pushing site updates to GitHub...
            "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\auto_git_push.ps1" -Mode analysis
            if errorlevel 1 (
                set "EXIT_CODE=%ERRORLEVEL%"
                echo Auto push failed with code %EXIT_CODE%.
            )
        )
    ) else (
        echo Limited run detected, skip GitHub auto push.
    )
)

if not defined STOCK_ANALYSIS_NO_PAUSE pause
exit /b %EXIT_CODE%
