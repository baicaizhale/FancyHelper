package org.YanPl.util;

import org.YanPl.FancyHelper;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ResourceUtil {

    /**
     * 资源工具类：负责将 jar 内嵌的资源释放到插件数据目录（如 preset 文件）。
     */

    public static void releaseResources(FancyHelper plugin, String resourceDir, boolean replace, String extension) {
        if (!resourceDir.endsWith("/")) {
            resourceDir += "/";
        }

        // 遍历 jar 包中的条目并保存匹配的资源
        try {
            URL jarUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            URI jarUri = jarUrl.toURI();
            File jarFile = new File(jarUri);
            
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (name.startsWith(resourceDir) && !entry.isDirectory()) {
                        if (extension == null || name.endsWith(extension)) {
                            saveResource(plugin, name, replace);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("释放资源目录 " + resourceDir + " 时出错: " + e.getMessage());
        }
    }

    public static void saveResource(FancyHelper plugin, String resourcePath, boolean replace) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (replace || !file.exists()) {
            try {
                plugin.saveResource(resourcePath, replace);
            } catch (IllegalArgumentException e) {
            }
        }
    }
}
