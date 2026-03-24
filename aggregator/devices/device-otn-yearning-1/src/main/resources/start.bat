@echo off
setlocal enabledelayedexpansion

REM Get current directory name
for %%I in (.) do set CURRENT_DIR_NAME=%%~nxI

REM Build jar file name
set JAR_NAME=%CURRENT_DIR_NAME%.jar

REM Default parameters
set PORT=%CURRENT_DIR_NAME:device-=%
set DEVICE=1
set THREAD=1

REM Parse command line arguments
:parse
if "%~1"=="" goto endparse
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

:endparse

REM Copy original jar as temporary name
copy /Y device.jar %JAR_NAME% >nul

REM Run in background (start without waiting)
echo Starting device on port %PORT% with device number %DEVICE%
start /B java -jar %JAR_NAME% -p %PORT% -d %DEVICE% -t %THREAD%

echo Started device on port %PORT% with device number %DEVICE%
echo Check process with: jps ^| findstr device-%PORT%
