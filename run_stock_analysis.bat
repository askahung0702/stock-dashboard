@echo off
setlocal

cd /d "%~dp0"
set "EXIT_CODE=0"

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
set "RUN_MODE=full"
set "RUN_STAGE=full"
set "HAS_LIMITED_ARGS="
set "JAVA_STAGE_OPTS="

if /I "%~1"=="close" goto mode_close
if /I "%~1"=="full" goto mode_full
if /I "%~1"=="news-event" goto mode_news_event
if /I "%~1"=="news-only" goto mode_news_event
if /I "%~1"=="export" goto mode_export
if /I "%~1"=="export-now" goto mode_export_now
if not "%~1"=="" set "HAS_LIMITED_ARGS=1"
goto mode_done

:mode_close
set "RUN_MODE=close"
set "RUN_STAGE=close"
goto mode_done

:mode_full
set "RUN_MODE=full"
set "RUN_STAGE=full"
goto mode_done

:mode_news_event
set "RUN_MODE=news-event"
set "RUN_STAGE=news-event"
goto mode_done

:mode_export
"%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\request_export.ps1" -Mode manual
exit /b %ERRORLEVEL%

:mode_export_now
set "RUN_MODE=export-now"
set "RUN_STAGE=export"
goto mode_done

:mode_done

if /I "%RUN_MODE%"=="close" if not "%~2"=="" set "HAS_LIMITED_ARGS=1"
if /I "%RUN_MODE%"=="full" if not "%~2"=="" set "HAS_LIMITED_ARGS=1"
if /I "%RUN_MODE%"=="news-event" if not "%~2"=="" set "HAS_LIMITED_ARGS=1"

if /I "%RUN_MODE%"=="close" (
    set "JAVA_STAGE_OPTS=-Dstock.analysis.stageOnly=true -Dstock.analyzer.perStockPauseMs=150 -Dstock.close.deferNews=true -Dstock.close.deferEventRisk=true"
)
if /I "%RUN_MODE%"=="news-event" (
    set "JAVA_STAGE_OPTS=-Dstock.news.stageOnly=true -Dstock.analyzer.perStockPauseMs=150"
)

set "LOCK_NAME=%RUN_MODE%"
set "LOCK_NAME=%LOCK_NAME:-=_%"
set "LOCK_DIR=%~dp0.run_stock_analysis_%LOCK_NAME%.lock"

if exist "%LOCK_DIR%" (
    echo Another %RUN_MODE% run is already in progress. Skip duplicated launch.
    set "EXIT_CODE=9"
    goto cleanup
)

mkdir "%LOCK_DIR%" >nul 2>nul
if errorlevel 1 (
    echo Cannot acquire %RUN_MODE% run lock. Skip duplicated launch.
    set "EXIT_CODE=9"
    goto cleanup
)
set "LOCK_ACQUIRED=1"
> "%LOCK_DIR%\started_at.txt" echo %DATE% %TIME%

if /I "%RUN_MODE%"=="news-event" goto run_news_event
if /I "%RUN_MODE%"=="export-now" goto run_export_now
goto run_analysis

:run_analysis
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
"%JAVA_CMD%" %JAVA_STAGE_OPTS% -cp "bin;%APP_CLASSPATH%" stock.StockAnalysis %*
set "EXIT_CODE=%ERRORLEVEL%"
goto after_primary_run

:run_news_event
echo Compiling StockNewsOnlyAnalysis.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%APP_CLASSPATH%" -d bin -sourcepath src src\stock\StockNewsOnlyAnalysis.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    set "EXIT_CODE=1"
    goto cleanup
)

echo.
echo Running stock.StockNewsOnlyAnalysis %*
if "%~2"=="" (
    "%JAVA_CMD%" %JAVA_STAGE_OPTS% -cp "bin;%APP_CLASSPATH%" stock.StockNewsOnlyAnalysis
) else (
    "%JAVA_CMD%" %JAVA_STAGE_OPTS% -cp "bin;%APP_CLASSPATH%" stock.StockNewsOnlyAnalysis %~2
)
set "EXIT_CODE=%ERRORLEVEL%"
goto after_primary_run

:run_export_now
echo Compiling StockStageExporter.java...
"%JAVAC_CMD%" -encoding UTF-8 -cp "%APP_CLASSPATH%" -d bin -sourcepath src src\stock\StockStageExporter.java
if errorlevel 1 (
    echo.
    echo Compile failed.
    set "EXIT_CODE=1"
    goto cleanup
)

echo.
echo Running stock.StockStageExporter...
"%JAVA_CMD%" -cp "bin;%APP_CLASSPATH%" stock.StockStageExporter
set "EXIT_CODE=%ERRORLEVEL%"

if "%EXIT_CODE%"=="0" (
    echo.
    echo Running early_breakout_screener.py...
    python "%~dp0scripts\early_breakout_screener.py"
    if errorlevel 1 echo [WARN] early_breakout_screener.py exited with error, continuing...
)

if "%EXIT_CODE%"=="0" if not defined STOCK_SKIP_AUTO_PUSH (
    echo Auto-pushing exported site updates to GitHub...
    "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\auto_git_push.ps1" -Mode export
    if errorlevel 1 (
        set "EXIT_CODE=1"
        echo Auto push failed.
    )
)
goto cleanup

:after_primary_run
echo.
if not "%EXIT_CODE%"=="0" (
    echo Program exited with code %EXIT_CODE%.
)

if "%EXIT_CODE%"=="0" (
    if /I "%RUN_MODE%"=="close" goto after_close_run
    if /I "%RUN_MODE%"=="news-event" goto after_news_event_run
    goto after_full_run
)
goto cleanup

:after_close_run
if defined HAS_LIMITED_ARGS (
    echo Limited close run detected, skip export request.
) else (
    echo Requesting serialized export after close stage...
    "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\request_export.ps1" -Mode close
    if errorlevel 1 set "EXIT_CODE=%ERRORLEVEL%"
)
goto cleanup

:after_news_event_run
if defined HAS_LIMITED_ARGS (
    echo Limited news-event run detected, skip export request.
) else (
    echo Requesting serialized export after news-event stage...
    "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\request_export.ps1" -Mode news-event
    if errorlevel 1 set "EXIT_CODE=%ERRORLEVEL%"
)
goto cleanup

:after_full_run
echo.
echo Running early_breakout_screener.py...
python "%~dp0scripts\early_breakout_screener.py"
if errorlevel 1 echo [WARN] early_breakout_screener.py exited with error, continuing...

echo.
echo Running early_breakout_forward_returns.py...
python "%~dp0scripts\early_breakout_forward_returns.py"
if errorlevel 1 echo [WARN] early_breakout_forward_returns.py exited with error, continuing...

echo.
echo Running early_breakout_portfolio_tracker.py...
python "%~dp0scripts\early_breakout_portfolio_tracker.py"
if errorlevel 1 echo [WARN] early_breakout_portfolio_tracker.py exited with error, continuing...

if not defined HAS_LIMITED_ARGS (
    if defined STOCK_SKIP_AUTO_PUSH (
        echo Auto push skipped because STOCK_SKIP_AUTO_PUSH is set.
    ) else (
        echo Auto-pushing site updates to GitHub...
                "%POWERSHELL_CMD%" -ExecutionPolicy Bypass -File "%~dp0scripts\auto_git_push.ps1" -Mode %RUN_STAGE%
                if errorlevel 1 (
                    set "EXIT_CODE=1"
                    echo Auto push failed.
                )
    )
) else (
    echo Limited run detected, skip GitHub auto push.
)
goto cleanup

:cleanup
if defined LOCK_ACQUIRED rd /s /q "%LOCK_DIR%" >nul 2>nul
if not defined STOCK_ANALYSIS_NO_PAUSE pause
exit /b %EXIT_CODE%
