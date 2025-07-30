package io.lighty.netconf.device.utils;

import java.util.Arrays;

public class FileUtil {
    public static boolean isPackage() {
        String property = System.getProperty("user.dir");
        return !Arrays.asList(property.split("[/\\\\]")).contains("netconf-simulator");
    }
}
