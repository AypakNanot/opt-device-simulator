package com.optel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optel.DeviceConfig.GlobalConfig;
import com.optel.DeviceTypeConfig.InstanceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main device launcher class that manages multiple device instances.
 * Supports JSON configuration file and command-line arguments.
 */
public class DeviceLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceLauncher.class);

    private static final String DEFAULT_CONFIG_FILE = "config.json";

    // Device type to JAR file name mapping
    private static final Map<String, String> DEVICE_JAR_MAPPING = Map.of(
        "otn-diligent-2", "device-otn-diligent-2",
        "otn-yearning-1", "device-otn-yearning-1",
        "wdm-diligent-201", "device-wdm-diligent-201",
        "wdm-diligent-10201", "device-wdm-diligent-10201",
        "wdm-yearning-10202", "device-wdm-yearning-10202"
    );

    // Device type to model package mapping (for YANG model JARs)
    private static final Map<String, String> DEVICE_MODEL_MAPPING = Map.of(
        "otn-diligent-2", "otn-diligent-2",
        "otn-yearning-1", "otn-yearning-1",
        "wdm-diligent-201", "wdm-diligent-201",
        "wdm-diligent-10201", "wdm-diligent-10201",
        "wdm-yearning-10202", "wdm-yearning-10202"
    );

    private final List<Process> processes = new CopyOnWriteArrayList<>();
    private final Map<String, List<Process>> processesByType = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public static void main(String[] args) {
        DeviceLauncher launcher = new DeviceLauncher();
        launcher.run(args);
    }

    /**
     * Main entry point for running the launcher.
     */
    public void run(String[] args) {
        try {
            // Parse command line arguments
            CommandLineArgs cmdArgs = parseCommandLine(args);

            // Load configuration
            DeviceConfig config = loadConfig(cmdArgs.configFile);

            // Merge with command line arguments
            mergeCommandLineArgs(config, cmdArgs);

            // Start all devices
            start(config);

            // Register shutdown hook
            registerShutdownHook();

            // Wait for interrupt
            waitForShutdown();

        } catch (Exception e) {
            LOG.error("Failed to start device launcher", e);
            System.exit(1);
        }
    }

    /**
     * Parse command line arguments.
     */
    private CommandLineArgs parseCommandLine(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();
        cmdArgs.configFile = DEFAULT_CONFIG_FILE;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-c", "--config" -> {
                    if (i + 1 < args.length) {
                        cmdArgs.configFile = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing config file path after -c/--config");
                    }
                }
                case "--device" -> {
                    if (i + 1 < args.length) {
                        String deviceArg = args[++i];
                        parseDeviceArgument(cmdArgs, deviceArg);
                    } else {
                        throw new IllegalArgumentException("Missing device specification after --device");
                    }
                }
                case "-h", "--help" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> {
                    LOG.warn("Unknown argument: {}", args[i]);
                }
            }
        }

        return cmdArgs;
    }

    /**
     * Parse device argument in format: type:basePort:count:threadPoolSize
     */
    private void parseDeviceArgument(CommandLineArgs cmdArgs, String deviceArg) {
        String[] parts = deviceArg.split(":");
        if (parts.length < 2 || parts.length > 4) {
            throw new IllegalArgumentException(
                "Invalid device format: " + deviceArg +
                ". Expected format: type:basePort[:count:threadPoolSize]"
            );
        }

        DeviceInstanceConfig instance = new DeviceInstanceConfig();
        instance.setType(parts[0]);
        instance.setBasePort(Integer.parseInt(parts[1]));
        instance.setCount(parts.length > 2 ? Integer.parseInt(parts[2]) : 1);
        if (parts.length > 3) {
            instance.setThreadPoolSize(Integer.parseInt(parts[3]));
        }

        cmdArgs.additionalDevices.add(instance);
    }

    /**
     * Print help message.
     */
    private void printHelp() {
        System.out.println("Device Launcher - Multi-device instance manager");
        System.out.println();
        System.out.println("Usage: java -jar device-launcher.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c, --config <file>     Configuration file (default: config.json)");
        System.out.println("  --device <spec>         Add device instance (format: type:basePort[:count:threadPoolSize])");
        System.out.println("  -h, --help              Show this help message");
        System.out.println();
        System.out.println("Device Types:");
        System.out.println("  otn-diligent-2, otn-yearning-1, wdm-diligent-201,");
        System.out.println("  wdm-diligent-10201, wdm-yearning-10202");
        System.out.println();
        System.out.println("Default Base Ports (5000 interval):");
        System.out.println("  otn-diligent-2:     15000");
        System.out.println("  otn-yearning-1:     20000");
        System.out.println("  wdm-diligent-201:   25000");
        System.out.println("  wdm-diligent-10201: 30000");
        System.out.println("  wdm-yearning-10202: 35000");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar device-launcher.jar -c config.json");
        System.out.println("  java -jar device-launcher.jar --device otn-diligent-2:15000:100:10");
        System.out.println("  java -jar device-launcher.jar -c config.json --device wdm-diligent-201:25000:50:10");
    }

    /**
     * Merge command line arguments with configuration.
     */
    private void mergeCommandLineArgs(DeviceConfig config, CommandLineArgs cmdArgs) {
        if (cmdArgs.additionalDevices.isEmpty()) {
            return;
        }

        // Group additional devices by type
        Map<String, List<DeviceInstanceConfig>> byType = new ConcurrentHashMap<>();
        for (DeviceInstanceConfig instance : cmdArgs.additionalDevices) {
            byType.computeIfAbsent(instance.getType(), k -> new ArrayList<>()).add(instance);
        }

        // Add to existing config or create new entries
        List<DeviceTypeConfig> existingTypes = config.getDevices();
        if (existingTypes == null) {
            existingTypes = new ArrayList<>();
            config.setDevices(existingTypes);
        }

        for (Map.Entry<String, List<DeviceInstanceConfig>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<DeviceInstanceConfig> instances = entry.getValue();

            // Find existing device type config
            DeviceTypeConfig existingConfig = existingTypes.stream()
                .filter(tc -> tc.getType().equals(type))
                .findFirst()
                .orElse(null);

            if (existingConfig != null) {
                existingConfig.getInstances().addAll(instances);
            } else {
                // Create new device type config
                DeviceTypeConfig newConfig = new DeviceTypeConfig();
                newConfig.setType(type);
                newConfig.setBasePort(instances.get(0).getBasePort());
                newConfig.getInstances().addAll(instances);
                existingTypes.add(newConfig);
            }
        }
    }

    /**
     * Load configuration from JSON file.
     */
    private DeviceConfig loadConfig(String configFile) throws IOException {
        Path configPath = Paths.get(configFile);

        if (!Files.exists(configPath)) {
            LOG.warn("Configuration file not found: {}. Using empty configuration.", configFile);
            DeviceConfig config = new DeviceConfig();
            config.setGlobal(new GlobalConfig());
            config.setDevices(new ArrayList<>());
            return config;
        }

        LOG.info("Loading configuration from: {}", configFile);
        ObjectMapper mapper = new ObjectMapper();
        DeviceConfig config = mapper.readValue(configPath.toFile(), DeviceConfig.class);

        if (config.getGlobal() == null) {
            config.setGlobal(new GlobalConfig());
        }

        if (config.getDevices() == null) {
            config.setDevices(new ArrayList<>());
        }

        return config;
    }

    /**
     * Start all configured device instances.
     */
    public void start(DeviceConfig config) {
        GlobalConfig global = config.getGlobal();
        LOG.info("Starting device launcher with {} device types", config.getDevices().size());

        for (DeviceTypeConfig typeConfig : config.getDevices()) {
            String type = typeConfig.getType();

            // Validate device type
            if (!DEVICE_JAR_MAPPING.containsKey(type)) {
                LOG.warn("Unknown device type: {}. Skipping...", type);
                continue;
            }

            InstanceConfig defaultConfig = typeConfig.getConfig();
            if (defaultConfig == null) {
                defaultConfig = new InstanceConfig();
                defaultConfig.setDeviceCount(global.getDefaultDeviceCount());
                defaultConfig.setThreadPoolSize(global.getDefaultThreadPoolSize());
            }

            LOG.info("Starting {} instances for device type: {}", typeConfig.getInstances().size(), type);

            for (DeviceInstanceConfig instance : typeConfig.getInstances()) {
                // Apply defaults if not specified
                if (instance.getCount() <= 0) {
                    instance.setCount(defaultConfig.getDeviceCount());
                }
                if (instance.getThreadPoolSize() == null) {
                    instance.setThreadPoolSize(defaultConfig.getThreadPoolSize());
                }

                // Start device process
                startDeviceProcess(type, instance);
            }
        }

        LOG.info("Device launcher started. Total processes: {}", processes.size());
    }

    /**
     * Start a single device instance as a separate JVM process.
     * Uses lib directory for common dependencies and device-specific JAR.
     */
    private void startDeviceProcess(String type, DeviceInstanceConfig instance) {
        String deviceDirName = DEVICE_JAR_MAPPING.get(type);
        String deviceJarPath = findDeviceJar(deviceDirName);

        if (deviceJarPath == null) {
            LOG.error("Device JAR not found for type: {}. Skipping instance.", type);
            return;
        }

        // Build classpath: lib/* + specific model JAR + device.jar
        String classpath = buildDeviceClasspath(deviceJarPath, type);

        // Get model package name for YANG models
        String modelPackage = DEVICE_MODEL_MAPPING.get(type);
        if (modelPackage == null) {
            modelPackage = deviceDirName;
        }

        // Build command: java -cp <classpath> com.optel.Main -p <basePort> -d <count> -t <threadPoolSize>
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(classpath);
        command.add("com.optel.Main");
        command.add("-p");
        command.add(String.valueOf(instance.getBasePort()));
        command.add("-d");
        command.add(String.valueOf(instance.getCount()));
        command.add("-t");
        command.add(String.valueOf(instance.getThreadPoolSize()));

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.inheritIO();

            Process process = processBuilder.start();

            processes.add(process);
            processesByType.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(process);

            LOG.info("Started {} instance: basePort={}, count={}, threadPoolSize={}, ports={}-{}",
                type,
                instance.getBasePort(),
                instance.getCount(),
                instance.getThreadPoolSize(),
                instance.getBasePort(),
                instance.getEndPort());

        } catch (IOException e) {
            LOG.error("Failed to start device process: {} on port {}", type, instance.getBasePort(), e);
        }
    }

    /**
     * Build classpath for device process using lib directory and device JAR.
     * Uses lib/* wildcard for all JARs including device model JARs.
     * YANG model loading is handled by FileUtil.getModels() which selects
     * the appropriate models based on the device type.
     */
    private String buildDeviceClasspath(String deviceJarPath, String deviceType) {
        StringBuilder classpath = new StringBuilder();

        // Add lib directory wildcard (all JARs including device model JARs)
        classpath.append("lib").append(File.separator).append("*");

        // Add device JAR
        classpath.append(File.pathSeparatorChar);
        classpath.append(deviceJarPath);

        return classpath.toString();
    }

    /**
     * Find the device JAR file in the devices directory.
     * Device JARs are extracted to devices/{type}/device/device.jar
     */
    private String findDeviceJar(String deviceDirName) {
        // Try relative to current directory (devices subfolder)
        Path devicesDir = Paths.get("devices");
        if (Files.exists(devicesDir)) {
            // Look for device folder with nested device/device.jar structure
            Path deviceDir = devicesDir.resolve(deviceDirName);
            if (Files.exists(deviceDir)) {
                Path nestedDeviceDir = deviceDir.resolve("device");
                if (Files.exists(nestedDeviceDir)) {
                    Path deviceJar = nestedDeviceDir.resolve("device.jar");
                    if (Files.exists(deviceJar)) {
                        return deviceJar.toAbsolutePath().toString();
                    }
                }
            }
        }

        // Try current directory
        Path currentJar = Paths.get(deviceDirName + ".jar");
        if (Files.exists(currentJar)) {
            return currentJar.toAbsolutePath().toString();
        }

        return null;
    }

    /**
     * Register shutdown hook for graceful shutdown.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down device launcher...");
            running = false;
            stopAll();
        }));
    }

    /**
     * Stop all running device instances.
     */
    public void stopAll() {
        LOG.info("Stopping all device instances...");

        for (Process process : processes) {
            if (process.isAlive()) {
                process.destroy();
            }
        }

        // Wait for processes to terminate
        for (Process process : processes) {
            try {
                if (process.isAlive()) {
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for process to terminate");
            }
        }

        LOG.info("All device instances stopped");
    }

    /**
     * Stop all instances of a specific device type.
     */
    public void stopByType(String type) {
        List<Process> typeProcesses = processesByType.get(type);
        if (typeProcesses == null || typeProcesses.isEmpty()) {
            LOG.warn("No running instances for device type: {}", type);
            return;
        }

        LOG.info("Stopping {} instances of device type: {}", typeProcesses.size(), type);

        for (Process process : typeProcesses) {
            if (process.isAlive()) {
                process.destroy();
            }
        }

        // Remove from tracking
        processes.removeAll(typeProcesses);
        processesByType.remove(type);
    }

    /**
     * Wait for shutdown signal.
     */
    private void waitForShutdown() {
        synchronized (this) {
            while (running) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Launcher interrupted");
                    break;
                }
            }
        }
    }

    /**
     * Command line arguments holder.
     */
    private static class CommandLineArgs {
        String configFile = DEFAULT_CONFIG_FILE;
        List<DeviceInstanceConfig> additionalDevices = new ArrayList<>();
    }
}
