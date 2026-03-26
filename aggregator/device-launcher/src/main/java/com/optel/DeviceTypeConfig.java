package com.optel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a specific device type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceTypeConfig {

    @JsonProperty("type")
    private String type;

    @JsonProperty("basePort")
    private int basePort;

    @JsonProperty("instances")
    private List<DeviceInstanceConfig> instances = new ArrayList<>();

    @JsonProperty("config")
    private InstanceConfig config;

    public DeviceTypeConfig() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getBasePort() {
        return basePort;
    }

    public void setBasePort(int basePort) {
        this.basePort = basePort;
    }

    public List<DeviceInstanceConfig> getInstances() {
        return instances;
    }

    public void setInstances(List<DeviceInstanceConfig> instances) {
        this.instances = instances;
    }

    public InstanceConfig getConfig() {
        return config;
    }

    public void setConfig(InstanceConfig config) {
        this.config = config;
    }

    /**
     * Default configuration for all instances of this device type.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstanceConfig {
        @JsonProperty("deviceCount")
        private int deviceCount = 1;

        @JsonProperty("threadPoolSize")
        private int threadPoolSize = 10;

        public InstanceConfig() {
        }

        public int getDeviceCount() {
            return deviceCount;
        }

        public void setDeviceCount(int deviceCount) {
            this.deviceCount = deviceCount;
        }

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }
    }
}
