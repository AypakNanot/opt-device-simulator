@echo off
REM ========================================================================
REM Device Launcher Stop Script for Windows
REM ========================================================================

echo Stopping Device Launcher...

REM Find and kill the Device Launcher process
for /f "tokens=2" %%i in ('tasklist /v ^| findstr "DeviceLauncher"') do (
    echo Killing process %%i
    taskkill /F /PID %%i
)

echo Device Launcher stopped.
pause
