package io.lighty.netconf.device.utils;


import cn.hutool.core.text.CharSequenceUtil;
import io.lighty.core.common.models.ModuleId;
import io.lighty.core.common.models.YangModuleUtils;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);
    public static String DIR = "xml";
    public static String SUFFIX = ".xml";

    // Pre-compiled regex patterns for YANG parsing
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("namespace\\s+\"([^\"]+)\"");
    private static final Pattern MODULE_NAME_PATTERN = Pattern.compile("module\\s+\"?([a-zA-Z0-9\\-]+)\"?\\s*\\{");
    private static final Pattern REVISION_PATTERN = Pattern.compile("revision\\s+\"([^\"]+)\"");

    public static boolean isPackage() {
        String property = System.getProperty("user.dir");
        return !Arrays.asList(property.split("[/\\\\]")).contains("opt-device-simulator");
    }

    public static InputStream getFile(String filename) throws IOException {
        if (CharSequenceUtil.isBlank(filename)) {
            return null;
        }
        if (!filename.endsWith(SUFFIX)) {
            filename += SUFFIX;
        }
        return isPackage() ? Files.newInputStream(Paths.get(System.getProperty("user.dir"), DIR, filename)) : FileUtil.class.getClassLoader().getResourceAsStream(DIR + "/" + filename);
    }

    public static Path getPath(String filename) throws URISyntaxException {
        if (CharSequenceUtil.isBlank(filename)) {
            return null;
        }
        if (!filename.endsWith(SUFFIX)) {
            filename += SUFFIX;
        }
        return isPackage() ? Paths.get(System.getProperty("user.dir"), DIR, filename) : Path.of(Objects.requireNonNull(FileUtil.class.getClassLoader().getResource(DIR + "/" + filename)).toURI());
    }

    public static String getJarFilePath(Class<?> clazz) {
        try {
            URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
            File jarFile = new File(url.toURI());
            return jarFile.getParentFile().getAbsolutePath();
        } catch (URISyntaxException e) {
            LOG.error("Failed to get JAR file path for class: {}", clazz.getName(), e);
            return null;
        }
    }

    // 读取 JAR 文件中的所有 YANG 文件
    public static List<String> loadAllYangFilesFromJar(String jarFilePath) {
        List<String> result = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String fileName = entry.getName();

                if (fileName.endsWith(".yang")) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        result.add(reader.lines().collect(Collectors.joining("\n")));
                    }
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read YANG files from JAR: {}", jarFilePath, e);
        }
        return result;
    }

    private static String extractNamespace(String content) {
        Matcher matcher = NAMESPACE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractName(String content) {
        Matcher matcher = MODULE_NAME_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractRevision(String content) {
        Matcher matcher = REVISION_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static Set<YangModuleInfo> getModels(Class<?> clazz, String jarName) {
        String projectJarFilePath = getJarFilePath(clazz);
        if (CharSequenceUtil.isBlank(projectJarFilePath)) {
            return Collections.emptySet();
        }
        // 依赖 JAR 文件的路径
        Path libPath = Paths.get(projectJarFilePath, "lib");

        // Try to find models from lib directory first (for backward compatibility)
        if (Files.exists(libPath)) {
            // Try to find the specific JAR file first
            Path jarPath = Paths.get(projectJarFilePath, "lib", jarName);

            // If the specific JAR doesn't exist, try to find it by prefix matching
            if (!Files.exists(jarPath)) {
                try {
                    // Extract the base name without version (e.g., "otn-diligent-2" from "otn-diligent-2-22.2.0-SNAPSHOT.jar")
                    String baseName = jarName.replace(".jar", "");

                    // Find matching JAR files in lib directory
                    try (var stream = Files.list(libPath)) {
                        List<String> allYangContent = stream
                            .filter(path -> path.toString().endsWith(".jar"))
                            .filter(path -> path.getFileName().toString().startsWith(baseName + "-")
                                         || path.getFileName().toString().equals(jarName))
                            .findFirst()
                            .map(matchingJar -> loadAllYangFilesFromJar(matchingJar.toString()))
                            .orElse(Collections.emptyList());

                        return buildModelsFromYangContent(allYangContent);
                    }
                } catch (IOException e) {
                    LOG.error("Failed to search for JAR file in lib directory: {}", projectJarFilePath, e);
                    // Fall through to classpath-based loading
                }
            } else {
                // Load from the specific JAR file
                List<String> yangContent = loadAllYangFilesFromJar(jarPath.toString());
                return buildModelsFromYangContent(yangContent);
            }
        }

        // Fallback: Load models from classpath (all JARs in classpath)
        LOG.info("Loading YANG models from classpath for {}", jarName);
        return getModelsFromClasspath(clazz, jarName);
    }

    /**
     * Load YANG models from classpath JARs.
     * Scans all JAR files in the classpath for YANG model files.
     * Only loads from device model JARs (otn-*.jar, wdm-*.jar) to avoid duplicates.
     */
    private static Set<YangModuleInfo> getModelsFromClasspath(Class<?> clazz, String jarName) {
        try {
            Set<ModuleId> result = new HashSet<>();

            // Get all JARs from classpath
            String classpath = System.getProperty("java.class.path");
            if (classpath == null || classpath.isEmpty()) {
                return Collections.emptySet();
            }

            String[] jarPaths = classpath.split(File.pathSeparator);

            // Extract base device type from jarName (e.g., "otn-diligent-2" from "otn-diligent-2.jar")
            String baseDeviceType = jarName.replace(".jar", "");

            // First pass: load from the specific device model JAR for this device type
            for (String jarPath : jarPaths) {
                if (!jarPath.endsWith(".jar")) {
                    continue;
                }

                Path path = Paths.get(jarPath);
                String fileName = path.getFileName().toString();

                // Only load from the matching device model JAR
                if (fileName.startsWith(baseDeviceType + "-")) {
                    // Load YANG content from this JAR
                    try {
                        List<String> yangContent = loadAllYangFilesFromJar(jarPath);
                        for (String content : yangContent) {
                            Optional.ofNullable(extractNamespace(content))
                                .ifPresent(ns -> result.add(ModuleId.from(ns, extractName(content), extractRevision(content))));
                        }
                        // Found and loaded the device model JAR, break
                        break;
                    } catch (Exception e) {
                        LOG.error("Failed to load YANG models from JAR: {}", jarPath, e);
                    }
                }
            }

            if (result.isEmpty()) {
                LOG.warn("No YANG models found in classpath for {}", jarName);
                return Collections.emptySet();
            }

            LOG.info("Loaded {} YANG modules from classpath for {}", result.size(), jarName);
            return YangModuleUtils.getModelsFromClasspath(result);
        } catch (Exception e) {
            LOG.error("Failed to load YANG models from classpath", e);
            return Collections.emptySet();
        }
    }

    private static Set<YangModuleInfo> buildModelsFromYangContent(List<String> yangContent) {
        Set<ModuleId> result = new HashSet<>();
        yangContent.forEach(item -> Optional.ofNullable(extractNamespace(item)).ifPresent(ele -> result.add(ModuleId.from(ele, extractName(item), extractRevision(item)))));
        return YangModuleUtils.getModelsFromClasspath(result);
    }

    public static Map<String, String> getYangContent(Class<?> clazz, String jarName) {
        String projectJarFilePath = getJarFilePath(clazz);
        if (CharSequenceUtil.isBlank(projectJarFilePath)) {
            return Collections.emptyMap();
        }
        // 依赖 JAR 文件的路径
        Path libPath = Paths.get(projectJarFilePath, "lib");

        // Try to find the specific JAR file first
        Path jarPath = Paths.get(projectJarFilePath, "lib", jarName);

        // If the specific JAR doesn't exist, try to find it by prefix matching
        if (!Files.exists(jarPath)) {
            try {
                // Extract the base name without version (e.g., "otn-diligent-2" from "otn-diligent-2-22.2.0-SNAPSHOT.jar")
                String baseName = jarName.replace(".jar", "");

                // Find matching JAR files in lib directory
                try (var stream = Files.list(libPath)) {
                    return stream
                        .filter(path -> path.toString().endsWith(".jar"))
                        .filter(path -> path.getFileName().toString().startsWith(baseName + "-")
                                     || path.getFileName().toString().equals(jarName))
                        .findFirst()
                        .map(matchingJar -> {
                            List<String> yangContent = loadAllYangFilesFromJar(matchingJar.toString());
                            Map<String, String> result = new HashMap<>();
                            yangContent.forEach(item -> result.put(extractName(item), item));
                            return result;
                        })
                        .orElse(Collections.emptyMap());
                }
            } catch (IOException e) {
                LOG.error("Failed to search for JAR file in lib directory: {}", projectJarFilePath, e);
                return Collections.emptyMap();
            }
        }

        // Load from the specific JAR file
        List<String> yangContent = loadAllYangFilesFromJar(jarPath.toString());
        Map<String, String> result = new HashMap<>();
        yangContent.forEach(item -> result.put(extractName(item), item));
        return result;
    }

}
