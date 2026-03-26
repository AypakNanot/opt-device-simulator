#!/bin/bash
# ========================================================================
# Device Launcher Startup Script for Linux/Unix
# ========================================================================

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Find Java (prefer JAVA_HOME if set)
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

# Set classpath - include lib folder with all dependencies
CLASSPATH="${SCRIPT_DIR}/device-launcher.jar"
for jar in "${SCRIPT_DIR}"/lib/*.jar; do
    CLASSPATH="${CLASSPATH}:${jar}"
done

# Set default JVM options
JVM_OPTS="-Xmx256m"

# Set default config file
CONFIG_FILE="${SCRIPT_DIR}/config.json"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        -h|--help)
            echo "Device Launcher - Multi-device instance manager"
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  -c, --config <file>    Configuration file (default: config.json)"
            echo "  -h, --help             Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0"
            echo "  $0 -c config.json"
            echo "  $0 --config custom-config.json"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# Run the device launcher
echo "Starting Device Launcher..."
echo "Config file: ${CONFIG_FILE}"
exec "$JAVA_EXE" ${JVM_OPTS} -cp "${CLASSPATH}" com.optel.DeviceLauncher -c "${CONFIG_FILE}" "$@"
