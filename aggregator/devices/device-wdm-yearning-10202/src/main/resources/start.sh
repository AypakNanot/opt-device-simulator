#!/bin/bash

# Get current directory name
CURRENT_DIR_NAME=${PWD##*/}

# Build jar file name
JAR_NAME="$CURRENT_DIR_NAME.jar"

# Default parameters
PORT=17834
DEVICE=1
THREAD=1

echo "=========================================="
echo "  Device Simulator Startup"
echo "=========================================="
echo ""
echo "Current directory: $CURRENT_DIR_NAME"
echo "Default port: $PORT"
echo ""

# Check if command line arguments are provided
if [ $# -gt 0 ]; then
    # Parse command line arguments
    while getopts "p:d:t:" opt; do
      case $opt in
        p)
          PORT=$OPTARG
          ;;
        d)
          DEVICE=$OPTARG
          ;;
        t)
          THREAD=$OPTARG
          ;;
        *)
          exit 1
          ;;
      esac
    done
else
    # Interactive mode - prompt for input
    echo "Enter port (press Enter for default: $PORT):"
    read PORT_INPUT
    if [ -n "$PORT_INPUT" ]; then
        PORT=$PORT_INPUT
    fi

    echo "Enter device count (press Enter for default: $DEVICE):"
    read DEVICE_INPUT
    if [ -n "$DEVICE_INPUT" ]; then
        DEVICE=$DEVICE_INPUT
    fi

    echo "Enter thread pool size (press Enter for default: $THREAD):"
    read THREAD_INPUT
    if [ -n "$THREAD_INPUT" ]; then
        THREAD=$THREAD_INPUT
    fi
fi

echo ""
echo "------------------------------------------"
echo "Configuration:"
echo "  Port: $PORT"
echo "  Device Count: $DEVICE"
echo "  Thread Pool Size: $THREAD"
echo "------------------------------------------"
echo ""

# Copy original jar as temporary name
cp device.jar $JAR_NAME

# Run in background
nohup java -jar $JAR_NAME -p $PORT -d $DEVICE -t $THREAD > device-$PORT.log 2>&1 &

echo "Started device on port $PORT with device number $DEVICE"
echo "Check process with: jps | grep device-$PORT"
