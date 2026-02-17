package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.plugin.resourcepack.YamlResourcepackGenerator;
import nl.knokko.customitems.util.StringEncoder;
import nl.knokko.customitems.util.ValidationException;
import nl.knokko.customitems.util.ProgrammingValidationException;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class YamlToCisConverter {

    private static volatile Map<String, String> yamlBlockIdSnapshot = Collections.emptyMap();

    private YamlToCisConverter() {}

    public static Map<String, String> getYamlBlockIdSnapshot() {
        return yamlBlockIdSnapshot;
    }

    public static String getYamlBlockId(String blockInternalName) {
        if (blockInternalName == null) return null;
        return yamlBlockIdSnapshot.get(blockInternalName);
    }

    private static void resetYamlBlockIdSnapshot() {
        yamlBlockIdSnapshot = Collections.emptyMap();
    }

    private static void updateYamlBlockIdSnapshot(DefinitionCollections collections) {
        Map<String, String> mapping = new HashMap<>(collections.blocks.size());
        for (YamlBlockDefinition block : collections.blocks) {
            mapping.put(block.internalName, block.fullId);
        }
        yamlBlockIdSnapshot = Collections.unmodifiableMap(mapping);
    }

    public static boolean convertIfNeeded(File dataFolder, Consumer<String> log) {
        return convertIfNeeded(dataFolder, log, true);
    }

    public static boolean convertIfNeeded(
            File dataFolder, Consumer<String> log, boolean generateRuntimeResourcePack
    ) {
        resetYamlBlockIdSnapshot();

        File[] packDirs = dataFolder.listFiles(File::isDirectory);
        if (packDirs == null) return false;
        Arrays.sort(packDirs, Comparator.comparing(
                pack -> pack.getName().toLowerCase(Locale.ROOT)
        ));

        List<PackContent> parsedPacks = new ArrayList<>();
        for (File packDir : packDirs) {
            PackContent parsed = parsePack(packDir, log);
            if (parsed != null) parsedPacks.add(parsed);
        }
        if (parsedPacks.isEmpty()) return false;

        BuildResult buildResult = buildItemSet(parsedPacks, log);
        if (buildResult == null) {
            log.accept(ChatColor.RED + "YAML conversion failed; using existing items.cis.txt if present.");
            return false;
        }

        boolean didGenerateResourcePack = false;
        if (generateRuntimeResourcePack) {
            try {
                File resourcePackFile = new File(dataFolder, "resource-pack.zip");
                YamlResourcepackGenerator.write(buildResult.itemSet, resourcePackFile);
                didGenerateResourcePack = true;
            } catch (ValidationException | ProgrammingValidationException ex) {
                log.accept(ChatColor.RED + "YAML resource pack generation failed: " + ex.getMessage());
                return false;
            } catch (IOException ex) {
                log.accept(ChatColor.RED + "Failed to write resource-pack.zip: " + ex.getMessage());
                return false;
            }
        }

        try {
            ByteArrayBitOutput output = YamlItemSetBuilder.buildBinary(buildResult.itemSet);
            writeTextyFile(dataFolder, output);
            updateYamlBlockIdSnapshot(buildResult.collections);
            String resourcePackMessage;
            if (didGenerateResourcePack) resourcePackMessage = " and generated resource-pack.zip.";
            else resourcePackMessage = " and skipped resource-pack.zip generation because it is disabled in config.";
            log.accept(ChatColor.GREEN + "Converted " + buildResult.collections.items.size() + " item(s), "
                    + buildResult.collections.blocks.size() + " block(s), "
                    + buildResult.collections.recipes.size() + " recipe(s), "
                    + buildResult.collections.projectileCovers.size() + " projectile cover(s), and "
                    + buildResult.collections.projectiles.size() + " projectile(s) from "
                    + buildResult.packCount + " pack(s)" + resourcePackMessage);
            return true;
        } catch (IOException ex) {
            log.accept(ChatColor.RED + "Failed to write items.cis.txt: " + ex.getMessage());
            return false;
        }
    }

    private static void writeTextyFile(File dataFolder, ByteArrayBitOutput output) throws IOException {
        byte[] textBytes = StringEncoder.encodeTextyBytes(output.getBytes(), true);
        File targetFile = new File(dataFolder, "items.cis.txt");
        File tempFile = new File(dataFolder, "items.cis.txt.tmp");
        Files.write(tempFile.toPath(), textBytes);
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static PackContent parsePack(File packDir, Consumer<String> log) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        YamlPackDefinition pack = YamlPackReader.readPack(packDir, errors);
        if (pack == null) {
            logPackErrors(packDir, errors, log);
            return null;
        }

        List<YamlItemDefinition> items = YamlItemReader.readItems(pack, errors, warnings);
        List<YamlBlockDefinition> blocks = YamlBlockReader.readBlocks(pack, errors, warnings);
        List<YamlRecipeDefinition> recipes = YamlRecipeReader.readRecipes(pack, errors, warnings);
        List<YamlProjectileCoverDefinition> projectileCovers = YamlProjectileCoverReader.readProjectileCovers(
                pack, errors, warnings
        );
        List<YamlProjectileDefinition> projectiles = YamlProjectileReader.readProjectiles(pack, errors, warnings);

        logPackWarnings(packDir, warnings, log);

        if (!errors.isEmpty()) {
            logPackErrors(packDir, errors, log);
            log.accept(ChatColor.RED + "Skipping pack '" + packDir.getName() + "' due to YAML errors.");
            return null;
        }
        if (items.isEmpty() && blocks.isEmpty() && recipes.isEmpty()
                && projectileCovers.isEmpty() && projectiles.isEmpty()) {
            return null;
        }

        return new PackContent(packDir, items, blocks, recipes, projectileCovers, projectiles);
    }

    private static BuildResult buildItemSet(List<PackContent> parsedPacks, Consumer<String> log) {
        List<PackContent> activePacks = collectNonConflictingPacks(parsedPacks, log);
        if (activePacks.isEmpty()) {
            log.accept(ChatColor.RED + "YAML conversion failed: no valid packs remain.");
            return null;
        }

        while (!activePacks.isEmpty()) {
            DefinitionCollections collections = mergeDefinitions(activePacks);
            try {
                int mcVersion = YamlVersionContext.mcVersion;
                ItemSet itemSet = YamlItemSetBuilder.build(
                        collections.items,
                        collections.blocks,
                        collections.recipes,
                        collections.projectileCovers,
                        collections.projectiles,
                        mcVersion
                );
                return new BuildResult(itemSet, collections, activePacks.size());
            } catch (ValidationException | ProgrammingValidationException ex) {
                PackContent culprit = findCulpritPack(ex.getMessage(), activePacks);
                if (culprit == null) {
                    culprit = findCulpritPackByIsolation(activePacks);
                }
                if (culprit == null) {
                    log.accept(ChatColor.RED + "YAML conversion failed: " + ex.getMessage());
                    return null;
                }
                log.accept(ChatColor.RED + "Skipping pack '" + culprit.directory.getName()
                        + "' due to conversion error: " + ex.getMessage());
                activePacks.remove(culprit);
            }
        }

        log.accept(ChatColor.RED + "YAML conversion failed: no valid packs remain.");
        return null;
    }

    private static PackContent findCulpritPackByIsolation(List<PackContent> packs) {
        if (packs.isEmpty()) return null;
        if (packs.size() == 1) return packs.get(0);

        for (PackContent candidate : packs) {
            if (canBuildWithoutPack(packs, candidate)) return candidate;
        }

        for (PackContent candidate : packs) {
            if (!canBuildSinglePack(candidate)) return candidate;
        }

        return packs.get(0);
    }

    private static boolean canBuildWithoutPack(List<PackContent> packs, PackContent excluded) {
        List<PackContent> reduced = new ArrayList<>(packs.size() - 1);
        for (PackContent pack : packs) {
            if (pack != excluded) reduced.add(pack);
        }
        return canBuildPacks(reduced);
    }

    private static boolean canBuildSinglePack(PackContent pack) {
        List<PackContent> singleton = new ArrayList<>(1);
        singleton.add(pack);
        return canBuildPacks(singleton);
    }

    private static boolean canBuildPacks(List<PackContent> packs) {
        if (packs.isEmpty()) return true;
        DefinitionCollections collections = mergeDefinitions(packs);
        try {
            YamlItemSetBuilder.build(
                    collections.items,
                    collections.blocks,
                    collections.recipes,
                    collections.projectileCovers,
                    collections.projectiles,
                    YamlVersionContext.mcVersion
            );
            return true;
        } catch (ValidationException | ProgrammingValidationException ignored) {
            return false;
        }
    }

    private static List<PackContent> collectNonConflictingPacks(List<PackContent> parsedPacks, Consumer<String> log) {
        List<PackContent> accepted = new ArrayList<>();

        Map<String, File> itemNameSources = new HashMap<>();
        Map<String, File> blockNameSources = new HashMap<>();
        Map<String, File> recipeNameSources = new HashMap<>();
        Map<String, File> coverNameSources = new HashMap<>();
        Map<String, File> projectileNameSources = new HashMap<>();

        for (PackContent pack : parsedPacks) {
            List<String> duplicateWarnings = new ArrayList<>();

            List<YamlItemDefinition> filteredItems = filterDuplicateInternalNames(
                    "item", pack.items, itemNameSources, duplicateWarnings,
                    item -> item.internalName, item -> item.sourceFile
            );
            List<YamlBlockDefinition> filteredBlocks = filterDuplicateInternalNames(
                    "block", pack.blocks, blockNameSources, duplicateWarnings,
                    block -> block.internalName, block -> block.sourceFile
            );
            List<YamlRecipeDefinition> filteredRecipes = filterDuplicateInternalNames(
                    "recipe", pack.recipes, recipeNameSources, duplicateWarnings,
                    recipe -> recipe.internalName, recipe -> recipe.sourceFile
            );
            List<YamlProjectileCoverDefinition> filteredCovers = filterDuplicateInternalNames(
                    "projectile cover", pack.projectileCovers, coverNameSources, duplicateWarnings,
                    cover -> cover.internalName, cover -> cover.sourceFile
            );
            List<YamlProjectileDefinition> filteredProjectiles = filterDuplicateInternalNames(
                    "projectile", pack.projectiles, projectileNameSources, duplicateWarnings,
                    projectile -> projectile.internalName, projectile -> projectile.sourceFile
            );

            if (!duplicateWarnings.isEmpty()) {
                logPackWarnings(pack.directory, duplicateWarnings, log);
            }

            if (filteredItems.isEmpty() && filteredBlocks.isEmpty() && filteredRecipes.isEmpty()
                    && filteredCovers.isEmpty() && filteredProjectiles.isEmpty()) {
                continue;
            }

            accepted.add(new PackContent(
                    pack.directory,
                    filteredItems,
                    filteredBlocks,
                    filteredRecipes,
                    filteredCovers,
                    filteredProjectiles
            ));
        }

        return accepted;
    }

    private static <T> List<T> filterDuplicateInternalNames(
            String idType,
            List<T> definitions,
            Map<String, File> knownSources,
            List<String> warnings,
            Function<T, String> nameGetter,
            Function<T, File> sourceGetter
    ) {
        Map<String, File> localSources = new HashMap<>();
        List<T> filteredDefinitions = new ArrayList<>(definitions.size());
        for (T definition : definitions) {
            String internalName = nameGetter.apply(definition);
            File sourceFile = sourceGetter.apply(definition);

            File localExisting = localSources.get(internalName);
            if (localExisting != null) {
                warnings.add("Duplicate " + idType + " id for internal name '" + internalName + "' in "
                        + localExisting.getPath() + " and " + sourceFile.getPath()
                        + "; skipping duplicate declaration.");
                continue;
            }

            File existing = knownSources.get(internalName);
            if (existing != null) {
                warnings.add("Duplicate " + idType + " id for internal name '" + internalName + "' in "
                        + existing.getPath() + " and " + sourceFile.getPath()
                        + "; skipping duplicate declaration.");
                continue;
            }

            filteredDefinitions.add(definition);
            localSources.put(internalName, sourceFile);
            knownSources.put(internalName, sourceFile);
        }
        return filteredDefinitions;
    }

    private static DefinitionCollections mergeDefinitions(List<PackContent> packs) {
        DefinitionCollections collections = new DefinitionCollections();
        for (PackContent pack : packs) {
            collections.items.addAll(pack.items);
            collections.blocks.addAll(pack.blocks);
            collections.recipes.addAll(pack.recipes);
            collections.projectileCovers.addAll(pack.projectileCovers);
            collections.projectiles.addAll(pack.projectiles);
        }

        Collections.sort(collections.items, Comparator.comparing(item -> item.internalName));
        Collections.sort(collections.blocks, Comparator.comparing(block -> block.internalName));
        Collections.sort(collections.recipes, Comparator.comparing(recipe -> recipe.internalName));
        Collections.sort(collections.projectileCovers, Comparator.comparing(cover -> cover.internalName));
        Collections.sort(collections.projectiles, Comparator.comparing(projectile -> projectile.internalName));
        return collections;
    }

    private static PackContent findCulpritPack(String errorMessage, List<PackContent> packs) {
        if (errorMessage == null || errorMessage.isEmpty()) return null;
        String normalizedMessage = normalizePath(errorMessage);
        for (PackContent pack : packs) {
            String normalizedPackPath = normalizePath(pack.directory.getPath());
            if (normalizedMessage.contains(normalizedPackPath)) {
                return pack;
            }
            String packNameMarker = "/" + pack.directory.getName().toLowerCase(Locale.ROOT) + "/";
            if (normalizedMessage.contains(packNameMarker)) {
                return pack;
            }
        }
        return null;
    }

    private static String normalizePath(String path) {
        return path.toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private static void logPackErrors(File packDir, List<String> errors, Consumer<String> log) {
        if (errors.isEmpty()) return;
        log.accept(ChatColor.RED + "YAML conversion errors in pack '" + packDir.getName() + "':");
        for (String error : errors) {
            log.accept(ChatColor.RED + "- " + error);
        }
    }

    private static void logPackWarnings(File packDir, List<String> warnings, Consumer<String> log) {
        if (warnings.isEmpty()) return;
        log.accept(ChatColor.YELLOW + "YAML conversion warnings in pack '" + packDir.getName() + "':");
        for (String warning : warnings) {
            log.accept(ChatColor.YELLOW + "- " + warning);
        }
    }

    private static class PackContent {

        final File directory;
        final List<YamlItemDefinition> items;
        final List<YamlBlockDefinition> blocks;
        final List<YamlRecipeDefinition> recipes;
        final List<YamlProjectileCoverDefinition> projectileCovers;
        final List<YamlProjectileDefinition> projectiles;

        PackContent(
                File directory,
                List<YamlItemDefinition> items,
                List<YamlBlockDefinition> blocks,
                List<YamlRecipeDefinition> recipes,
                List<YamlProjectileCoverDefinition> projectileCovers,
                List<YamlProjectileDefinition> projectiles
        ) {
            this.directory = directory;
            this.items = items;
            this.blocks = blocks;
            this.recipes = recipes;
            this.projectileCovers = projectileCovers;
            this.projectiles = projectiles;
        }
    }

    private static class DefinitionCollections {

        final List<YamlItemDefinition> items = new ArrayList<>();
        final List<YamlBlockDefinition> blocks = new ArrayList<>();
        final List<YamlRecipeDefinition> recipes = new ArrayList<>();
        final List<YamlProjectileCoverDefinition> projectileCovers = new ArrayList<>();
        final List<YamlProjectileDefinition> projectiles = new ArrayList<>();
    }

    private static class BuildResult {

        final ItemSet itemSet;
        final DefinitionCollections collections;
        final int packCount;

        BuildResult(ItemSet itemSet, DefinitionCollections collections, int packCount) {
            this.itemSet = itemSet;
            this.collections = collections;
            this.packCount = packCount;
        }
    }
}
