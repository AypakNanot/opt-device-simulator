package io.lighty.netconf.device.utils;


import cn.hutool.core.text.CharSequenceUtil;
import io.lighty.core.common.models.ModuleId;
import io.lighty.core.common.models.YangModuleUtils;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileUtil {
    public static String DIR = "xml";
    public static String SUFFIX = ".xml";

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
            // 获取当前类的 ClassLoader
            URL url = clazz.getProtectionDomain().getCodeSource().getLocation();

            // 将 URL 转换为 URI，然后获取路径
            File jarFile = new File(url.toURI());
            return jarFile.getParentFile().getAbsolutePath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 读取 JAR 文件中的所有 YANG 文件
    public static List<String> loadAllYangFilesFromJar(String jarFilePath) {
        List<String> result = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(jarFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            boolean foundYangFile = false;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String fileName = entry.getName();

                // 检查文件是否以 .yang 结尾
                if (fileName.endsWith(".yang")) {
                    foundYangFile = true;

                    // 读取并输出 YANG 文件内容
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        StringBuilder fileContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            fileContent.append(line).append("\n");
                        }
                        result.add(fileContent.toString());
                    }
                }
            }

            if (!foundYangFile) {
                System.out.println("No YANG files found in the JAR.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    // 提取 namespace
    private static String extractNamespace(String content) {
        String regex = "namespace\\s+\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // 提取 name
    private static String extractName(String content) {
        String regex = "module\\s+\"?([a-zA-Z0-9\\-]+)\"?\\s*\\{";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // 提取 revision
    private static String extractRevision(String content) {
        String regex = "revision\\s+\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static Set<YangModuleInfo> getModels(Class<?> clazz, String jarName) {
        String projectJarFilePath = getJarFilePath(clazz);
        if (CharSequenceUtil.isBlank(projectJarFilePath)) {
            return Collections.emptySet();
        }
        // 依赖 JAR 文件的路径
        Path jarPath = Paths.get(projectJarFilePath, "lib", jarName);
        // 加载并列出所有 YANG 文件
        List<String> yangContent = loadAllYangFilesFromJar(jarPath.toString());
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
        Path jarPath = Paths.get(projectJarFilePath, "lib", jarName);
        // 加载并列出所有 YANG 文件
        List<String> yangContent = loadAllYangFilesFromJar(jarPath.toString());
        Map<String, String> result = new HashMap<>();
        yangContent.forEach(item -> result.put(extractName(item), item));
        return result;
    }

}
