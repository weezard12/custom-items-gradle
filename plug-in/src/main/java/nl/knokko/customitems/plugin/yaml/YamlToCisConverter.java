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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class YamlToCisConverter {

    private YamlToCisConverter() {}

    public static boolean convertIfNeeded(File dataFolder, Consumer<String> log) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<YamlItemDefinition> items = new ArrayList<>();
        List<YamlBlockDefinition> blocks = new ArrayList<>();
        List<YamlRecipeDefinition> recipes = new ArrayList<>();
        List<YamlProjectileCoverDefinition> projectileCovers = new ArrayList<>();
        List<YamlProjectileDefinition> projectiles = new ArrayList<>();

        File[] packDirs = dataFolder.listFiles(File::isDirectory);
        if (packDirs == null) return false;

        int packCount = 0;
        for (File packDir : packDirs) {
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
            logWarnings(warnings, log);
        }

        if (!errors.isEmpty()) {
            logErrors(errors, log);
            log.accept(ChatColor.RED + "YAML conversion failed; using existing items.cis.txt if present.");
            return false;
        }

        if (items.isEmpty() && blocks.isEmpty() && recipes.isEmpty()
                && projectileCovers.isEmpty() && projectiles.isEmpty()) return false;

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

        if (!errors.isEmpty()) {
            logErrors(errors, log);
            log.accept(ChatColor.RED + "YAML conversion failed; using existing items.cis.txt if present.");
            return false;
        }

        Collections.sort(items, Comparator.comparing(item -> item.internalName));
        Collections.sort(blocks, Comparator.comparing(block -> block.internalName));
        Collections.sort(recipes, Comparator.comparing(recipe -> recipe.internalName));
        Collections.sort(projectileCovers, Comparator.comparing(cover -> cover.internalName));
        Collections.sort(projectiles, Comparator.comparing(projectile -> projectile.internalName));
        ItemSet itemSet;
        try {
            itemSet = YamlItemSetBuilder.build(items, blocks, recipes, projectileCovers, projectiles);
        } catch (ValidationException | ProgrammingValidationException ex) {
            log.accept(ChatColor.RED + "YAML conversion failed: " + ex.getMessage());
            return false;
        }

        try {
            File resourcePackFile = new File(dataFolder, "resource-pack.zip");
            YamlResourcepackGenerator.write(itemSet, resourcePackFile);
        } catch (ValidationException | ProgrammingValidationException ex) {
            log.accept(ChatColor.RED + "YAML resource pack generation failed: " + ex.getMessage());
            return false;
        } catch (IOException ex) {
            log.accept(ChatColor.RED + "Failed to write resource-pack.zip: " + ex.getMessage());
            return false;
        }

        try {
            ByteArrayBitOutput output = YamlItemSetBuilder.buildBinary(itemSet);
            writeTextyFile(dataFolder, output);
            log.accept(ChatColor.GREEN + "Converted " + items.size() + " item(s), " + blocks.size()
                    + " block(s), " + recipes.size() + " recipe(s), " + projectileCovers.size()
                    + " projectile cover(s), and " + projectiles.size() + " projectile(s) from "
                    + packCount + " pack(s) and generated resource-pack.zip.");
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

    private static void logErrors(List<String> errors, Consumer<String> log) {
        log.accept(ChatColor.RED + "YAML conversion errors:");
        for (String error : errors) {
            log.accept(ChatColor.RED + "- " + error);
        }
    }

    private static void logWarnings(List<String> warnings, Consumer<String> log) {
        log.accept(ChatColor.YELLOW + "YAML conversion warnings:");
        for (String warning : warnings) {
            log.accept(ChatColor.YELLOW + "- " + warning);
        }
    }
}
