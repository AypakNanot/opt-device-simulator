# OPT Device Simulator

光网络设备模拟器项目，基于 lighty.io NETCONF Device 框架开发，用于模拟光传输网络设备（OTN/WDM），支持 NETCONF 协议通信。

## 项目简介

本项目提供了一套完整的 NETCONF 设备模拟器解决方案，主要用于：
- **网络设备测试**：模拟真实光传输设备，用于网管系统测试
- **协议验证**：验证 NETCONF 协议实现和 YANG 数据模型
- **开发环境**：为上层管理系统提供稳定的测试设备环境

### 技术特性

- 基于 **lighty.io NETCONF Device** 框架（版本 22.2.0-SNAPSHOT）
- 支持 **NETCONF over SSH** 通信
- 完整的 **YANG 模型** 支持（OTN、WDM、Ethernet 等）
- 可配置的设备数量和多线程处理
- 支持 RPC 远程调用、告警通知、性能监控等功能

---

## 项目结构

```
opt-device-simulator/
├── lighty-netconf-device/          # NETCONF 设备核心库
│   ├── src/main/java/
│   │   └── io/lighty/netconf/device/
│   │       ├── NetconfDevice.java          # 设备主类
│   │       ├── NetconfDeviceBuilder.java   # 设备构建器
│   │       ├── requests/                   # 请求处理器
│   │       ├── processors/                 # 内部处理逻辑
│   │       └── utils/                      # 工具类
│   └── pom.xml
│
├── examples/                        # 示例设备集合
│   ├── devices/                     # 设备实现
│   │   ├── device-otn-diligent-2/   # OTN 设备（Diligent 2）
│   │   ├── device-otn-yearning-1/   # OTN 设备（Yearning 1）
│   │   ├── device-wdm-diligent-201/ # WDM 设备（Diligent 201）
│   │   ├── device-wdm-diligent-10201/
│   │   └── device-wdm-yearning-10202/
│   │
│   ├── models/                      # YANG 模型定义
│   │   ├── otn-diligent-2/
│   │   ├── otn-yearning-1/
│   │   └── wdm-*/
│   │
│   └── parents/                     # Maven 父 POM
│       ├── examples-parent/
│       └── examples-bom/
│
└── pom.xml                          # 项目根 POM
```

---

## 环境要求

| 依赖项 | 版本要求 |
|--------|----------|
| Java JDK | 21+ |
| Maven | 3.8+ |
| SSH 客户端 | 支持 NETCONF over SSH |

---

## 快速开始

### 1. 构建项目

```bash
# 克隆项目
git clone git@github.com:AypakNanot/opt-device-simulator.git
cd opt-device-simulator

# 编译打包
mvn clean install
```

### 2. 运行设备模拟器

构建完成后，可执行 JAR 文件位于各设备模块的 `target/` 目录下。

#### 基本启动命令

```bash
java -jar examples/devices/device-otn-diligent-2/target/device.jar \
    --port 17830 \
    --devices-count 1 \
    --thread-pool-size 10
```

#### 参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--port` | NETCONF 监听端口 | 17830 |
| `--devices-count` | 模拟设备数量 | 1 |
| `--thread-pool-size` | 线程池大小 | 10 |

#### 各设备端口分配

| 设备名称 | 描述 | 建议端口 |
|----------|------|----------|
| `device-otn-diligent-2` | OTN 设备 | 17830 |
| `device-otn-yearning-1` | OTN 设备 | 17831 |
| `device-wdm-diligent-201` | WDM 设备 | 17832 |
| `device-wdm-diligent-10201` | WDM 设备 | 17833 |
| `device-wdm-yearning-10202` | WDM 设备 | 17834 |

---

## 使用指南

### 连接设备

使用 SSH 连接到 NETCONF 设备：

```bash
ssh -oStrictHostKeyChecking=no -oUserKnownHostsFile=/dev/null \
    admin@127.0.0.1 -p 17830 -s netconf
```

**默认凭据**：
- 用户名：`admin`
- 密码：`admin`

### NETCONF 会话示例

#### 1. 建立连接（Hello 消息）

```xml
<hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
    <capabilities>
        <capability>urn:ietf:params:netconf:base:1.0</capability>
    </capabilities>
</hello>
]]>]]>
```

#### 2. 获取配置（get-config）

```xml
<rpc message-id="101" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
    <get-config>
        <source>
            <running/>
        </source>
    </get-config>
</rpc>
]]>]]>
```

#### 3. 获取状态（get）

```xml
<rpc message-id="102" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
    <get/>
</rpc>
]]>]]>
```

#### 4. 编辑配置（edit-config）

```xml
<rpc message-id="103" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
    <edit-config>
        <target>
            <running/>
        </target>
        <config>
            <!-- 你的配置数据 -->
        </config>
    </edit-config>
</rpc>
]]>]]>
```

---

## 自定义开发

### 创建新的设备模拟器

#### 1. 创建模型模块

在 `examples/models/` 下创建新的 YANG 模型模块：

```xml
<!-- pom.xml -->
<project>
    <groupId>com.optel</groupId>
    <artifactId>your-model-name</artifactId>
    <packaging>jar</packaging>

    <build>
        <resources>
            <resource>
                <directory>src/main/yang</directory>
            </resource>
        </resources>
    </build>
</project>
```

#### 2. 创建设备模块

在 `examples/devices/` 下创建设备实现：

```xml
<!-- pom.xml -->
<project>
    <parent>
        <groupId>io.lighty.netconf.device.examples</groupId>
        <artifactId>examples-parent</artifactId>
        <version>22.2.0-SNAPSHOT</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>com.optel</groupId>
            <artifactId>your-model-name</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.lighty.netconf.device</groupId>
            <artifactId>lighty-netconf-device</artifactId>
        </dependency>
    </dependencies>
</project>
```

#### 3. 实现主类

参考 `com.optel.Main` 实现设备启动逻辑：

```java
public class Main {
    public static void main(String[] args) {
        // 1. 解析命令行参数
        ArgumentParser argumentParser = new ArgumentParser();
        Namespace parseArguments = argumentParser.parseArguments(args);

        // 2. 加载 YANG 模型
        Set<YangModuleInfo> models = FileUtil.getModels(this.getClass(), JAR_NAME);

        // 3. 初始化 RPC 处理器
        Map<QName, RequestProcessor> processors = getProcessors();

        // 4. 构建并启动 NETCONF 设备
        NetconfDevice netconfDevice = new NetconfDeviceBuilder()
                .setCredentials("admin", "admin")
                .setBindingPort(port)
                .withModels(models)
                .withDefaultRequestProcessors()
                .withRequestProcessors(processors)
                .build();

        netconfDevice.start();
    }
}
```

#### 4. 实现 RPC 处理器

创建自定义 RPC 处理器：

```java
public class YourRpcProcessor extends BaseRequestProcessor {

    @Override
    public QName getIdentifier() {
        return QName.create("urn:your:namespace", "your-rpc-name");
    }

    @Override
    public NormalizedNode handleRpc(NormalizedNode input) {
        // 处理 RPC 请求
        return buildResponse(input);
    }
}
```

---

## YANG 模型

### OTN 设备模型

| 模块 | 描述 |
|------|------|
| `acc-connection` | 连接管理 |
| `acc-devm` | 设备管理 |
| `acc-eth` | 以太网接口 |
| `acc-otn` | OTN 层管理 |
| `acc-alarms` | 告警管理 |
| `acc-performance` | 性能监控 |

### WDM 设备模型

| 模块 | 描述 |
|------|------|
| `openconfig-interfaces` | 接口管理 |
| `openconfig-platform` | 硬件平台 |
| `openconfig-alarms` | 告警管理 |
| `openconfig-performance` | 性能监控 |
| `openconfig-optical-amplifier` | 光放大器 |

---

## 常见问题

### 1. 启动缓慢

**问题**：启动模拟器时响应慢
**原因**：Java 随机数生成器使用 `/dev/random`
**解决方法**：

```bash
java -Djava.security.egd=file:/dev/./urandom -jar device.jar
```

### 2. 端口被占用

**问题**：`Address already in use`
**解决**：更换端口或关闭占用端口的进程

```bash
# Windows
netstat -ano | findstr :17830
taskkill /F /PID <PID>

# Linux
lsof -i :17830
kill -9 <PID>
```

### 3. SSH 连接失败

**检查**：
- 防火墙是否开放端口
- 设备是否成功启动（查看日志）
- SSH 客户端版本兼容性

---

## 开发与调试

### 日志配置

修改 `logback.xml` 调整日志级别：

```xml
<configuration>
    <root level="DEBUG">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### 调试连接

使用 `-Xdebug` 参数启动 JVM 进行远程调试：

```bash
java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005 \
    -jar device.jar
```

---

## 许可证

本项目遵循 **Eclipse Public License v1.0**。

- 主项目版权：PANTHEON.tech s.r.o.
- 示例代码版权：OPTEL

---

## 联系方式

- GitHub: [AypakNanot/opt-device-simulator](https://github.com/AypakNanot/opt-device-simulator)
- 问题反馈：请在 GitHub 提交 Issue

---

## 更新日志

### v22.2.0-SNAPSHOT
- 升级至 lighty.io 22.x 版本
- 支持 Java 21
- 新增多个 OTN/WDM 设备模拟器
- 优化 RPC 处理性能
- 改进告警通知机制
