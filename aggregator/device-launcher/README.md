# Device Launcher

Multi-device instance manager for NETCONF device simulators.

## Overview

The Device Launcher module provides a unified way to start and manage multiple instances of different device types (OTN/WDM). Each device instance runs as a separate JVM process, ensuring complete isolation.

## Features

- **Multi-device support**: Manage all 5 device types from a single launcher
- **Multi-instance**: Run multiple instances of each device type
- **Flexible configuration**: JSON configuration file + command-line arguments
- **Process isolation**: Each instance runs in a separate JVM process
- **Graceful shutdown**: Clean shutdown of all managed processes

## Device Types

| Device Type | Description | Default Base Port |
|-------------|-------------|-------------------|
| otn-diligent-2 | OTN Diligent 2 | 15000 |
| otn-yearning-1 | OTN Yearning 1 | 20000 |
| wdm-diligent-201 | WDM Diligent 201 | 25000 |
| wdm-diligent-10201 | WDM Diligent 10201 | 30000 |
| wdm-yearning-10202 | WDM Yearning 10202 | 35000 |

**Note:** Each device type has a base port interval of 5000, providing ample space for multiple instances.

## Requirements

- **Java 21 or later** (required for running the device launcher)
- Set `JAVA_HOME` environment variable to Java 21 installation directory

## Building

```bash
# Build entire project
mvn clean install

# Build device-launcher only
mvn clean install -pl aggregator/device-launcher -am
```

## Distribution Package

After building, the distribution package is available at:
```
aggregator/device-launcher/target/device-launcher-device-launcher.zip
```

Extract the package to get:
- `device-launcher.jar` - Main launcher
- `devices/` - Device distributions with all dependencies
- `lib/` - Launcher dependencies
- `config.json` - Example configuration
- `start.sh` / `start.bat` - Startup scripts
- `stop.sh` / `stop.bat` - Stop scripts

## Quick Start

### Linux/macOS

```bash
# Extract the distribution
unzip device-launcher-device-launcher.zip
cd device-launcher

# Start with default config
./start.sh

# Start with custom config
./start.sh -c custom-config.json

# Stop all devices
./stop.sh
```

### Windows

```bat
REM Extract the distribution
unzip device-launcher-device-launcher.zip
cd device-launcher

REM Start with default config
start.bat

REM Start with custom config
start.bat -c custom-config.json

REM Stop all devices
stop.bat
```

## Usage

### Using Configuration File

Create a `config.json` file:

```json
{
  "global": {
    "defaultThreadPoolSize": 10,
    "defaultDeviceCount": 1
  },
  "devices": [
    {
      "type": "otn-diligent-2",
      "instances": [
        {
          "basePort": 15000,
          "count": 100,
          "threadPoolSize": 10
        }
      ]
    },
    {
      "type": "otn-yearning-1",
      "instances": [
        {
          "basePort": 20000,
          "count": 50,
          "threadPoolSize": 10
        }
      ]
    }
  ]
}
```

Run:
```bash
java -jar device-launcher.jar -c config.json
```

### Command-Line Arguments

Add device instances via command line:

```bash
# Single device instance
java -jar device-launcher.jar --device otn-diligent-2:15000:100:10

# Multiple device instances
java -jar device-launcher.jar \
  --device otn-diligent-2:15000:100:10 \
  --device wdm-diligent-201:25000:50:10

# Combine config file with command-line additions
java -jar device-launcher.jar -c config.json \
  --device otn-diligent-2:40000:200:20
```

### Device Argument Format

```
--device <type>:<basePort>[:<count>[:<threadPoolSize>]]
```

- `type`: Device type (otn-diligent-2, otn-yearning-1, etc.)
- `basePort`: Starting port number
- `count`: Number of devices (default: 1)
- `threadPoolSize`: Thread pool size (default: 10)

### Port Range Calculation

For each instance:
- Start Port: `basePort`
- End Port: `basePort + count - 1`

Example: `basePort=15000, count=100` → Ports 15000-15099

## Configuration Options

### Global Settings

| Property | Description | Default |
|----------|-------------|---------|
| defaultThreadPoolSize | Default thread pool size | 10 |
| defaultDeviceCount | Default number of devices per instance | 1 |

### Device Type Settings

| Property | Description | Required |
|----------|-------------|----------|
| type | Device type identifier | Yes |
| basePort | Base port for this type | Yes |
| instances | List of instance configurations | Yes |
| config.deviceCount | Default device count | No |
| config.threadPoolSize | Default thread pool size | No |

### Instance Settings

| Property | Description | Default |
|----------|-------------|---------|
| basePort | Starting port | Required |
| count | Number of devices | 1 |
| threadPoolSize | Thread pool size | Inherited from config |

## Command Line Reference

```
Usage: java -jar device-launcher.jar [options]

Options:
  -c, --config <file>     Configuration file (default: config.json)
  --device <spec>         Add device instance
  -h, --help              Show help

Examples:
  java -jar device-launcher.jar -c config.json
  java -jar device-launcher.jar --device otn-diligent-2:15000:100:10
```

## Process Management

### Stopping Devices

Press `Ctrl+C` to stop all device instances gracefully.

The launcher will:
1. Send SIGTERM to all managed processes
2. Wait for processes to terminate
3. Exit when all processes have stopped

### Monitoring

The launcher logs all process start/stop events. Each device instance outputs its own logs to the console.

## Troubleshooting

### Port Already in Use

If you see "Address already in use" errors:
1. Check if another process is using the port
2. Use a different basePort in configuration
3. Wait for existing processes to fully terminate

### Device JAR Not Found

Ensure the `devices/` directory contains all required device JAR files:
- device-otn-diligent-2.jar
- device-otn-yearning-1.jar
- device-wdm-diligent-201.jar
- device-wdm-diligent-10201.jar
- device-wdm-yearning-10202.jar

### Memory Issues

Each JVM process consumes memory. For many instances:
- Reduce `-Xmx` heap size per device
- Limit number of concurrent instances
- Monitor system memory usage

## Example Scenarios

### Scenario 1: Single Instance Per Type

```json
{
  "devices": [
    {"type": "otn-diligent-2", "basePort": 15000, "instances": [{"basePort": 15000, "count": 1}]},
    {"type": "otn-yearning-1", "basePort": 20000, "instances": [{"basePort": 20000, "count": 1}]},
    {"type": "wdm-diligent-201", "basePort": 25000, "instances": [{"basePort": 25000, "count": 1}]}
  ]
}
```

### Scenario 2: Multiple Instances Per Type

```json
{
  "devices": [
    {
      "type": "otn-diligent-2",
      "basePort": 15000,
      "instances": [
        {"basePort": 15000, "count": 100, "threadPoolSize": 10},
        {"basePort": 16000, "count": 200, "threadPoolSize": 20}
      ]
    },
    {
      "type": "otn-yearning-1",
      "basePort": 20000,
      "instances": [
        {"basePort": 20000, "count": 50, "threadPoolSize": 10},
        {"basePort": 21000, "count": 100, "threadPoolSize": 15}
      ]
    }
  ]
}
```

### Scenario 3: Mixed Configuration

```bash
# Start with config file and add extra instances
java -jar device-launcher.jar -c config.json \
  --device otn-diligent-2:40000:100:10 \
  --device wdm-diligent-201:45000:50:10
```

## Default Port Planning

The default configuration uses a 5000-port interval between device types:

| Device Type | Instance | Base Port | Count | Port Range |
|-------------|----------|-----------|-------|------------|
| otn-diligent-2 | 1 | 15000 | 100 | 15000-15099 |
| otn-yearning-1 | 1 | 20000 | 50 | 20000-20049 |
| wdm-diligent-201 | 1 | 25000 | 100 | 25000-25099 |
| wdm-diligent-10201 | 1 | 30000 | 50 | 30000-30049 |
| wdm-yearning-10202 | 1 | 35000 | 100 | 35000-35099 |

This port planning provides:
- **Clear separation**: 5000 ports between each device type base
- **Room for expansion**: Each type can have multiple instances
- **Easy to remember**: Ports start at 15000 and increment by 5000

## License

Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.

This program and the accompanying materials are made available under the
terms of the Eclipse Public License v1.0 which accompanies this distribution,
and is available at https://www.eclipse.org/legal/epl-v10.html
