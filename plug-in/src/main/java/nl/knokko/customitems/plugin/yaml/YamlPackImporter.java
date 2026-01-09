package nl.knokko.customitems.plugin.yaml;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class YamlPackImporter {

    private static final String[] PACK_ROOTS = { "customitems/", "custom-items/" };
    private static final String MARKER_FILE = ".kci-imported.txt";

    static void importEmbeddedPacks(Plugin self, File dataFolder, Consumer<String> log) {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        int importedCount = 0;

        for (Plugin plugin : plugins) {
            if (plugin == null || plugin == self) continue;
            File jarFile = getPluginJar(plugin);
            if (jarFile == null || !jarFile.isFile()) continue;
            importedCount += importFromJar(plugin, jarFile, dataFolder, log);
        }

        if (importedCount > 0) {
            log.accept(ChatColor.GREEN + "Imported " + importedCount + " embedded pack(s) from other plugins.");
        }
    }

    private static File getPluginJar(Plugin plugin) {
        try {
            URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return null;
            return new File(location.toURI());
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static int importFromJar(
            Plugin plugin, File jarFile, File dataFolder, Consumer<String> log
    ) {
        Map<String, PackInfo> packs = new HashMap<>();
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream().forEach(entry -> {
                PackPath path = parsePackPath(entry.getName());
                if (path == null || path.relativePath.isEmpty()) return;
                PackInfo info = packs.computeIfAbsent(path.packName, name -> new PackInfo(path.root));
                if (!entry.isDirectory()) {
                    info.resources.add(new PackResource(entry.getName(), path.relativePath));
                    if (!info.hasYamlDefinition && isYamlFile(path.relativePath)) {
                        try (InputStream input = jar.getInputStream(entry)) {
                            if (looksLikeYamlDefinition(input)) {
                                info.hasYamlDefinition = true;
                            }
                        } catch (IOException ignored) {
                            // Ignore unreadable YAML files
                        }
                    }
                }
            });
        } catch (IOException ex) {
            log.accept(ChatColor.RED + "Failed to read plugin jar " + jarFile.getName() + ": " + ex.getMessage());
            return 0;
        }

        int imported = 0;
        for (Map.Entry<String, PackInfo> entry : packs.entrySet()) {
            PackInfo info = entry.getValue();
            if (!info.hasYamlDefinition) continue;

            String packName = entry.getKey();
            if (!isSafePackName(packName)) {
                log.accept(ChatColor.RED + "Skipping embedded pack '" + packName + "' from "
                        + plugin.getName() + ": invalid pack name");
                continue;
            }

            File targetPackDir = new File(dataFolder, packName);
            if (targetPackDir.exists() && !shouldOverwrite(targetPackDir, plugin)) {
                log.accept(ChatColor.YELLOW + "Skipping embedded pack '" + packName + "' from " + plugin.getName()
                        + " because " + targetPackDir.getPath() + " already exists.");
                continue;
            }

            if (!targetPackDir.exists() && !targetPackDir.mkdirs()) {
                log.accept(ChatColor.RED + "Failed to create pack directory " + targetPackDir.getPath());
                continue;
            }

            try (JarFile jar = new JarFile(jarFile)) {
                if (copyPackResources(jar, targetPackDir, info.resources, log, plugin.getName(), packName)) {
                    writeMarker(targetPackDir, plugin, info.root);
                    imported++;
                }
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to read plugin jar " + jarFile.getName() + ": " + ex.getMessage());
            }
        }

        return imported;
    }

    private static boolean copyPackResources(
            JarFile jar,
            File targetPackDir,
            List<PackResource> resources,
            Consumer<String> log,
            String pluginName,
            String packName
    ) {
        for (PackResource resource : resources) {
            if (!isSafeRelativePath(resource.relativePath)) {
                log.accept(ChatColor.RED + "Skipping suspicious path in embedded pack '" + packName
                        + "' from " + pluginName + ": " + resource.relativePath);
                continue;
            }
            File targetFile = new File(targetPackDir, resource.relativePath);
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.accept(ChatColor.RED + "Failed to create directory " + parent.getPath());
                return false;
            }
            try (InputStream input = jar.getInputStream(jar.getJarEntry(resource.entryName))) {
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to copy " + resource.entryName + " from " + pluginName
                        + ": " + ex.getMessage());
                return false;
            }
        }
        return true;
    }

    private static boolean shouldOverwrite(File targetPackDir, Plugin plugin) {
        File marker = new File(targetPackDir, MARKER_FILE);
        if (!marker.isFile()) return false;
        try {
            List<String> lines = Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8);
            return !lines.isEmpty() && lines.get(0).trim().equals(plugin.getName());
        } catch (IOException ex) {
            return false;
        }
    }

    private static void writeMarker(File targetPackDir, Plugin plugin, String root) {
        File marker = new File(targetPackDir, MARKER_FILE);
        String content = plugin.getName() + "\n" + root;
        try {
            Files.write(marker.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Marker failures are not critical
        }
    }

    private static PackPath parsePackPath(String entryName) {
        if (entryName == null) return null;
        for (String root : PACK_ROOTS) {
            if (entryName.startsWith(root)) {
                String remainder = entryName.substring(root.length());
                int slash = remainder.indexOf('/');
                if (slash <= 0) return null;
                String packName = remainder.substring(0, slash);
                String relativePath = remainder.substring(slash + 1);
                return new PackPath(packName, relativePath, root);
            }
        }
        return null;
    }

    private static boolean isYamlFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static boolean looksLikeYamlDefinition(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
                trimmed = trimmed.substring(1).trim();
            }
            return trimmed.startsWith("item:") || trimmed.startsWith("block:");
        }
        return false;
    }

    private static boolean isSafePackName(String packName) {
        if (packName.isEmpty()) return false;
        return !(packName.contains("..") || packName.contains("/") || packName.contains("\\")
                || packName.contains(":") || packName.startsWith("."));
    }

    private static boolean isSafeRelativePath(String path) {
        if (path.isEmpty()) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        return !(path.contains("..") || path.contains(":"));
    }

    private static class PackPath {
        final String packName;
        final String relativePath;
        final String root;

        PackPath(String packName, String relativePath, String root) {
            this.packName = packName;
            this.relativePath = relativePath;
            this.root = root;
        }
    }

    private static class PackResource {
        final String entryName;
        final String relativePath;

        PackResource(String entryName, String relativePath) {
            this.entryName = entryName;
            this.relativePath = relativePath;
        }
    }

    private static class PackInfo {
        final String root;
        final List<PackResource> resources = new ArrayList<>();
        boolean hasYamlDefinition;

        PackInfo(String root) {
            this.root = root;
        }
    }
}
