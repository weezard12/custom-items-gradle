package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.itemset.ItemSet;
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
        List<YamlItemDefinition> items = new ArrayList<>();

        File[] packDirs = dataFolder.listFiles(File::isDirectory);
        if (packDirs == null) return false;

        int packCount = 0;
        for (File packDir : packDirs) {
            YamlPackDefinition pack = YamlPackReader.readPack(packDir, errors);
            if (pack == null) continue;
            List<YamlItemDefinition> packItems = YamlItemReader.readItems(pack, errors);
            if (!packItems.isEmpty()) {
                packCount++;
                items.addAll(packItems);
            }
        }

        if (!errors.isEmpty()) {
            logErrors(errors, log);
            log.accept(ChatColor.RED + "YAML conversion failed; using existing items.cis.txt if present.");
            return false;
        }

        if (items.isEmpty()) return false;

        Map<String, File> internalNameSources = new HashMap<>();
        for (YamlItemDefinition item : items) {
            File existing = internalNameSources.putIfAbsent(item.internalName, item.sourceFile);
            if (existing != null) {
                errors.add("Duplicate item id for internal name '" + item.internalName + "' in "
                        + existing.getPath() + " and " + item.sourceFile.getPath());
            }
        }

        if (!errors.isEmpty()) {
            logErrors(errors, log);
            log.accept(ChatColor.RED + "YAML conversion failed; using existing items.cis.txt if present.");
            return false;
        }

        try {
            Collections.sort(items, Comparator.comparing(item -> item.internalName));
            ItemSet itemSet = YamlItemSetBuilder.build(items);
            ByteArrayBitOutput output = YamlItemSetBuilder.buildBinary(itemSet);
            writeTextyFile(dataFolder, output);
            log.accept(ChatColor.GREEN + "Converted " + items.size() + " item(s) from " + packCount + " pack(s).");
            return true;
        } catch (ValidationException | ProgrammingValidationException ex) {
            log.accept(ChatColor.RED + "YAML conversion failed: " + ex.getMessage());
        } catch (IOException ex) {
            log.accept(ChatColor.RED + "Failed to write items.cis.txt: " + ex.getMessage());
        }

        return false;
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
}
