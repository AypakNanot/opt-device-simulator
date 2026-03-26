#!/bin/bash
# ========================================================================
# Device Launcher Stop Script for Linux/Unix
# ========================================================================

echo "Stopping Device Launcher..."

# Find and kill the Device Launcher process
PID=$(ps aux | grep "[D]eviceLauncher" | awk '{print $2}')

if [ -n "$PID" ]; then
    echo "Killing process $PID"
    kill "$PID"
    echo "Device Launcher stopped."
else
    echo "Device Launcher is not running."
fi
