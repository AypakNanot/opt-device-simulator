package com.optel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for a single device instance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceInstanceConfig {

    @JsonProperty("basePort")
    private Integer basePort;

    @JsonProperty("count")
    private int count = 1;

    @JsonProperty("threadPoolSize")
    private Integer threadPoolSize;

    // Transient field for command-line parsing (not persisted to JSON)
    private transient String type;

    public DeviceInstanceConfig() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getBasePort() {
        return basePort;
    }

    public void setBasePort(Integer basePort) {
        this.basePort = basePort;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Integer getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(Integer threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Calculate the end port for this instance.
     * @return the last port number in the range
     */
    public int getEndPort() {
        return basePort + count - 1;
    }

    /**
     * Get the port range as a string.
     * @return port range in format "start-end"
     */
    public String getPortRange() {
        return basePort + "-" + getEndPort();
    }
}
