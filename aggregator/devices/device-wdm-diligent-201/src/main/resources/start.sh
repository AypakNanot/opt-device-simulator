#!/bin/bash

# Get current directory name
CURRENT_DIR_NAME=${PWD##*/}

# Build jar file name
JAR_NAME="$CURRENT_DIR_NAME.jar"

# Default parameters
PORT=$(echo $CURRENT_DIR_NAME | sed 's/device-//')
DEVICE=1
THREAD=1

# Parse command line arguments
while getopts "d:t:" opt; do
  case $opt in
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

# Copy original jar as temporary name
cp device.jar $JAR_NAME

# Run in background
nohup java -jar $JAR_NAME -p $PORT -d $DEVICE -t $THREAD > device-$PORT.log 2>&1 &

echo "Started device on port $PORT with device number $DEVICE"
echo "Check process with: jps | grep device-$PORT"
