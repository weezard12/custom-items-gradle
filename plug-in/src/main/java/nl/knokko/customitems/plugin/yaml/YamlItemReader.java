package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.item.enchantment.VEnchantmentType;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

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

                int errorCountBefore = errors.size();

                String displayName = translateColors(name, "name", file, errors);
                List<String> lore = parseLore(itemSection.get("lore"), file, errors);
                YamlMaterialDefinition material = parseMaterial(itemSection.get("material"), file, errors);
                List<YamlEnchantmentDefinition> enchantments = parseEnchantments(itemSection.get("enchantments"), file, errors);
                Integer stackSize = parseInteger(itemSection.get("stack_size"), 1, 64, "stack_size", file, errors);
                Integer damageValue = parseInteger(itemSection.get("damage_value"), 0, Short.MAX_VALUE, "damage_value", file, errors);
                Boolean unbreakable = parseBoolean(itemSection.get("unbreakable"), "unbreakable", file, errors);
                Double attackDamage = parseDouble(itemSection.get("attack_damage"), "attack_damage", file, errors);
                Double attackSpeed = parseDouble(itemSection.get("attack_speed"), "attack_speed", file, errors);

                if (errors.size() != errorCountBefore) return;

                items.add(new YamlItemDefinition(
                        parsedId.fullId,
                        parsedId.internalName,
                        displayName,
                        file,
                        lore,
                        material,
                        enchantments,
                        stackSize,
                        damageValue,
                        unbreakable,
                        attackDamage,
                        attackSpeed
                ));
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

    private static YamlMaterialDefinition parseMaterial(Object rawMaterial, File sourceFile, List<String> errors) {
        if (rawMaterial == null) return null;
        if (!(rawMaterial instanceof String)) {
            errors.add("item.material must be a string in " + sourceFile.getPath());
            return null;
        }

        String raw = ((String) rawMaterial).trim();
        if (raw.isEmpty()) return null;

        String normalized = normalizeNamespacedValue(raw);
        if (normalized == null) {
            errors.add("Invalid item.material '" + raw + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            KciItemType itemType = KciItemType.valueOf(normalized);
            if (mcVersion < itemType.firstVersion || mcVersion > itemType.lastVersion) {
                errors.add("item.material '" + raw + "' is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return new YamlMaterialDefinition(itemType, null);
        } catch (IllegalArgumentException notItemType) {
            // Try VMaterial
        }

        try {
            VMaterial material = VMaterial.valueOf(normalized);
            if (mcVersion < material.firstVersion || mcVersion > material.lastVersion) {
                errors.add("item.material '" + raw + "' is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            if (mcVersion < MCVersions.VERSION1_14) {
                errors.add("item.material '" + raw + "' requires MC 1.14+ (" + sourceFile.getPath() + ")");
                return null;
            }
            return new YamlMaterialDefinition(KciItemType.OTHER, material);
        } catch (IllegalArgumentException notMaterial) {
            errors.add("Unknown item.material '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static List<YamlEnchantmentDefinition> parseEnchantments(
            Object rawEnchantments, File sourceFile, List<String> errors
    ) {
        if (rawEnchantments == null) return Collections.emptyList();
        if (!(rawEnchantments instanceof List<?>)) {
            errors.add("item.enchantments must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlEnchantmentDefinition> result = new ArrayList<>();
        for (Object entry : (List<?>) rawEnchantments) {
            ParsedEnchantment parsed = parseEnchantmentEntry(entry, sourceFile, errors);
            if (parsed != null) {
                result.add(new YamlEnchantmentDefinition(parsed.type, parsed.level));
            }
        }

        return result;
    }

    private static ParsedEnchantment parseEnchantmentEntry(
            Object entry, File sourceFile, List<String> errors
    ) {
        if (entry instanceof String) {
            return parseEnchantmentString((String) entry, sourceFile, errors);
        }

        if (entry instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) entry;
            Object idValue = map.get("id");
            Object levelValue = map.get("level");
            if (idValue == null) {
                errors.add("item.enchantments entry is missing id in " + sourceFile.getPath());
                return null;
            }
            if (!(idValue instanceof String)) {
                errors.add("item.enchantments id must be a string in " + sourceFile.getPath());
                return null;
            }
            String id = ((String) idValue).trim();
            if (id.isEmpty()) {
                errors.add("item.enchantments id is empty in " + sourceFile.getPath());
                return null;
            }
            int level = 1;
            if (levelValue != null) {
                Integer parsedLevel = parseInteger(levelValue, 1, Integer.MAX_VALUE, "enchantments.level", sourceFile, errors);
                if (parsedLevel == null) return null;
                level = parsedLevel;
            }
            VEnchantmentType type = parseEnchantmentType(id);
            if (type == null) {
                errors.add("Unknown enchantment id '" + id + "' in " + sourceFile.getPath());
                return null;
            }
            return new ParsedEnchantment(type, level);
        }

        errors.add("item.enchantments entry must be a string or map in " + sourceFile.getPath());
        return null;
    }

    private static ParsedEnchantment parseEnchantmentString(
            String raw, File sourceFile, List<String> errors
    ) {
        String value = raw.trim();
        if (value.isEmpty()) {
            errors.add("item.enchantments entry is empty in " + sourceFile.getPath());
            return null;
        }

        String id = value;
        Integer level = null;
        int lastColon = value.lastIndexOf(':');
        if (lastColon > 0 && lastColon + 1 < value.length()) {
            String maybeLevel = value.substring(lastColon + 1);
            if (isInteger(maybeLevel)) {
                id = value.substring(0, lastColon);
                level = Integer.parseInt(maybeLevel);
            }
        }

        VEnchantmentType type = parseEnchantmentType(id);
        if (type == null) {
            errors.add("Unknown enchantment id '" + id + "' in " + sourceFile.getPath());
            return null;
        }

        int finalLevel = level != null ? level : 1;
        if (finalLevel < 1) {
            errors.add("enchantment level must be positive in " + sourceFile.getPath());
            return null;
        }

        return new ParsedEnchantment(type, finalLevel);
    }

    private static VEnchantmentType parseEnchantmentType(String rawId) {
        String normalized = normalizeNamespacedValue(rawId);
        if (normalized == null) return null;

        try {
            return VEnchantmentType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // continue
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        for (VEnchantmentType type : VEnchantmentType.values()) {
            if (type.getKey().equalsIgnoreCase(lowered)) {
                return type;
            }
        }

        return null;
    }

    private static Integer parseInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                errors.add("item." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                errors.add("item." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            errors.add("item." + fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            errors.add("item." + fieldName + " must be between " + min + " and " + max + " in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    private static Double parseDouble(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        Double value;
        if (rawValue instanceof Number) {
            value = ((Number) rawValue).doubleValue();
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            try {
                value = Double.parseDouble(trimmed);
            } catch (NumberFormatException ex) {
                errors.add("item." + fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            errors.add("item." + fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Double.isFinite(value)) {
            errors.add("item." + fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    private static Boolean parseBoolean(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        if (rawValue instanceof Boolean) return (Boolean) rawValue;
        if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals("true")) return true;
            if (trimmed.equals("false")) return false;
        }
        errors.add("item." + fieldName + " must be true or false in " + sourceFile.getPath());
        return null;
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static String normalizeNamespacedValue(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        int firstColon = trimmed.indexOf(':');
        if (firstColon >= 0 && trimmed.indexOf(':', firstColon + 1) >= 0) return null;
        String core = firstColon >= 0 ? trimmed.substring(firstColon + 1) : trimmed;
        if (core.isEmpty()) return null;
        String normalized = core.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replace('-', '_').replace(' ', '_');
        return normalized;
    }

    private static List<String> parseLore(Object rawLore, File sourceFile, List<String> errors) {
        if (rawLore == null) return Collections.emptyList();
        if (!(rawLore instanceof List<?>)) {
            errors.add("item.lore must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawLore) {
            if (!(entry instanceof String)) {
                errors.add("item.lore entry " + index + " must be a string in " + sourceFile.getPath());
                return Collections.emptyList();
            }
            String translated = translateColors((String) entry, "lore", sourceFile, errors);
            if (translated == null) return Collections.emptyList();
            result.add(translated);
            index++;
        }

        return result;
    }

    private static String translateColors(
            String rawText, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawText == null) return null;
        StringBuilder result = new StringBuilder(rawText.length());
        int index = 0;
        boolean usedHex = false;
        while (index < rawText.length()) {
            int hexIndex = rawText.indexOf("&#", index);
            if (hexIndex < 0) {
                result.append(rawText, index, rawText.length());
                break;
            }

            result.append(rawText, index, hexIndex);
            if (hexIndex + 8 > rawText.length()) {
                errors.add("item." + fieldName + " has invalid hex color in " + sourceFile.getPath());
                return null;
            }

            String hex = rawText.substring(hexIndex + 2, hexIndex + 8);
            if (!isHex(hex)) {
                errors.add("item." + fieldName + " has invalid hex color in " + sourceFile.getPath());
                return null;
            }

            usedHex = true;
            result.append(toAmpersandHex(hex));
            index = hexIndex + 8;
        }

        if (usedHex && mcVersion < MCVersions.VERSION1_16) {
            errors.add("Hex colors require MC 1.16+ in " + sourceFile.getPath());
            return null;
        }

        return result.toString();
    }

    private static String toAmpersandHex(String hex) {
        String upper = hex.toUpperCase(Locale.ROOT);
        return "&x&" + upper.charAt(0) + "&" + upper.charAt(1)
                + "&" + upper.charAt(2) + "&" + upper.charAt(3)
                + "&" + upper.charAt(4) + "&" + upper.charAt(5);
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean upper = c >= 'A' && c <= 'F';
            boolean lower = c >= 'a' && c <= 'f';
            if (!digit && !upper && !lower) return false;
        }
        return value.length() == 6;
    }

    private static class ParsedEnchantment {

        final VEnchantmentType type;
        final int level;

        ParsedEnchantment(VEnchantmentType type, int level) {
            this.type = type;
            this.level = level;
        }
    }
}
