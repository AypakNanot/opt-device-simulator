#!/bin/bash

# Get current directory name
CURRENT_DIR_NAME=${PWD##*/}

# Build jar file name
JAR_FILE="$CURRENT_DIR_NAME.jar"

# Find process running this jar
PIDS=$(ps aux | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "No running process found for $JAR_FILE"
else
    echo "Stopping process(es) for $JAR_FILE: $PIDS"
    kill -9 $PIDS
    echo "Stopped $JAR_FILE"
fi