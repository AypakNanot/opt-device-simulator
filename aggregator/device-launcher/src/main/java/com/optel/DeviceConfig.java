package com.optel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root configuration class for device launcher.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceConfig {

    @JsonProperty("global")
    private GlobalConfig global;

    @JsonProperty("devices")
    private List<DeviceTypeConfig> devices;

    public DeviceConfig() {
    }

    public GlobalConfig getGlobal() {
        return global;
    }

    public void setGlobal(GlobalConfig global) {
        this.global = global;
    }

    public List<DeviceTypeConfig> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceTypeConfig> devices) {
        this.devices = devices;
    }

    /**
     * Global configuration settings.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GlobalConfig {
        @JsonProperty("defaultThreadPoolSize")
        private int defaultThreadPoolSize = 10;

        @JsonProperty("defaultDeviceCount")
        private int defaultDeviceCount = 1;

        public GlobalConfig() {
        }

        public int getDefaultThreadPoolSize() {
            return defaultThreadPoolSize;
        }

        public void setDefaultThreadPoolSize(int defaultThreadPoolSize) {
            this.defaultThreadPoolSize = defaultThreadPoolSize;
        }

        public int getDefaultDeviceCount() {
            return defaultDeviceCount;
        }

        public void setDefaultDeviceCount(int defaultDeviceCount) {
            this.defaultDeviceCount = defaultDeviceCount;
        }
    }
}
