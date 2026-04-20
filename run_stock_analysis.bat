@echo off
setlocal

cd /d "%~dp0"
set "EXIT_CODE=0"
set "LOCK_DIR=%~dp0.run_stock_analysis.lock"

if exist "%LOCK_DIR%" (
    echo Another run is already in progress. Skip duplicated launch.
    set "EXIT_CODE=9"
    goto cleanup
)

mkdir "%LOCK_DIR%" >nul 2>nul
if errorlevel 1 (
    echo Cannot acquire run lock. Skip duplicated launch.
    set "EXIT_CODE=9"
    goto cleanup
)
set "LOCK_ACQUIRED=1"
> "%LOCK_DIR%\started_at.txt" echo %DATE% %TIME%

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
set "RUN_STAGE=full"
set "RUN_STAGE_EXPLICIT="
set "HAS_LIMITED_ARGS="

if /I "%~1"=="close" (
    set "RUN_STAGE=close"
    set "RUN_STAGE_EXPLICIT=1"
) else (
    if /I "%~1"=="full" (
        set "RUN_STAGE=full"
        set "RUN_STAGE_EXPLICIT=1"
    )
)

if defined RUN_STAGE_EXPLICIT (
    if not "%~2"=="" set "HAS_LIMITED_ARGS=1"
) else (
    if not "%~1"=="" set "HAS_LIMITED_ARGS=1"
)

echo Compiling StockAnalysis.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%APP_CLASSPATH%" -d bin -sourcepath src src\stock\StockAnalysis.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    set "EXIT_CODE=1"
    goto cleanup
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
    echo.
    echo Running early_breakout_screener.py...
    python "%~dp0scripts\early_breakout_screener.py"
    if errorlevel 1 echo [WARN] early_breakout_screener.py exited with error, continuing...
)

if "%EXIT_CODE%"=="0" if /I not "%RUN_STAGE%"=="close" (
    echo.
    echo Running early_breakout_forward_returns.py...
    python "%~dp0scripts\early_breakout_forward_returns.py"
    if errorlevel 1 echo [WARN] early_breakout_forward_returns.py exited with error, continuing...
)

if "%EXIT_CODE%"=="0" if /I not "%RUN_STAGE%"=="close" (
    echo.
    echo Running early_breakout_portfolio_tracker.py...
    python "%~dp0scripts\early_breakout_portfolio_tracker.py"
    if errorlevel 1 echo [WARN] early_breakout_portfolio_tracker.py exited with error, continuing...
)

if "%EXIT_CODE%"=="0" (
    if not defined HAS_LIMITED_ARGS (
        if defined STOCK_SKIP_AUTO_PUSH (
            echo Auto push skipped because STOCK_SKIP_AUTO_PUSH is set.
        ) else (
            echo Auto-pushing site updates to GitHub...
            "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\auto_git_push.ps1" -Mode %RUN_STAGE%
            if errorlevel 1 (
                set "EXIT_CODE=%ERRORLEVEL%"
                echo Auto push failed with code %EXIT_CODE%.
            )
        )
    ) else (
        echo Limited run detected, skip GitHub auto push.
    )
)

:cleanup
if defined LOCK_ACQUIRED rd /s /q "%LOCK_DIR%" >nul 2>nul
if not defined STOCK_ANALYSIS_NO_PAUSE pause
exit /b %EXIT_CODE%
