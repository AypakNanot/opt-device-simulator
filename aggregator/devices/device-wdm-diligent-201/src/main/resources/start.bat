@echo off
setlocal enabledelayedexpansion

REM Get current directory name
for %%I in (.) do set CURRENT_DIR_NAME=%%~nxI

REM Build jar file name
set JAR_NAME=%CURRENT_DIR_NAME%.jar

REM Default parameters
set PORT=17832
set DEVICE=1
set THREAD=1

echo ==========================================
echo   Device Simulator Startup
echo ==========================================
echo.
echo Current directory: %CURRENT_DIR_NAME%
echo Default port: %PORT%
echo.

REM Check if command line arguments are provided
if "%~1"=="" goto interactive

REM Parse command line arguments
:parse
if "%~1"=="" goto endparse
if "%~1"=="-p" (
    set PORT=%~2
    shift
    shift
    goto parse
)
if "%~1"=="-d" (
    set DEVICE=%~2
    shift
    shift
    goto parse
)
if "%~1"=="-t" (
    set THREAD=%~2
    shift
    shift
    goto parse
)
shift
goto parse

:interactive
REM Interactive mode - prompt for input
set /p DEVICE_INPUT="Enter device count (press Enter for default: %DEVICE%): "
if not "!DEVICE_INPUT!"=="" set DEVICE=!DEVICE_INPUT!

set /p THREAD_INPUT="Enter thread pool size (press Enter for default: %THREAD%): "
if not "!THREAD_INPUT!"=="" set THREAD=!THREAD_INPUT!

:endparse

echo.
echo ------------------------------------------
echo Configuration:
echo   Port: %PORT%
echo   Device Count: %DEVICE%
echo   Thread Pool Size: %THREAD%
echo ------------------------------------------
echo.

REM Copy original jar as temporary name
copy /Y device.jar %JAR_NAME% >nul

REM Run in background (start without waiting)
start /B java -jar %JAR_NAME% -p %PORT% -d %DEVICE% -t %THREAD%

echo Started device on port %PORT% with device number %DEVICE%
echo Check process with: jps ^| findstr device-%PORT%
