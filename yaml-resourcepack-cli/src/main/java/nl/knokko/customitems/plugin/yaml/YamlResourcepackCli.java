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
import java.util.stream.Stream;

public final class YamlResourcepackCli {

    private static final String DEFAULT_OUTPUT_NAME = "resource-pack.zip";
    private static final String[] PACK_ROOTS = new String[] { "customitems", "custom-items" };
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

        List<PackSource> packSources;
        try {
            packSources = collectPackSources(resourcesDir, out);
        } catch (DuplicatePackException ex) {
            err.println(ex.getMessage());
            return 1;
        } catch (IOException ex) {
            err.println("Failed to scan resources: " + ex.getMessage());
            return 1;
        }

        if (packSources.isEmpty()) {
            err.println("No YAML packs found under " + resourcesDir);
            return 1;
        }

        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory("kci-yaml-pack-");
        } catch (IOException ex) {
            err.println("Failed to create staging directory: " + ex.getMessage());
            return 1;
        }

        try {
            stageGlobalAssets(resourcesDir, stagingDir, out);
            stagePacks(packSources, stagingDir, out);

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
        return parsed;
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: <pluginRoot> <outputPath> [--mc-version <1.20.4>]");
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

    private static List<PackSource> collectPackSources(Path resourcesDir, PrintStream out) throws IOException {
        Map<String, Path> packNames = new LinkedHashMap<>();
        List<PackSource> result = new ArrayList<>();

        boolean foundRoot = false;
        for (String rootName : PACK_ROOTS) {
            Path root = resourcesDir.resolve(rootName);
            if (!Files.isDirectory(root)) continue;
            foundRoot = true;
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory).forEach(packDir -> {
                    String packName = packDir.getFileName().toString();
                    if (!isSafePackName(packName)) return;
                    if (!containsYamlDefinitionQuiet(packDir)) return;
                    Path existing = packNames.putIfAbsent(packName, packDir);
                    if (existing != null) {
                        throw new DuplicatePackException(packName, existing, packDir);
                    }
                    result.add(new PackSource(packName, packDir));
                });
            }
        }

        try (Stream<Path> stream = Files.list(resourcesDir)) {
            stream.filter(Files::isDirectory).forEach(rootDir -> {
                String dirName = rootDir.getFileName().toString();
                if (isReservedRoot(dirName)) return;
                if (!containsYamlDefinitionQuiet(rootDir)) return;
                if (!isSafePackName(dirName)) return;
                Path existing = packNames.putIfAbsent(dirName, rootDir);
                if (existing != null) {
                    throw new DuplicatePackException(dirName, existing, rootDir);
                }
                result.add(new PackSource(dirName, rootDir));
            });
        }

        if (result.isEmpty()) {
            if (foundRoot) {
                out.println("No YAML packs with definitions found under " + resourcesDir + ".");
            }
            return result;
        }

        out.println("Found " + result.size() + " YAML pack(s).");
        return result;
    }

    private static boolean isReservedRoot(String name) {
        if (name == null) return true;
        for (String root : PACK_ROOTS) {
            if (root.equalsIgnoreCase(name)) return true;
        }
        return "assets".equalsIgnoreCase(name);
    }

    private static boolean isSafePackName(String packName) {
        if (packName == null || packName.isEmpty()) return false;
        return !(packName.contains("..") || packName.contains("/") || packName.contains("\\")
                || packName.contains(":") || packName.startsWith("."));
    }

    private static boolean containsYamlDefinitionQuiet(Path dir) {
        try {
            return containsYamlDefinition(dir);
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean containsYamlDefinition(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(YamlResourcepackCli::isYamlFile)
                    .anyMatch(path -> {
                        try {
                            return looksLikeYamlDefinition(path);
                        } catch (IOException ex) {
                            return false;
                        }
                    });
        }
    }

    private static boolean isYamlFile(Path path) {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
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

    private static void stageGlobalAssets(Path resourcesDir, Path stagingDir, PrintStream out) throws IOException {
        Path assetsTarget = stagingDir.resolve("assets");
        boolean copied = false;

        Path rootAssets = resourcesDir.resolve("assets");
        if (Files.isDirectory(rootAssets)) {
            copyDirectory(rootAssets, assetsTarget);
            copied = true;
        }

        for (String root : PACK_ROOTS) {
            Path assets = resourcesDir.resolve(root).resolve("assets");
            if (Files.isDirectory(assets)) {
                copyDirectory(assets, assetsTarget);
                copied = true;
            }
        }

        if (copied) {
            out.println("Staged global assets.");
        }
    }

    private static void stagePacks(List<PackSource> sources, Path stagingDir, PrintStream out) throws IOException {
        for (PackSource source : sources) {
            Path target = stagingDir.resolve(source.name);
            copyDirectory(source.sourceDir, target);
        }
        out.println("Staged " + sources.size() + " pack(s).");
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (!Files.isDirectory(source)) return;

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path targetDir = target.resolve(relative);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path targetFile = target.resolve(relative);
                Files.createDirectories(targetFile.getParent());
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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
    }

    private static final class PackSource {
        final String name;
        final Path sourceDir;

        PackSource(String name, Path sourceDir) {
            this.name = name;
            this.sourceDir = sourceDir;
        }
    }

    private static final class DuplicatePackException extends RuntimeException {
        DuplicatePackException(String packName, Path first, Path second) {
            super("Duplicate pack name '" + packName + "' from " + first + " and " + second);
        }
    }
}
