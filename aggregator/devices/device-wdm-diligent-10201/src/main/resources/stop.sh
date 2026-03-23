#!/bin/bash

# 获取当前目录名
CURRENT_DIR_NAME=${PWD##*/}

# 构建 jar 文件名
JAR_FILE="$CURRENT_DIR_NAME.jar"

# 查找运行该 jar 的进程
PIDS=$(ps aux | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "No running process found for $JAR_FILE"
else
    echo "Stopping process(es) for $JAR_FILE: $PIDS"
    kill -9 $PIDS
    echo "Stopped $JAR_FILE"
fi