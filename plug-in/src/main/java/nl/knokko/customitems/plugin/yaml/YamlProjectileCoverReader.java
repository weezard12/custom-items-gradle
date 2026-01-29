package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.item.KciItemType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

class YamlProjectileCoverReader {

    static List<YamlProjectileCoverDefinition> readProjectileCovers(
            YamlPackDefinition pack, List<String> errors, List<String> warnings
    ) {
        List<YamlProjectileCoverDefinition> covers = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(pack.directory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) return;
                if (fileName.equals("pack.yml")) return;

                File file = path.toFile();
                List<YamlConfiguration> configs = YamlDocumentReader.loadDocuments(file, errors);
                for (YamlConfiguration config : configs) {
                    ConfigurationSection coverSection = config.getConfigurationSection("projectile_cover");
                    if (coverSection == null) coverSection = config.getConfigurationSection("projectile-cover");
                    if (coverSection == null) continue;

                    int errorCountBefore = errors.size();
                    YamlProjectileCoverDefinition cover = parseCoverDefinition(pack, file, coverSection, errors, warnings);
                    if (errors.size() != errorCountBefore) return;
                    if (cover != null) {
                        covers.add(cover);
                    }
                }
            });
        } catch (IOException ex) {
            errors.add("Failed to scan pack folder " + pack.directory.getPath() + ": " + ex.getMessage());
        }
        return covers;
    }

    private static YamlProjectileCoverDefinition parseCoverDefinition(
            YamlPackDefinition pack,
            File file,
            ConfigurationSection coverSection,
            List<String> errors,
            List<String> warnings
    ) {
        String rawId = coverSection.getString("id");
        if (rawId == null || rawId.trim().isEmpty()) {
            errors.add("Missing projectile_cover.id in " + file.getPath());
            return null;
        }

        ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors);
        if (parsedId == null) return null;

        ConfigurationSection requiresSection = getChildSection(coverSection, "requires", file, warnings);
        if (!matchesRequires(requiresSection, file, warnings)) {
            return null;
        }

        YamlProjectileCoverType type = parseCoverType(coverSection.get("type"), coverSection, file, warnings);
        if (type == null) return null;

        KciItemType itemType = parseItemType(coverSection.get("item_type"), file, warnings);
        if (itemType == null) itemType = parseItemType(coverSection.get("item"), file, warnings);

        String geyserTexture = parseOptionalString(coverSection.get("geyser_texture"), "geyser_texture", file, warnings);

        if (type == YamlProjectileCoverType.SPHERE) {
            String texture;
            if (coverSection.isSet("texture")) {
                texture = parseOptionalString(coverSection.get("texture"), "texture", file, warnings);
                if (texture == null) {
                    errors.add("projectile_cover.texture must be a non-empty string in " + file.getPath());
                    return null;
                }
            } else {
                texture = parsedId.internalName;
            }
            Integer slotsPerAxis = parseOptionalInteger(
                    coverSection.get("slots_per_axis"), 1, 50, "slots_per_axis", file, warnings
            );
            Double scale = parseOptionalDouble(coverSection.get("scale"), 0.0001, Double.MAX_VALUE, "scale", file, warnings);
            return new YamlProjectileCoverDefinition(
                    parsedId.fullId,
                    parsedId.internalName,
                    parsedId.name,
                    pack.directory,
                    file,
                    type,
                    itemType,
                    texture,
                    slotsPerAxis,
                    scale,
                    null,
                    null,
                    geyserTexture
            );
        }

        ConfigurationSection modelSection = getChildSection(coverSection, "model", file, warnings);
        if (modelSection == null) {
            errors.add("projectile_cover.model is required for custom covers in " + file.getPath());
            return null;
        }
        String modelPath = parseOptionalString(modelSection.get("json"), "model.json", file, warnings);
        if (modelPath == null) {
            modelPath = parseOptionalString(modelSection.get("model"), "model.model", file, warnings);
        }
        if (modelPath == null) {
            errors.add("projectile_cover.model.json is required for custom covers in " + file.getPath());
            return null;
        }

        ConfigurationSection texturesSection = getChildSection(modelSection, "textures", file, warnings);
        if (texturesSection == null) {
            errors.add("projectile_cover.model.textures is required for custom covers in " + file.getPath());
            return null;
        }
        Map<String, String> texturePaths = new HashMap<>();
        for (String key : texturesSection.getKeys(false)) {
            String value = parseOptionalString(texturesSection.get(key), "model.textures." + key, file, warnings);
            if (value == null) return null;
            texturePaths.put(key, value);
        }

        return new YamlProjectileCoverDefinition(
                parsedId.fullId,
                parsedId.internalName,
                parsedId.name,
                pack.directory,
                file,
                type,
                itemType,
                null,
                null,
                null,
                modelPath,
                texturePaths,
                geyserTexture
        );
    }

    private static YamlProjectileCoverType parseCoverType(
            Object rawType, ConfigurationSection coverSection, File sourceFile, List<String> warnings
    ) {
        if (rawType == null) {
            if (coverSection.isSet("model")) return YamlProjectileCoverType.CUSTOM;
            return YamlProjectileCoverType.SPHERE;
        }
        if (!(rawType instanceof String)) {
            warnOptional(warnings, "projectile_cover.type must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawType).trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (trimmed.equals("sphere")) return YamlProjectileCoverType.SPHERE;
        if (trimmed.equals("custom")) return YamlProjectileCoverType.CUSTOM;
        warnOptional(warnings, "Unknown projectile_cover.type '" + trimmed + "' in " + sourceFile.getPath());
        return null;
    }

    private static KciItemType parseItemType(Object rawValue, File sourceFile, List<String> warnings) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, "projectile_cover.item_type must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid projectile_cover.item_type in " + sourceFile.getPath());
            return null;
        }
        try {
            KciItemType itemType = KciItemType.valueOf(normalized);
            if (mcVersion < itemType.firstVersion || mcVersion > itemType.lastVersion) {
                warnOptional(warnings, "projectile_cover.item_type is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            if (!itemType.canServe(KciItemType.Category.PROJECTILE_COVER)) {
                warnOptional(warnings, "projectile_cover.item_type can't be used for projectile covers in "
                        + sourceFile.getPath());
                return null;
            }
            return itemType;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown projectile_cover.item_type '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors
    ) {
        String namespace;
        String name;

        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            if (rawId.indexOf(':', colonIndex + 1) >= 0) {
                errors.add("Invalid projectile_cover.id '" + rawId + "' in " + sourceFile.getPath() + ": too many ':' characters");
                return null;
            }
            namespace = rawId.substring(0, colonIndex);
            name = rawId.substring(colonIndex + 1);
        } else {
            namespace = defaultNamespace;
            name = rawId;
        }

        if (namespace == null || namespace.isEmpty()) {
            errors.add("Missing namespace for projectile_cover.id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }
        if (name.isEmpty()) {
            errors.add("Missing name for projectile_cover.id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            Validation.safeName(namespace);
            Validation.safeName(name);
        } catch (ValidationException | ProgrammingValidationException ex) {
            errors.add("Invalid projectile_cover.id '" + rawId + "' in " + sourceFile.getPath() + ": " + ex.getMessage());
            return null;
        }

        String fullId = namespace + ":" + name;
        String internalName = namespace + "_" + name;
        return new ParsedId(fullId, internalName, name);
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> warnings
    ) {
        if (parent == null) return null;
        if (!parent.isSet(name)) return null;
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) {
            warnOptional(warnings, "projectile_cover." + name + " must be a map in " + sourceFile.getPath());
            return null;
        }
        return section;
    }

    private static boolean matchesRequires(
            ConfigurationSection requiresSection, File sourceFile, List<String> warnings
    ) {
        if (requiresSection == null) return true;
        String raw = parseOptionalString(requiresSection.get("mc"), "requires.mc", sourceFile, warnings);
        if (raw == null) return true;
        String trimmed = raw.trim();

        String operator = "==";
        String versionPart = trimmed;
        if (trimmed.startsWith(">=") || trimmed.startsWith("<=")) {
            operator = trimmed.substring(0, 2);
            versionPart = trimmed.substring(2);
        } else if (trimmed.startsWith(">") || trimmed.startsWith("<") || trimmed.startsWith("=")) {
            operator = trimmed.substring(0, 1);
            versionPart = trimmed.substring(1);
        }

        Integer version = parseMcVersion(versionPart.trim(), sourceFile, warnings);
        if (version == null) return true;

        switch (operator) {
            case ">=":
                return mcVersion >= version;
            case "<=":
                return mcVersion <= version;
            case ">":
                return mcVersion > version;
            case "<":
                return mcVersion < version;
            case "=":
            case "==":
                return mcVersion == version;
            default:
                warnOptional(warnings, "Invalid requires.mc operator in " + sourceFile.getPath());
                return true;
        }
    }

    private static Integer parseMcVersion(String raw, File sourceFile, List<String> warnings) {
        if (raw == null || raw.isEmpty()) {
            warnOptional(warnings, "requires.mc is empty in " + sourceFile.getPath());
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("1.")) trimmed = trimmed.substring(2);
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex >= 0) trimmed = trimmed.substring(0, dotIndex);
        if (!isInteger(trimmed)) {
            warnOptional(warnings, "Invalid requires.mc '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        int version = Integer.parseInt(trimmed);
        if (version < MCVersions.FIRST_VERSION || version > MCVersions.LAST_VERSION) {
            warnOptional(warnings, "Unsupported requires.mc '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        return version;
    }

    private static Integer parseOptionalInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                warnOptional(warnings, "projectile_cover." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                warnOptional(warnings, "projectile_cover." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    private static Double parseOptionalDouble(
            Object rawValue, double min, double max, String fieldName, File sourceFile, List<String> warnings
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
                warnOptional(warnings, "projectile_cover." + fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Double.isFinite(value)) {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }
        if (value < min || value > max) {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, "projectile_cover." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, "projectile_cover." + fieldName + " must not be empty in " + sourceFile.getPath());
            return null;
        }
        return trimmed;
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

    private static void warnOptional(List<String> warnings, String message) {
        if (warnings == null) return;
        warnings.add("WARNING - " + message + ". (This field will be ignored.)");
    }

    private static String normalizeEnumKey(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex >= 0 && trimmed.indexOf(':', colonIndex + 1) >= 0) return null;
        String core = colonIndex >= 0 ? trimmed.substring(colonIndex + 1) : trimmed;
        if (core.isEmpty()) return null;
        String normalized = core.toUpperCase(Locale.ROOT);
        normalized = normalized.replace('-', '_').replace(' ', '_').replace('.', '_').replace('/', '_');
        return normalized;
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
}
