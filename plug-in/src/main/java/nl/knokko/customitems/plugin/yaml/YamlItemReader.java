package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.Validation;
import nl.knokko.customitems.util.ValidationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

class YamlItemReader {

    static List<YamlItemDefinition> readItems(YamlPackDefinition pack, List<String> errors) {
        List<YamlItemDefinition> items = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(pack.directory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) return;
                if (fileName.equals("pack.yml")) return;

                File file = path.toFile();
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection itemSection = config.getConfigurationSection("item");
                if (itemSection == null) return;

                String rawId = itemSection.getString("id");
                String name = itemSection.getString("name");

                if (rawId == null || rawId.trim().isEmpty()) {
                    errors.add("Missing item.id in " + file.getPath());
                    return;
                }
                if (name == null || name.trim().isEmpty()) {
                    errors.add("Missing item.name in " + file.getPath());
                    return;
                }

                ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors);
                if (parsedId == null) return;

                items.add(new YamlItemDefinition(parsedId.fullId, parsedId.internalName, name, file));
            });
        } catch (IOException ex) {
            errors.add("Failed to scan pack folder " + pack.directory.getPath() + ": " + ex.getMessage());
        }

        return items;
    }

    private static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors
    ) {
        String namespace;
        String name;

        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            if (rawId.indexOf(':', colonIndex + 1) >= 0) {
                errors.add("Invalid item.id '" + rawId + "' in " + sourceFile.getPath() + ": too many ':' characters");
                return null;
            }
            namespace = rawId.substring(0, colonIndex);
            name = rawId.substring(colonIndex + 1);
        } else {
            namespace = defaultNamespace;
            name = rawId;
        }

        if (namespace == null || namespace.isEmpty()) {
            errors.add("Missing namespace for item.id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }
        if (name.isEmpty()) {
            errors.add("Missing name for item.id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            Validation.safeName(namespace);
            Validation.safeName(name);
        } catch (ValidationException | ProgrammingValidationException ex) {
            errors.add("Invalid item.id '" + rawId + "' in " + sourceFile.getPath() + ": " + ex.getMessage());
            return null;
        }

        String fullId = namespace + ":" + name;
        String internalName = namespace + "_" + name;
        return new ParsedId(fullId, internalName);
    }

    private static class ParsedId {

        final String fullId;
        final String internalName;

        ParsedId(String fullId, String internalName) {
            this.fullId = fullId;
            this.internalName = internalName;
        }
    }
}
