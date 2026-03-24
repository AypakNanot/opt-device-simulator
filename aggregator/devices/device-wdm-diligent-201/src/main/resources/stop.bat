@echo off
setlocal enabledelayedexpansion

REM Get current directory name
for %%I in (.) do set CURRENT_DIR_NAME=%%~nxI

REM Build jar file name
set JAR_FILE=%CURRENT_DIR_NAME%.jar

REM Find process running this jar
for /f "tokens=2" %%i in ('tasklist /FI "WINDOWTITLE eq java*" /FI "IMAGENAME eq java.exe" /FO CSV /NH 2^>nul ^| find "java.exe"') do (
    for /f "tokens=1" %%p in ('wmic process where "ProcessId=%%i" get CommandLine 2^>nul ^| find ".jar"') do (
        echo %%p | find "%JAR_FILE%" >nul
        if not errorlevel 1 (
            echo Stopping process for %JAR_FILE%: %%i
            taskkill /F /PID %%i >nul 2>&1
        )
    )
)

echo Stopped %JAR_FILE%
