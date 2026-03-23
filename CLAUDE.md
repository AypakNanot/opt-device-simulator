# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Workflow

**IMPORTANT**: Do NOT commit changes automatically. Always ask for explicit permission before:
- Creating commits
- Pushing to remote
- Any destructive git operations (reset --hard, push --force, checkout -f)

Only commit when the user explicitly requests it.

## Build & Run Commands

```bash
# Build entire project
mvn clean install

# Run specific device simulator
java -jar examples/devices/device-otn-diligent-2/target/device.jar --port 17830 --devices-count 1 --thread-pool-size 10

# Run with fast random generation (avoids slow startup)
java -Djava.security.egd=file:/dev/./urandom -jar device.jar

# Remote debug
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 -jar device.jar
```

### Device Port Assignments
| Device | Port |
|--------|------|
| device-otn-diligent-2 | 17830 |
| device-otn-yearning-1 | 17831 |
| device-wdm-diligent-201 | 17832 |
| device-wdm-diligent-10201 | 17833 |
| device-wdm-yearning-10202 | 17834 |

### Connect via NETCONF over SSH
```bash
ssh -oStrictHostKeyChecking=no -oUserKnownHostsFile=/dev/null admin@127.0.0.1 -p 17830 -s netconf
# Credentials: admin / admin
```

## Project Architecture

### Module Structure
```
opt-device-simulator/
├── lighty-netconf-device/       # Core NETCONF device library
│   └── src/main/java/io/lighty/netconf/device/
│       ├── NetconfDevice.java            # Device interface
│       ├── NetconfDeviceBuilder.java     # Fluent builder
│       ├── requests/                     # NETCONF RPC processors (GetConfig, EditConfig, etc.)
│       └── requests/notification/        # Subscription & notification handling
│
├── aggregator/                  # Example devices & models
│   ├── devices/                 # Device implementations (OTN/WDM)
│   │   └── device-*/src/main/java/com/optel/
│   │       ├── Main.java               # Entry point, RPC auto-discovery
│   │       ├── processors/             # Custom RPC handlers
│   │       └── rpcs/                   # RPC service implementations
│   │
│   └── models/                  # YANG model definitions
│       └── */src/main/yang/            # .yang files (ACC, OpenConfig, IETF)
│
└── aggregator/parents/
    ├── examples-parent/         # Maven parent POM (lighty-app-parent 22.1.0)
    └── examples-bom/            # Dependency management
```

### Key Technologies
- **Framework**: lighty.io NETCONF Device 22.2.0-SNAPSHOT
- **Base**: lighty-parent 22.1.0 / lighty-app-parent 22.1.0
- **Java**: 21+ (maven.compiler.release=21)
- **NETCONF**: Over SSH using OpenDaylight netconf-testtool
- **YANG**: Binding-codec generated models from .yang definitions

### Core Patterns

**Device Initialization** (see `com.optel.Main`):
1. Parse CLI args (port, device-count, thread-pool-size)
2. Load YANG models via `FileUtil.getModels()` - scans JAR for .yang files
3. Auto-discover RPC processors via Reflections library (`com.optel.processors` package)
4. Build NetconfDevice with builder pattern:
   - `.setCredentials()` - SSH auth
   - `.withModels()` - YANG schemas
   - `.withDefaultRequestProcessors()` - standard NETCONF ops
   - `.withDefaultNotificationProcessor()` - event subscriptions
5. Start device and register shutdown hook

**RPC Processor Pattern**:
```java
public class YourRpcProcessor extends BaseRequestProcessor {
    @Override
    public QName getIdentifier() {
        return QName.create("urn:your:namespace", "rpc-name");
    }

    @Override
    public NormalizedNode handleRpc(NormalizedNode input) {
        // Process and return response
    }
}
```

Processors are auto-discovered via Reflections by `Main.getProcessors()`.

**YANG Model Loading**:
- Models stored in `src/main/yang/` as `.yang` files
- Packaged into JAR via `<resource><directory>src/main/yang</directory></resource>`
- `FileUtil.getModels()` extracts .yang from JAR, parses namespace/name/revision
- `YangModuleUtils.getModelsFromClasspath()` loads binding classes

### Data Flow
1. SSH client connects to NETCONF port
2. lighty.io framework routes NETCONF RPCs to registered RequestProcessors
3. Custom processors (GetPmData, CreateEthConnection, etc.) handle device-specific logic
4. Responses serialized to XML and returned over NETCONF
5. Notifications published via `NotificationPublishService`

### Device Types
- **OTN** (Optical Transport Network): acc-otn, acc-eth, acc-connection models
- **WDM** (Wavelength Division Multiplexing): openconfig-platform, openconfig-alarms models
- Each device simulates multiple virtual devices via `--devices-count`
