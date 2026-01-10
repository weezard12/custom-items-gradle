package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.KciItemType.Category;
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
                List<YamlConfiguration> configs = YamlDocumentReader.loadDocuments(file, errors);
                for (YamlConfiguration config : configs) {
                    ConfigurationSection itemSection = config.getConfigurationSection("item");
                    if (itemSection == null) continue;

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

                    ConfigurationSection toolSection = getChildSection(itemSection, "tool", file, errors);
                    ConfigurationSection armorSection = getChildSection(itemSection, "armor", file, errors);
                    ConfigurationSection foodSection = getChildSection(itemSection, "food", file, errors);

                    YamlItemType type = parseItemType(itemSection.get("type"), file, errors);
                    if (type == null) {
                        type = inferItemType(toolSection, armorSection, foodSection, file, errors);
                    }

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

                    if (type == null) return;

                    validateTypeSections(type, toolSection, armorSection, foodSection, file, errors);
                    validateMaterialForType(type, material, file, errors);
                    if ((type == YamlItemType.TOOL || type == YamlItemType.ARMOR) && stackSize != null) {
                        errors.add("item.stack_size is not supported for type " + type.name().toLowerCase(Locale.ROOT)
                                + " in " + file.getPath());
                        return;
                    }

                    YamlToolDefinition toolDefinition = null;
                    YamlArmorDefinition armorDefinition = null;
                    YamlFoodDefinition foodDefinition = null;
                    if (type == YamlItemType.TOOL) {
                        toolDefinition = parseToolDefinition(toolSection, file, errors);
                    } else if (type == YamlItemType.ARMOR) {
                        armorDefinition = parseArmorDefinition(armorSection, file, errors);
                    } else if (type == YamlItemType.FOOD) {
                        foodDefinition = parseFoodDefinition(foodSection, file, errors);
                    }

                    if (errors.size() != errorCountBefore) return;

                    items.add(new YamlItemDefinition(
                            parsedId.fullId,
                            parsedId.internalName,
                            parsedId.name,
                            pack.directory,
                            displayName,
                            file,
                            lore,
                            type,
                            toolDefinition,
                            armorDefinition,
                            foodDefinition,
                            material,
                            enchantments,
                            stackSize,
                            damageValue,
                            unbreakable,
                            attackDamage,
                            attackSpeed
                    ));
                }
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
        return new ParsedId(fullId, internalName, name);
    }

    private static class ParsedId {

        final String fullId;
        final String internalName;
        final String name;

        ParsedId(String fullId, String internalName, String name) {
            this.fullId = fullId;
            this.internalName = internalName;
            this.name = name;
        }
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> errors
    ) {
        if (parent == null) return null;
        if (!parent.isSet(name)) return null;
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) {
            errors.add("item." + name + " must be a map in " + sourceFile.getPath());
            return null;
        }
        return section;
    }

    private static YamlItemType parseItemType(Object rawType, File sourceFile, List<String> errors) {
        if (rawType == null) return null;
        if (!(rawType instanceof String)) {
            errors.add("item.type must be a string in " + sourceFile.getPath());
            return null;
        }

        String trimmed = ((String) rawType).trim();
        if (trimmed.isEmpty()) {
            errors.add("item.type must not be empty in " + sourceFile.getPath());
            return null;
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "simple":
                return YamlItemType.SIMPLE;
            case "tool":
                return YamlItemType.TOOL;
            case "armor":
            case "armour":
                return YamlItemType.ARMOR;
            case "food":
                return YamlItemType.FOOD;
            default:
                errors.add("Unknown item.type '" + trimmed + "' in " + sourceFile.getPath());
                return null;
        }
    }

    private static YamlItemType inferItemType(
            ConfigurationSection toolSection,
            ConfigurationSection armorSection,
            ConfigurationSection foodSection,
            File sourceFile,
            List<String> errors
    ) {
        int count = 0;
        YamlItemType inferred = YamlItemType.SIMPLE;
        if (toolSection != null) {
            count++;
            inferred = YamlItemType.TOOL;
        }
        if (armorSection != null) {
            count++;
            inferred = YamlItemType.ARMOR;
        }
        if (foodSection != null) {
            count++;
            inferred = YamlItemType.FOOD;
        }

        if (count > 1) {
            errors.add("item.type must be set when multiple type blocks are present in " + sourceFile.getPath());
            return null;
        }

        return inferred;
    }

    private static void validateTypeSections(
            YamlItemType type,
            ConfigurationSection toolSection,
            ConfigurationSection armorSection,
            ConfigurationSection foodSection,
            File sourceFile,
            List<String> errors
    ) {
        if (type != YamlItemType.TOOL && toolSection != null) {
            errors.add("item.tool is only allowed for type tool in " + sourceFile.getPath());
        }
        if (type != YamlItemType.ARMOR && armorSection != null) {
            errors.add("item.armor is only allowed for type armor in " + sourceFile.getPath());
        }
        if (type != YamlItemType.FOOD && foodSection != null) {
            errors.add("item.food is only allowed for type food in " + sourceFile.getPath());
        }
    }

    private static void validateMaterialForType(
            YamlItemType type,
            YamlMaterialDefinition material,
            File sourceFile,
            List<String> errors
    ) {
        if (material == null) return;

        if (type == YamlItemType.SIMPLE) return;

        if (type == YamlItemType.FOOD) {
            if (material.otherMaterial != null) return;
            if (!material.itemType.canServe(Category.FOOD)) {
                errors.add("item.material must be food-compatible for type food in " + sourceFile.getPath());
            }
            return;
        }

        if (material.otherMaterial != null) {
            if (type == YamlItemType.TOOL && material.otherMaterial == VMaterial.MACE) {
                return;
            }
            errors.add("item.material must be a custom item type for type " + type.name().toLowerCase(Locale.ROOT)
                    + " in " + sourceFile.getPath());
            return;
        }

        if (type == YamlItemType.TOOL && !isToolItemType(material.itemType)) {
            errors.add("item.material must be a tool material for type tool in " + sourceFile.getPath());
        } else if (type == YamlItemType.ARMOR && !isArmorItemType(material.itemType)) {
            errors.add("item.material must be an armor material for type armor in " + sourceFile.getPath());
        }
    }

    private static boolean isToolItemType(KciItemType itemType) {
        return itemType.canServe(Category.SWORD) || itemType.canServe(Category.AXE)
                || itemType.canServe(Category.PICKAXE) || itemType.canServe(Category.SHOVEL)
                || itemType.canServe(Category.HOE) || itemType.canServe(Category.SHEAR)
                || itemType.canServe(Category.FISHING) || itemType.canServe(Category.FLINT)
                || itemType.canServe(Category.CARROTSTICK);
    }

    private static boolean isArmorItemType(KciItemType itemType) {
        return itemType.canServe(Category.HELMET) || itemType.canServe(Category.CHESTPLATE)
                || itemType.canServe(Category.LEGGINGS) || itemType.canServe(Category.BOOTS);
    }

    private static YamlToolDefinition parseToolDefinition(
            ConfigurationSection toolSection, File sourceFile, List<String> errors
    ) {
        if (toolSection == null) return new YamlToolDefinition(null, null, null);
        Integer maxDurability = parseInteger(toolSection.get("max_durability"), 1, Integer.MAX_VALUE,
                "tool.max_durability", sourceFile, errors);
        Integer entityHitLoss = parseInteger(toolSection.get("entity_hit_durability_loss"), 0, Integer.MAX_VALUE,
                "tool.entity_hit_durability_loss", sourceFile, errors);
        Integer blockBreakLoss = parseInteger(toolSection.get("block_break_durability_loss"), 0, Integer.MAX_VALUE,
                "tool.block_break_durability_loss", sourceFile, errors);
        return new YamlToolDefinition(maxDurability, entityHitLoss, blockBreakLoss);
    }

    private static YamlArmorDefinition parseArmorDefinition(
            ConfigurationSection armorSection, File sourceFile, List<String> errors
    ) {
        if (armorSection == null) return new YamlArmorDefinition(null, null, null, null, null);
        Integer maxDurability = parseInteger(armorSection.get("max_durability"), 1, Integer.MAX_VALUE,
                "armor.max_durability", sourceFile, errors);
        Integer entityHitLoss = parseInteger(armorSection.get("entity_hit_durability_loss"), 0, Integer.MAX_VALUE,
                "armor.entity_hit_durability_loss", sourceFile, errors);
        Integer blockBreakLoss = parseInteger(armorSection.get("block_break_durability_loss"), 0, Integer.MAX_VALUE,
                "armor.block_break_durability_loss", sourceFile, errors);
        Double armorValue = parseDouble(armorSection.get("armor_value"), "armor.armor_value", sourceFile, errors);
        Double armorToughness = parseDouble(armorSection.get("armor_toughness"), "armor.armor_toughness", sourceFile, errors);
        if (armorValue != null && armorValue < 0.0) {
            errors.add("item.armor.armor_value must be non-negative in " + sourceFile.getPath());
            return null;
        }
        if (armorToughness != null && armorToughness < 0.0) {
            errors.add("item.armor.armor_toughness must be non-negative in " + sourceFile.getPath());
            return null;
        }
        return new YamlArmorDefinition(maxDurability, entityHitLoss, blockBreakLoss, armorValue, armorToughness);
    }

    private static YamlFoodDefinition parseFoodDefinition(
            ConfigurationSection foodSection, File sourceFile, List<String> errors
    ) {
        if (foodSection == null) return new YamlFoodDefinition(null, null);
        Integer foodValue = parseInteger(foodSection.get("food_value"), 0, Integer.MAX_VALUE,
                "food.food_value", sourceFile, errors);
        Integer eatTime = parseInteger(foodSection.get("eat_time"), 1, Integer.MAX_VALUE,
                "food.eat_time", sourceFile, errors);
        return new YamlFoodDefinition(foodValue, eatTime);
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
        while (index < rawText.length()) {
            int ampIndex = rawText.indexOf('&', index);
            if (ampIndex < 0) {
                result.append(rawText, index, rawText.length());
                break;
            }

            result.append(rawText, index, ampIndex);
            if (ampIndex + 1 >= rawText.length()) {
                result.append('&');
                break;
            }

            char next = rawText.charAt(ampIndex + 1);
            if (next == '#') {
                if (ampIndex + 8 > rawText.length()) {
                    errors.add("item." + fieldName + " has invalid hex color in " + sourceFile.getPath());
                    return null;
                }
                String hex = rawText.substring(ampIndex + 2, ampIndex + 8);
                if (!isHex(hex)) {
                    errors.add("item." + fieldName + " has invalid hex color in " + sourceFile.getPath());
                    return null;
                }

                if (mcVersion >= MCVersions.VERSION1_16) {
                    result.append(toAmpersandHex(hex));
                } else {
                    result.append(toLegacyColor(hex));
                }
                index = ampIndex + 8;
            } else if (isColorCodeChar(next)) {
                result.append('&').append(Character.toLowerCase(next));
                index = ampIndex + 2;
            } else {
                result.append('&');
                index = ampIndex + 1;
            }
        }

        return result.toString();
    }

    private static String toAmpersandHex(String hex) {
        String lower = hex.toLowerCase(Locale.ROOT);
        return "&x&" + lower.charAt(0) + "&" + lower.charAt(1)
                + "&" + lower.charAt(2) + "&" + lower.charAt(3)
                + "&" + lower.charAt(4) + "&" + lower.charAt(5);
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

    private static boolean isColorCodeChar(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z');
    }

    private static String toLegacyColor(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < LEGACY_COLOR_RGB.length; index++) {
            int legacy = LEGACY_COLOR_RGB[index];
            int lr = (legacy >> 16) & 0xFF;
            int lg = (legacy >> 8) & 0xFF;
            int lb = legacy & 0xFF;
            int dr = red - lr;
            int dg = green - lg;
            int db = blue - lb;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }

        return "&" + LEGACY_COLOR_CODES[bestIndex];
    }

    private static final char[] LEGACY_COLOR_CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static final int[] LEGACY_COLOR_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static class ParsedEnchantment {

        final VEnchantmentType type;
        final int level;

        ParsedEnchantment(VEnchantmentType type, int level) {
            this.type = type;
            this.level = level;
        }
    }
}
