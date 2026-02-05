package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.plugin.resourcepack.YamlResourcepackGenerator;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class YamlSourceResourcepackCli {

    private static final String DEFAULT_OUTPUT_NAME = "resource-pack.zip";
    private static final String[] DEFAULT_ROOTS = new String[] { "customitems", "custom-items" };
    private static final String[] YAML_KEYS = new String[] {
            "item:", "block:", "recipe:", "projectile:", "projectile_cover:", "projectile-cover:"
    };

    public static void main(String[] args) {
        int exit = run(args, System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    private static int run(String[] args, PrintStream out, PrintStream err) {
        CliArgs parsed = parseArgs(args, err);
        if (parsed == null) return 1;

        Path pluginRoot = parsed.pluginRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(pluginRoot)) {
            err.println("Plugin root does not exist or is not a directory: " + pluginRoot);
            return 1;
        }

        Path resourcesDir = pluginRoot.resolve(Paths.get("src", "main", "resources"));
        if (!Files.isDirectory(resourcesDir)) {
            err.println("Missing resources directory: " + resourcesDir);
            return 1;
        }

        Integer mcVersion = resolveMcVersion(parsed.mcVersionRaw, resourcesDir, out, err);
        if (mcVersion == null) return 1;
        YamlVersionContext.mcVersion = mcVersion;

        Path outputPath = resolveOutputPath(parsed.outputPath);
        try {
            Path outputParent = outputPath.getParent();
            if (outputParent != null) {
                Files.createDirectories(outputParent);
            }
        } catch (IOException ex) {
            err.println("Failed to create output directory: " + outputPath.getParent() + " (" + ex.getMessage() + ")");
            return 1;
        }

        List<String> packRoots = normalizeRoots(parsed.packRoots);
        ScanResult scan;
        try {
            scan = scanResources(resourcesDir, parsed.restrictToRoots, packRoots);
        } catch (IOException ex) {
            err.println("Failed to scan resources: " + ex.getMessage());
            return 1;
        }

        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory("kci-yaml-source-pack-");
        } catch (IOException ex) {
            err.println("Failed to create staging directory: " + ex.getMessage());
            return 1;
        }

        try {
            stageGlobalAssets(scan.globalAssets, stagingDir, out, err);
            int packCount = stagePacks(scan.packs, stagingDir, out, err);
            if (packCount == 0) {
                err.println("No YAML packs found under " + resourcesDir);
                return 1;
            }

            ItemSet itemSet = buildItemSet(stagingDir.toFile(), mcVersion, out, err);
            if (itemSet == null) return 1;

            YamlResourcepackGenerator.write(itemSet, outputPath.toFile());
            out.println("Generated resource pack: " + outputPath);
            return 0;
        } catch (ValidationException | ProgrammingValidationException ex) {
            err.println("Resource pack generation failed: " + ex.getMessage());
            return 1;
        } catch (IOException ex) {
            err.println("I/O error: " + ex.getMessage());
            return 1;
        } finally {
            try {
                deleteRecursively(stagingDir);
            } catch (IOException ex) {
                err.println("Warning: failed to delete staging directory " + stagingDir + ": " + ex.getMessage());
            }
        }
    }

    private static CliArgs parseArgs(String[] args, PrintStream err) {
        if (args == null || args.length == 0) {
            printUsage(err);
            return null;
        }

        String mcVersionRaw = null;
        boolean restrictToRoots = false;
        List<String> roots = new ArrayList<>();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage(err);
                return null;
            }
            if (arg.startsWith("--mc-version=") || arg.startsWith("--mcVersion=") || arg.startsWith("--mc=")) {
                mcVersionRaw = arg.substring(arg.indexOf('=') + 1);
                continue;
            }
            if ("--mc-version".equals(arg) || "--mcVersion".equals(arg) || "--mc".equals(arg)) {
                if (i + 1 >= args.length) {
                    err.println("Missing value for " + arg);
                    printUsage(err);
                    return null;
                }
                mcVersionRaw = args[++i];
                continue;
            }
            if ("--restrict-to-roots".equals(arg) || "--restrict-to-pack-roots".equals(arg)) {
                restrictToRoots = true;
                continue;
            }
            if ("--no-restrict-to-roots".equals(arg) || "--allow-all-roots".equals(arg)) {
                restrictToRoots = false;
                continue;
            }
            if (arg.startsWith("--pack-roots=") || arg.startsWith("--roots=")) {
                String raw = arg.substring(arg.indexOf('=') + 1);
                roots.addAll(parseRoots(raw));
                continue;
            }
            if ("--pack-roots".equals(arg) || "--roots".equals(arg)) {
                if (i + 1 >= args.length) {
                    err.println("Missing value for " + arg);
                    printUsage(err);
                    return null;
                }
                roots.addAll(parseRoots(args[++i]));
                continue;
            }
            if (arg.startsWith("-")) {
                err.println("Unknown option: " + arg);
                printUsage(err);
                return null;
            }
            positional.add(arg);
        }

        if (positional.size() < 2) {
            printUsage(err);
            return null;
        }

        CliArgs parsed = new CliArgs();
        parsed.pluginRoot = Paths.get(positional.get(0));
        parsed.outputPath = Paths.get(positional.get(1));
        parsed.mcVersionRaw = mcVersionRaw;
        parsed.restrictToRoots = restrictToRoots;
        parsed.packRoots = roots;
        return parsed;
    }

    private static List<String> parseRoots(String raw) {
        if (raw == null) return Collections.emptyList();
        String[] parts = raw.split(",");
        List<String> roots = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) roots.add(trimmed);
        }
        return roots;
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: <pluginRoot> <outputPath> [--mc-version <1.20.4>] [--restrict-to-roots] [--roots <customitems,custom-items>]");
        err.println("If outputPath is a directory, " + DEFAULT_OUTPUT_NAME + " will be created inside it.");
    }

    private static Integer resolveMcVersion(
            String rawValue,
            Path resourcesDir,
            PrintStream out,
            PrintStream err
    ) {
        Integer parsed = null;
        if (rawValue != null && !rawValue.trim().isEmpty()) {
            parsed = parseMcVersion(rawValue.trim(), err);
            if (parsed == null) return null;
            out.println("Using MC version from args: " + MCVersions.createString(parsed));
            return parsed;
        }

        parsed = readApiVersion(resourcesDir, out, err);
        if (parsed != null) return parsed;

        int fallback = MCVersions.LAST_VERSION;
        out.println("No MC version specified; defaulting to " + MCVersions.createString(fallback));
        return fallback;
    }

    private static Integer parseMcVersion(String raw, PrintStream err) {
        Integer parsed = MCVersions.parseVersion(raw);
        if (parsed == null) {
            err.println("Invalid MC version: " + raw);
            return null;
        }
        return MCVersions.normalize(parsed);
    }

    private static Integer readApiVersion(Path resourcesDir, PrintStream out, PrintStream err) {
        Integer fromPlugin = readApiVersionFile(resourcesDir.resolve("plugin.yml"), out, err);
        if (fromPlugin != null) return fromPlugin;
        Integer fromPaper = readApiVersionFile(resourcesDir.resolve("paper-plugin.yml"), out, err);
        if (fromPaper != null) return fromPaper;
        return null;
    }

    private static Integer readApiVersionFile(Path path, PrintStream out, PrintStream err) {
        if (!Files.isRegularFile(path)) return null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
        String raw = config.getString("api-version");
        if (raw == null || raw.trim().isEmpty()) return null;
        Integer parsed = MCVersions.parseVersion(raw);
        if (parsed == null) {
            err.println("Ignoring invalid api-version in " + path + ": " + raw);
            return null;
        }
        int normalized = MCVersions.normalize(parsed);
        out.println("Using api-version from " + path.getFileName() + ": " + MCVersions.createString(normalized));
        return normalized;
    }

    private static Path resolveOutputPath(Path outputPath) {
        Path normalized = outputPath.toAbsolutePath().normalize();
        String lower = normalized.toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return normalized;
        }
        return normalized.resolve(DEFAULT_OUTPUT_NAME);
    }

    private static ScanResult scanResources(Path resourcesDir, boolean restrictToRoots, List<String> roots) throws IOException {
        Map<String, PackInfo> packs = new LinkedHashMap<>();
        List<ResourceCopy> globalAssets = new ArrayList<>();

        Files.walkFileTree(resourcesDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = resourcesDir.relativize(file).toString().replace('\\', '/');
                String globalAsset = parseGlobalAssetsPath(relative, roots);
                if (globalAsset != null) {
                    if (isSafeRelativePath(globalAsset)) {
                        globalAssets.add(new ResourceCopy(file, globalAsset));
                    }
                    return FileVisitResult.CONTINUE;
                }

                PackPath packPath = parsePackPath(relative, restrictToRoots, roots);
                if (packPath == null || packPath.relativePath.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }

                PackInfo info = packs.computeIfAbsent(packPath.packName, PackInfo::new);
                if (isSafeRelativePath(packPath.relativePath)) {
                    info.resources.add(new ResourceCopy(file, packPath.relativePath));
                }

                if (!info.hasYamlDefinition && isYamlFile(packPath.relativePath)) {
                    if (looksLikeYamlDefinition(file)) {
                        info.hasYamlDefinition = true;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return new ScanResult(packs, globalAssets);
    }

    private static int stagePacks(Map<String, PackInfo> packs, Path stagingDir, PrintStream out, PrintStream err)
            throws IOException {
        int count = 0;
        List<PackInfo> sorted = new ArrayList<>(packs.values());
        sorted.sort(Comparator.comparing(info -> info.name));
        for (PackInfo info : sorted) {
            if (!info.hasYamlDefinition) {
                out.println("Skipping pack '" + info.name + "': no YAML definitions found.");
                continue;
            }
            if (!isSafePackName(info.name)) {
                err.println("Skipping pack '" + info.name + "': invalid pack name.");
                continue;
            }
            Path target = stagingDir.resolve(info.name);
            copyResources(info.resources, target, err);
            count++;
        }
        if (count > 0) {
            out.println("Staged " + count + " pack(s).");
        }
        return count;
    }

    private static void stageGlobalAssets(List<ResourceCopy> resources, Path stagingDir, PrintStream out, PrintStream err)
            throws IOException {
        if (resources.isEmpty()) return;
        Path assetsTarget = stagingDir.resolve("assets");
        copyResources(resources, assetsTarget, err);
        out.println("Staged global assets.");
    }

    private static void copyResources(List<ResourceCopy> resources, Path targetRoot, PrintStream err) throws IOException {
        if (resources.isEmpty()) return;

        List<ResourceCopy> sorted = new ArrayList<>(resources);
        sorted.sort(Comparator
                .comparing((ResourceCopy resource) -> resource.relativePath)
                .thenComparing(resource -> resource.source.toString()));

        for (ResourceCopy resource : sorted) {
            if (!isSafeRelativePath(resource.relativePath)) {
                err.println("Skipping suspicious path: " + resource.relativePath);
                continue;
            }
            Path target = targetRoot.resolve(resource.relativePath).normalize();
            if (!target.startsWith(targetRoot)) {
                err.println("Skipping suspicious path: " + resource.relativePath);
                continue;
            }
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(resource.source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isYamlFile(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static boolean looksLikeYamlDefinition(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.charAt(0) == '\uFEFF') {
                    trimmed = trimmed.substring(1).trim();
                }
                if ("---".equals(trimmed)) continue;
                for (String key : YAML_KEYS) {
                    if (trimmed.startsWith(key)) return true;
                }
                return false;
            }
        }
        return false;
    }

    private static PackPath parsePackPath(String relativePath, boolean restrictToRoots, List<String> roots) {
        if (relativePath == null) return null;
        String normalized = relativePath.replace('\\', '/');

        PackPath rooted = parsePackPathInRoots(normalized, roots);
        if (rooted != null) return rooted;
        if (restrictToRoots) return null;

        int slash = normalized.indexOf('/');
        if (slash <= 0) return null;
        String packName = normalized.substring(0, slash);
        if (isIgnoredRoot(packName, roots)) return null;
        String relative = normalized.substring(slash + 1);
        return new PackPath(packName, relative);
    }

    private static PackPath parsePackPathInRoots(String normalizedPath, List<String> roots) {
        if (normalizedPath == null) return null;
        for (String root : roots) {
            String normalizedRoot = ensureTrailingSlash(root);
            if (normalizedPath.startsWith(normalizedRoot)) {
                String remainder = normalizedPath.substring(normalizedRoot.length());
                int slash = remainder.indexOf('/');
                if (slash <= 0) return null;
                String packName = remainder.substring(0, slash);
                String relative = remainder.substring(slash + 1);
                return new PackPath(packName, relative);
            }
        }
        return null;
    }

    private static String parseGlobalAssetsPath(String relativePath, List<String> roots) {
        if (relativePath == null) return null;
        String normalized = relativePath.replace('\\', '/');
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

    private static boolean isSafePackName(String packName) {
        if (packName == null || packName.isEmpty()) return false;
        return !(packName.contains("..") || packName.contains("/") || packName.contains("\\")
                || packName.contains(":") || packName.startsWith("."));
    }

    private static boolean isSafeRelativePath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        return !(path.contains("..") || path.contains(":"));
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

    private static List<String> normalizeRoots(List<String> roots) {
        if (roots == null || roots.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            Collections.addAll(defaults, DEFAULT_ROOTS);
            return defaults;
        }
        List<String> normalized = roots.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            Collections.addAll(normalized, DEFAULT_ROOTS);
        }
        return normalized;
    }

    private static ItemSet buildItemSet(File dataFolder, int mcVersion, PrintStream out, PrintStream err)
            throws ValidationException, ProgrammingValidationException {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<YamlItemDefinition> items = new ArrayList<>();
        List<YamlBlockDefinition> blocks = new ArrayList<>();
        List<YamlRecipeDefinition> recipes = new ArrayList<>();
        List<YamlProjectileCoverDefinition> projectileCovers = new ArrayList<>();
        List<YamlProjectileDefinition> projectiles = new ArrayList<>();

        File[] packDirs = dataFolder.listFiles(File::isDirectory);
        if (packDirs == null) {
            err.println("No pack directories found in " + dataFolder.getPath());
            return null;
        }

        int packCount = 0;
        for (File packDir : packDirs) {
            if ("assets".equalsIgnoreCase(packDir.getName())) continue;
            YamlPackDefinition pack = YamlPackReader.readPack(packDir, errors);
            if (pack == null) continue;
            List<YamlItemDefinition> packItems = YamlItemReader.readItems(pack, errors, warnings);
            List<YamlBlockDefinition> packBlocks = YamlBlockReader.readBlocks(pack, errors, warnings);
            List<YamlRecipeDefinition> packRecipes = YamlRecipeReader.readRecipes(pack, errors, warnings);
            List<YamlProjectileCoverDefinition> packCovers = YamlProjectileCoverReader.readProjectileCovers(pack, errors, warnings);
            List<YamlProjectileDefinition> packProjectiles = YamlProjectileReader.readProjectiles(pack, errors, warnings);
            if (!packItems.isEmpty() || !packBlocks.isEmpty() || !packRecipes.isEmpty()
                    || !packCovers.isEmpty() || !packProjectiles.isEmpty()) {
                packCount++;
                items.addAll(packItems);
                blocks.addAll(packBlocks);
                recipes.addAll(packRecipes);
                projectileCovers.addAll(packCovers);
                projectiles.addAll(packProjectiles);
            }
        }

        if (!warnings.isEmpty()) {
            logWarnings(warnings, out);
        }

        if (!errors.isEmpty()) {
            logErrors(errors, err);
            err.println("YAML conversion failed.");
            return null;
        }

        if (items.isEmpty() && blocks.isEmpty() && recipes.isEmpty()
                && projectileCovers.isEmpty() && projectiles.isEmpty()) {
            err.println("No YAML definitions were found.");
            return null;
        }

        if (!checkDuplicates(items, blocks, recipes, projectileCovers, projectiles, errors)) {
            logErrors(errors, err);
            return null;
        }

        Collections.sort(items, Comparator.comparing(item -> item.internalName));
        Collections.sort(blocks, Comparator.comparing(block -> block.internalName));
        Collections.sort(recipes, Comparator.comparing(recipe -> recipe.internalName));
        Collections.sort(projectileCovers, Comparator.comparing(cover -> cover.internalName));
        Collections.sort(projectiles, Comparator.comparing(projectile -> projectile.internalName));

        ItemSet itemSet = YamlItemSetBuilder.build(items, blocks, recipes, projectileCovers, projectiles, mcVersion);
        out.println("Converted " + items.size() + " item(s), " + blocks.size() + " block(s), "
                + recipes.size() + " recipe(s), " + projectileCovers.size() + " projectile cover(s), and "
                + projectiles.size() + " projectile(s) from " + packCount + " pack(s).");
        return itemSet;
    }

    private static boolean checkDuplicates(
            List<YamlItemDefinition> items,
            List<YamlBlockDefinition> blocks,
            List<YamlRecipeDefinition> recipes,
            List<YamlProjectileCoverDefinition> projectileCovers,
            List<YamlProjectileDefinition> projectiles,
            List<String> errors
    ) {
        Map<String, File> internalNameSources = new HashMap<>();
        for (YamlItemDefinition item : items) {
            File existing = internalNameSources.putIfAbsent(item.internalName, item.sourceFile);
            if (existing != null) {
                errors.add("Duplicate item id for internal name '" + item.internalName + "' in "
                        + existing.getPath() + " and " + item.sourceFile.getPath());
            }
        }
        Map<String, File> blockNameSources = new HashMap<>();
        for (YamlBlockDefinition block : blocks) {
            File existing = blockNameSources.putIfAbsent(block.internalName, block.sourceFile);
            if (existing != null) {
                errors.add("Duplicate block id for internal name '" + block.internalName + "' in "
                        + existing.getPath() + " and " + block.sourceFile.getPath());
            }
        }
        Map<String, File> recipeNameSources = new HashMap<>();
        for (YamlRecipeDefinition recipe : recipes) {
            File existing = recipeNameSources.putIfAbsent(recipe.internalName, recipe.sourceFile);
            if (existing != null) {
                errors.add("Duplicate recipe id for internal name '" + recipe.internalName + "' in "
                        + existing.getPath() + " and " + recipe.sourceFile.getPath());
            }
        }
        Map<String, File> coverNameSources = new HashMap<>();
        for (YamlProjectileCoverDefinition cover : projectileCovers) {
            File existing = coverNameSources.putIfAbsent(cover.internalName, cover.sourceFile);
            if (existing != null) {
                errors.add("Duplicate projectile cover id for internal name '" + cover.internalName + "' in "
                        + existing.getPath() + " and " + cover.sourceFile.getPath());
            }
        }
        Map<String, File> projectileNameSources = new HashMap<>();
        for (YamlProjectileDefinition projectile : projectiles) {
            File existing = projectileNameSources.putIfAbsent(projectile.internalName, projectile.sourceFile);
            if (existing != null) {
                errors.add("Duplicate projectile id for internal name '" + projectile.internalName + "' in "
                        + existing.getPath() + " and " + projectile.sourceFile.getPath());
            }
        }
        return errors.isEmpty();
    }

    private static void logErrors(List<String> errors, PrintStream err) {
        err.println("YAML conversion errors:");
        for (String error : errors) {
            err.println("- " + error);
        }
    }

    private static void logWarnings(List<String> warnings, PrintStream out) {
        out.println("YAML conversion warnings:");
        for (String warning : warnings) {
            out.println("- " + warning);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class CliArgs {
        Path pluginRoot;
        Path outputPath;
        String mcVersionRaw;
        boolean restrictToRoots;
        List<String> packRoots;
    }

    private static final class ScanResult {
        final Map<String, PackInfo> packs;
        final List<ResourceCopy> globalAssets;

        ScanResult(Map<String, PackInfo> packs, List<ResourceCopy> globalAssets) {
            this.packs = packs;
            this.globalAssets = globalAssets;
        }
    }

    private static final class ResourceCopy {
        final Path source;
        final String relativePath;

        ResourceCopy(Path source, String relativePath) {
            this.source = source;
            this.relativePath = relativePath;
        }
    }

    private static final class PackInfo {
        final String name;
        final List<ResourceCopy> resources = new ArrayList<>();
        boolean hasYamlDefinition;

        PackInfo(String name) {
            this.name = name;
        }
    }

    private static final class PackPath {
        final String packName;
        final String relativePath;

        PackPath(String packName, String relativePath) {
            this.packName = packName;
            this.relativePath = relativePath;
        }
    }
}
