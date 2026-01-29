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
import java.util.jar.JarFile;

public class YamlPackImporter {

    private static final String MARKER_FILE = ".kci-imported.txt";

    public static void importEmbeddedPacks(
            Plugin self,
            File dataFolder,
            Consumer<String> log,
            boolean enabled,
            boolean restrictToRoots,
            List<String> roots,
            boolean overrideExisting
    ) {
        if (!enabled) {
            log.accept(ChatColor.DARK_GRAY + "Embedded pack import is disabled.");
            return;
        }
        List<String> normalizedRoots = normalizeRoots(roots);
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        int importedCount = 0;
        log.accept(ChatColor.GRAY + "Scanning plugin jars for embedded packs...");
        log.accept(ChatColor.GRAY + "Found " + plugins.length + " plugin(s).");

        for (Plugin plugin : plugins) {
            if (plugin == null) continue;
            if (plugin == self) {
                log.accept(ChatColor.DARK_GRAY + "Skipping CustomItems jar.");
                continue;
            }
            File jarFile = getPluginJar(plugin);
            if (jarFile == null || !jarFile.isFile()) {
                log.accept(ChatColor.DARK_GRAY + "Skipping " + plugin.getName() + ": jar not found.");
                continue;
            }
            int importedFromPlugin = importFromJar(
                    plugin, jarFile, dataFolder, log, restrictToRoots, normalizedRoots, overrideExisting
            );
            if (importedFromPlugin == 0) {
                log.accept(ChatColor.DARK_GRAY + "No embedded packs imported from " + plugin.getName() + ".");
            }
            importedCount += importedFromPlugin;
        }

        if (importedCount > 0) {
            log.accept(ChatColor.GREEN + "Imported " + importedCount + " embedded pack(s) from other plugins.");
        } else {
            log.accept(ChatColor.GRAY + "No embedded packs imported.");
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
            Plugin plugin,
            File jarFile,
            File dataFolder,
            Consumer<String> log,
            boolean restrictToRoots,
            List<String> roots,
            boolean overrideExisting
    ) {
        Map<String, PackInfo> packs = new HashMap<>();
        List<PackResource> globalAssets = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            jar.stream().forEach(entry -> {
                String globalAssetPath = parseGlobalAssetsPath(entry.getName(), roots);
                if (globalAssetPath != null) {
                    if (!entry.isDirectory()) {
                        globalAssets.add(new PackResource(entry.getName(), globalAssetPath));
                    }
                    return;
                }
                PackPath path = parsePackPath(entry.getName(), restrictToRoots, roots);
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

        if (packs.isEmpty()) {
            log.accept(ChatColor.DARK_GRAY + "No embedded pack roots found in " + plugin.getName() + ".");
            return 0;
        }
        log.accept(ChatColor.GRAY + "Found " + packs.size() + " embedded pack folder(s) in " + plugin.getName() + ".");

        int imported = 0;
        for (Map.Entry<String, PackInfo> entry : packs.entrySet()) {
            PackInfo info = entry.getValue();
            if (!info.hasYamlDefinition) {
                log.accept(ChatColor.DARK_GRAY + "Skipping pack '" + entry.getKey() + "' from " + plugin.getName()
                        + ": no item/block/recipe/projectile YAML definitions detected.");
                continue;
            }

            String packName = entry.getKey();
            if (!isSafePackName(packName)) {
                log.accept(ChatColor.RED + "Skipping embedded pack '" + packName + "' from "
                        + plugin.getName() + ": invalid pack name");
                continue;
            }

            File targetPackDir = new File(dataFolder, packName);
            if (targetPackDir.exists() && !overrideExisting) {
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
                    log.accept(ChatColor.GREEN + "Imported pack '" + packName + "' from " + plugin.getName()
                            + " (" + info.resources.size() + " file(s)).");
                    imported++;
                }
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to read plugin jar " + jarFile.getName() + ": " + ex.getMessage());
            }
        }

        if (!globalAssets.isEmpty()) {
            try (JarFile jar = new JarFile(jarFile)) {
                int copied = copyGlobalAssets(
                        jar, dataFolder, globalAssets, log, plugin.getName(), overrideExisting
                );
                if (copied > 0) {
                    log.accept(ChatColor.GREEN + "Imported " + copied + " global asset(s) from " + plugin.getName() + ".");
                }
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to read plugin jar " + jarFile.getName()
                        + " for global assets: " + ex.getMessage());
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

    private static int copyGlobalAssets(
            JarFile jar,
            File dataFolder,
            List<PackResource> resources,
            Consumer<String> log,
            String pluginName,
            boolean overrideExisting
    ) {
        File targetAssetsDir = new File(dataFolder, "assets");
        if (!targetAssetsDir.exists() && !targetAssetsDir.mkdirs()) {
            log.accept(ChatColor.RED + "Failed to create global assets directory " + targetAssetsDir.getPath()
                    + " for " + pluginName);
            return 0;
        }

        int copied = 0;
        for (PackResource resource : resources) {
            if (!isSafeRelativePath(resource.relativePath)) {
                log.accept(ChatColor.RED + "Skipping suspicious global asset path from " + pluginName
                        + ": " + resource.relativePath);
                continue;
            }
            File targetFile = new File(targetAssetsDir, resource.relativePath);
            if (targetFile.exists() && !overrideExisting) continue;

            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.accept(ChatColor.RED + "Failed to create directory " + parent.getPath()
                        + " for global assets from " + pluginName);
                continue;
            }
            java.util.jar.JarEntry jarEntry = jar.getJarEntry(resource.entryName);
            if (jarEntry == null) {
                log.accept(ChatColor.RED + "Missing global asset entry " + resource.entryName + " in " + pluginName);
                continue;
            }
            try (InputStream input = jar.getInputStream(jarEntry)) {
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to copy " + resource.entryName + " from " + pluginName
                        + " (global assets): " + ex.getMessage());
            }
        }

        return copied;
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

    private static PackPath parsePackPath(String entryName, boolean restrictToRoots, List<String> roots) {
        if (entryName == null) return null;
        if (restrictToRoots) {
            for (String root : roots) {
                String normalized = ensureTrailingSlash(root);
                if (entryName.startsWith(normalized)) {
                    String remainder = entryName.substring(normalized.length());
                    int slash = remainder.indexOf('/');
                    if (slash <= 0) return null;
                    String packName = remainder.substring(0, slash);
                    String relativePath = remainder.substring(slash + 1);
                    return new PackPath(packName, relativePath, normalized);
                }
            }
            return null;
        } else {
            int slash = entryName.indexOf('/');
            if (slash <= 0) return null;
            String packName = entryName.substring(0, slash);
            if (isIgnoredRoot(packName, roots)) return null;
            String relativePath = entryName.substring(slash + 1);
            return new PackPath(packName, relativePath, "");
        }
    }

    private static String parseGlobalAssetsPath(String entryName, List<String> roots) {
        if (entryName == null) return null;
        String normalized = entryName;
        String directPrefix = "assets/";
        if (normalized.startsWith(directPrefix)) {
            String relative = normalized.substring(directPrefix.length());
            return relative.isEmpty() ? null : relative;
        }
        for (String root : roots) {
            String normalizedRoot = ensureTrailingSlash(root);
            String assetsPrefix = normalizedRoot + "assets/";
            if (normalized.startsWith(assetsPrefix)) {
                String relative = normalized.substring(assetsPrefix.length());
                return relative.isEmpty() ? null : relative;
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
            if (trimmed.equals("---")) continue;
            return trimmed.startsWith("item:") || trimmed.startsWith("block:") || trimmed.startsWith("recipe:")
                    || trimmed.startsWith("projectile:") || trimmed.startsWith("projectile_cover:")
                    || trimmed.startsWith("projectile-cover:");
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

    private static List<String> normalizeRoots(List<String> roots) {
        if (roots == null || roots.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            defaults.add("customitems");
            defaults.add("custom-items");
            return defaults;
        }
        List<String> normalized = new ArrayList<>(roots.size());
        for (String root : roots) {
            if (root == null) continue;
            String trimmed = root.trim();
            if (trimmed.isEmpty()) continue;
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            normalized.add("customitems");
            normalized.add("custom-items");
        }
        return normalized;
    }

    private static boolean isIgnoredRoot(String packName, List<String> roots) {
        if (packName == null || packName.isEmpty()) return true;
        String normalized = packName.trim().toLowerCase(Locale.ROOT);
        for (String root : roots) {
            String rootName = root.toLowerCase(Locale.ROOT);
            if (rootName.endsWith("/")) rootName = rootName.substring(0, rootName.length() - 1);
            if (rootName.equals(normalized)) return true;
        }
        return false;
    }

    private static String ensureTrailingSlash(String root) {
        if (root.endsWith("/")) return root;
        return root + "/";
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
