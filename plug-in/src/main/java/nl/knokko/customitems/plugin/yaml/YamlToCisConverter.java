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

    private YamlToCisConverter() {}

    public static boolean convertIfNeeded(File dataFolder, Consumer<String> log) {
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

        try {
            File resourcePackFile = new File(dataFolder, "resource-pack.zip");
            YamlResourcepackGenerator.write(buildResult.itemSet, resourcePackFile);
        } catch (ValidationException | ProgrammingValidationException ex) {
            log.accept(ChatColor.RED + "YAML resource pack generation failed: " + ex.getMessage());
            return false;
        } catch (IOException ex) {
            log.accept(ChatColor.RED + "Failed to write resource-pack.zip: " + ex.getMessage());
            return false;
        }

        try {
            ByteArrayBitOutput output = YamlItemSetBuilder.buildBinary(buildResult.itemSet);
            writeTextyFile(dataFolder, output);
            log.accept(ChatColor.GREEN + "Converted " + buildResult.collections.items.size() + " item(s), "
                    + buildResult.collections.blocks.size() + " block(s), "
                    + buildResult.collections.recipes.size() + " recipe(s), "
                    + buildResult.collections.projectileCovers.size() + " projectile cover(s), and "
                    + buildResult.collections.projectiles.size() + " projectile(s) from "
                    + buildResult.packCount + " pack(s) and generated resource-pack.zip.");
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
                ItemSet itemSet = YamlItemSetBuilder.build(
                        collections.items,
                        collections.blocks,
                        collections.recipes,
                        collections.projectileCovers,
                        collections.projectiles
                );
                return new BuildResult(itemSet, collections, activePacks.size());
            } catch (ValidationException | ProgrammingValidationException ex) {
                PackContent culprit = findCulpritPack(ex.getMessage(), activePacks);
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

    private static List<PackContent> collectNonConflictingPacks(List<PackContent> parsedPacks, Consumer<String> log) {
        List<PackContent> accepted = new ArrayList<>();

        Map<String, File> itemNameSources = new HashMap<>();
        Map<String, File> blockNameSources = new HashMap<>();
        Map<String, File> recipeNameSources = new HashMap<>();
        Map<String, File> coverNameSources = new HashMap<>();
        Map<String, File> projectileNameSources = new HashMap<>();

        for (PackContent pack : parsedPacks) {
            List<String> conflicts = new ArrayList<>();

            collectInternalNameConflicts(
                    "item", pack.items, itemNameSources, conflicts,
                    item -> item.internalName, item -> item.sourceFile
            );
            collectInternalNameConflicts(
                    "block", pack.blocks, blockNameSources, conflicts,
                    block -> block.internalName, block -> block.sourceFile
            );
            collectInternalNameConflicts(
                    "recipe", pack.recipes, recipeNameSources, conflicts,
                    recipe -> recipe.internalName, recipe -> recipe.sourceFile
            );
            collectInternalNameConflicts(
                    "projectile cover", pack.projectileCovers, coverNameSources, conflicts,
                    cover -> cover.internalName, cover -> cover.sourceFile
            );
            collectInternalNameConflicts(
                    "projectile", pack.projectiles, projectileNameSources, conflicts,
                    projectile -> projectile.internalName, projectile -> projectile.sourceFile
            );

            if (!conflicts.isEmpty()) {
                logPackErrors(pack.directory, conflicts, log);
                log.accept(ChatColor.RED + "Skipping pack '" + pack.directory.getName() + "' due to ID conflicts.");
                continue;
            }

            registerInternalNames(pack.items, itemNameSources, item -> item.internalName, item -> item.sourceFile);
            registerInternalNames(pack.blocks, blockNameSources, block -> block.internalName, block -> block.sourceFile);
            registerInternalNames(pack.recipes, recipeNameSources, recipe -> recipe.internalName, recipe -> recipe.sourceFile);
            registerInternalNames(
                    pack.projectileCovers, coverNameSources, cover -> cover.internalName, cover -> cover.sourceFile
            );
            registerInternalNames(
                    pack.projectiles, projectileNameSources, projectile -> projectile.internalName, projectile -> projectile.sourceFile
            );

            accepted.add(pack);
        }

        return accepted;
    }

    private static <T> void collectInternalNameConflicts(
            String idType,
            List<T> definitions,
            Map<String, File> knownSources,
            List<String> conflicts,
            Function<T, String> nameGetter,
            Function<T, File> sourceGetter
    ) {
        Map<String, File> localSources = new HashMap<>();
        for (T definition : definitions) {
            String internalName = nameGetter.apply(definition);
            File sourceFile = sourceGetter.apply(definition);

            File localExisting = localSources.putIfAbsent(internalName, sourceFile);
            if (localExisting != null) {
                conflicts.add("Duplicate " + idType + " id for internal name '" + internalName + "' in "
                        + localExisting.getPath() + " and " + sourceFile.getPath());
                continue;
            }

            File existing = knownSources.get(internalName);
            if (existing != null) {
                conflicts.add("Duplicate " + idType + " id for internal name '" + internalName + "' in "
                        + existing.getPath() + " and " + sourceFile.getPath());
            }
        }
    }

    private static <T> void registerInternalNames(
            List<T> definitions,
            Map<String, File> knownSources,
            Function<T, String> nameGetter,
            Function<T, File> sourceGetter
    ) {
        for (T definition : definitions) {
            knownSources.put(nameGetter.apply(definition), sourceGetter.apply(definition));
        }
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
