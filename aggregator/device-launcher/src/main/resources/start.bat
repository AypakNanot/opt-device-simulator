@echo off
REM ========================================================================
REM Device Launcher Startup Script for Windows
REM ========================================================================

setlocal enabledelayedexpansion

REM Set the directory where the script is located
set "SCRIPT_DIR=%~dp0"

REM Find Java (prefer JAVA_HOME if set)
if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

REM Set classpath - use wildcard for lib folder (Java 6+ feature)
set "CLASSPATH=%SCRIPT_DIR%device-launcher.jar;%SCRIPT_DIR%lib\*"

REM Set default JVM options
set "JVM_OPTS=-Xmx256m"

REM Set default config file
set "CONFIG_FILE=%SCRIPT_DIR%config.json"

REM Parse command line arguments
:parse_args
if "%~1"=="" goto :run
if /i "%~1"=="-c" (
    set "CONFIG_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="--config" (
    set "CONFIG_FILE=%~2"
    shift
    shift
    goto :parse_args
)
if /i "%~1"=="-h" goto :help
if /i "%~1"=="--help" goto :help

REM Run the device launcher
:run
echo Starting Device Launcher...
echo Config file: %CONFIG_FILE%
"%JAVA_EXE%" %JVM_OPTS% -cp "%CLASSPATH%" com.optel.DeviceLauncher -c "%CONFIG_FILE%" %*
goto :eof

REM Show help
:help
echo Device Launcher - Multi-device instance manager
echo.
echo Usage: start.bat [options]
echo.
echo Options:
echo   -c, --config ^<file^>    Configuration file (default: config.json)
echo   -h, --help               Show this help message
echo.
echo Examples:
echo   start.bat
echo   start.bat -c config.json
echo   start.bat --config custom-config.json
goto :eof
