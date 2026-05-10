package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.drops.VBiome;
import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.sound.VSoundType;
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

import static nl.knokko.customitems.plugin.yaml.YamlVersionContext.mcVersion;

/**
 * Shared utility methods for YAML parsing across all reader classes.
 * This class consolidates common parsing logic to eliminate code duplication.
 */
public final class YamlParseUtils {

    private YamlParseUtils() {}

    @FunctionalInterface
    public interface YamlDocumentConsumer {
        void accept(File file, YamlConfiguration config);
    }

    /**
     * Iterates over all YAML documents in a pack directory (including pack.yml).
     */
    public static void forEachYamlDocument(
            YamlPackDefinition pack, List<String> errors, YamlDocumentConsumer consumer
    ) {
        try (Stream<Path> paths = Files.walk(pack.directory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) return;

                File file = path.toFile();
                List<YamlConfiguration> configs = YamlDocumentReader.loadDocuments(file, errors);
                for (YamlConfiguration config : configs) {
                    consumer.accept(file, config);
                }
            });
        } catch (IOException ex) {
            errors.add("Failed to scan pack folder " + pack.directory.getPath() + ": " + ex.getMessage());
        }
    }

    // ========== ID Parsing ==========

    /**
     * Represents a parsed ID with namespace and name components.
     */
    public static class ParsedId {
        public final String fullId;
        public final String internalName;
        public final String name;

        public ParsedId(String fullId, String internalName, String name) {
            this.fullId = fullId;
            this.internalName = internalName;
            this.name = name;
        }
    }

    /**
     * Parses a required ID string into namespace:name format.
     * Adds to errors if the ID is invalid.
     */
    public static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors, String context
    ) {
        String label = formatIdContext(context);
        String namespace;
        String name;

        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            if (rawId.indexOf(':', colonIndex + 1) >= 0) {
                errors.add("Invalid " + label + " '" + rawId + "' in " + sourceFile.getPath()
                        + ": too many ':' characters");
                return null;
            }
            namespace = rawId.substring(0, colonIndex);
            name = rawId.substring(colonIndex + 1);
        } else {
            namespace = defaultNamespace;
            name = rawId;
        }

        if (namespace == null || namespace.isEmpty()) {
            errors.add("Missing namespace for " + label + " '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }
        if (name.isEmpty()) {
            errors.add("Missing name for " + label + " '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            Validation.safeName(namespace);
            Validation.safeName(name);
        } catch (ValidationException | ProgrammingValidationException ex) {
            errors.add("Invalid " + label + " '" + rawId + "' in " + sourceFile.getPath() + ": " + ex.getMessage());
            return null;
        }

        String fullId = namespace + ":" + name;
        String internalName = namespace + "_" + name;
        return new ParsedId(fullId, internalName, name);
    }

    /**
     * Parses an optional ID string into namespace:name format.
     * Adds to warnings if the ID is invalid.
     */
    public static ParsedId parseOptionalId(
            String rawId, String defaultNamespace, File sourceFile, List<String> warnings, String fieldName
    ) {
        if (rawId == null || rawId.trim().isEmpty()) return null;
        String label = formatIdContext(fieldName);
        String namespace;
        String name;

        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            if (rawId.indexOf(':', colonIndex + 1) >= 0) {
                warnOptional(warnings, "Invalid " + label + " '" + rawId + "' in " + sourceFile.getPath()
                        + ": too many ':' characters");
                return null;
            }
            namespace = rawId.substring(0, colonIndex);
            name = rawId.substring(colonIndex + 1);
        } else {
            namespace = defaultNamespace;
            name = rawId;
        }

        if (namespace == null || namespace.isEmpty()) {
            warnOptional(warnings, "Missing namespace for " + label + " '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }
        if (name.isEmpty()) {
            warnOptional(warnings, "Missing name for " + label + " '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            Validation.safeName(namespace);
            Validation.safeName(name);
        } catch (ValidationException | ProgrammingValidationException ex) {
            warnOptional(warnings, "Invalid " + label + " '" + rawId + "' in " + sourceFile.getPath()
                    + ": " + ex.getMessage());
            return null;
        }

        String fullId = namespace + ":" + name;
        String internalName = namespace + "_" + name;
        return new ParsedId(fullId, internalName, name);
    }

    private static String formatIdContext(String context) {
        if (context == null || context.isEmpty()) return "id";
        String trimmed = context.trim();
        if (trimmed.isEmpty()) return "id";
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (lowered.endsWith("id")) return trimmed;
        if (trimmed.contains(".") || trimmed.contains("[")) return trimmed;
        return trimmed + " id";
    }

    // ========== Section Handling ==========

    /**
     * Gets a child configuration section, warning if it exists but is not a section.
     */
    public static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, String prefix, File sourceFile, List<String> warnings
    ) {
        if (parent == null) return null;
        if (!parent.isSet(name)) return null;
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) {
            warnOptional(warnings, prefix + name + " must be a map in " + sourceFile.getPath());
            return null;
        }
        return section;
    }

    // ========== Type Parsers ==========

    /**
     * Parses a required string value, adding errors if missing or invalid.
     */
    public static String parseRequiredString(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) {
            errors.add("Missing " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        if (!(rawValue instanceof String)) {
            errors.add(fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) {
            errors.add(fieldName + " must not be empty in " + sourceFile.getPath());
            return null;
        }
        return trimmed;
    }

    /**
     * Parses an integer value with range validation.
     */
    public static Integer parseInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                warnOptional(warnings, fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                warnOptional(warnings, fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            warnOptional(warnings, fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            warnOptional(warnings, fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    /**
     * Parses an optional integer value, adding errors if invalid.
     */
    public static Integer parseOptionalInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            errors.add(fieldName + " must be between " + min + " and " + max + " in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    /**
     * Parses a required integer value, adding to errors if missing.
     */
    public static Integer parseRequiredInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) {
            errors.add("Missing " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            errors.add(fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            errors.add(fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    /**
     * Parses a boolean value.
     */
    public static Boolean parseBoolean(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (rawValue instanceof Boolean) return (Boolean) rawValue;
        if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals("true")) return true;
            if (trimmed.equals("false")) return false;
        }
        warnOptional(warnings, fieldName + " must be true or false in " + sourceFile.getPath());
        return null;
    }

    /**
     * Parses a boolean value with a default.
     */
    public static Boolean parseBoolean(
            Object rawValue, boolean defaultValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return defaultValue;
        Boolean parsed = parseBoolean(rawValue, fieldName, sourceFile, warnings);
        return parsed != null ? parsed : defaultValue;
    }

    /**
     * Parses a double value.
     */
    public static Double parseDouble(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
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
                warnOptional(warnings, fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            warnOptional(warnings, fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Double.isFinite(value)) {
            warnOptional(warnings, fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    /**
     * Parses a double value with range validation.
     */
    public static Double parseDoubleInRange(
            Object rawValue, double min, double max, String fieldName, File sourceFile, List<String> warnings
    ) {
        Double value = parseDouble(rawValue, fieldName, sourceFile, warnings);
        if (value == null) return null;
        if (value < min || value > max) {
            warnOptional(warnings, fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    /**
     * Parses a float value.
     */
    public static Float parseFloat(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        Double doubleValue = parseDouble(rawValue, fieldName, sourceFile, warnings);
        return doubleValue != null ? doubleValue.floatValue() : null;
    }

    /**
     * Parses a float value with range validation.
     */
    public static Float parseFloatInRange(
            Object rawValue, float min, float max, String fieldName, File sourceFile, List<String> warnings
    ) {
        Float value = parseFloat(rawValue, fieldName, sourceFile, warnings);
        if (value == null) return null;
        if (value < min || value > max) {
            warnOptional(warnings, fieldName + " must be between " + min + " and " + max
                    + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    /**
     * Parses a float value, adding errors if invalid.
     */
    public static Float parseOptionalFloat(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        Float value;
        if (rawValue instanceof Number) {
            value = ((Number) rawValue).floatValue();
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            try {
                value = Float.parseFloat(trimmed);
            } catch (NumberFormatException ex) {
                errors.add(fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            errors.add(fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Float.isFinite(value)) {
            errors.add(fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }

        return value;
    }

    /**
     * Parses an optional string value.
     */
    public static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, fieldName + " must not be empty in " + sourceFile.getPath());
            return null;
        }
        return trimmed;
    }

    /**
     * Parses a list of strings.
     */
    public static List<String> parseStringList(
            Object rawList, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawList == null) return Collections.emptyList();
        if (!(rawList instanceof List<?>)) {
            warnOptional(warnings, fieldName + " must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawList) {
            if (!(entry instanceof String)) {
                warnOptional(warnings, fieldName + " entry " + index + " must be a string in "
                        + sourceFile.getPath());
                index++;
                continue;
            }
            String trimmed = ((String) entry).trim();
            if (trimmed.isEmpty()) {
                warnOptional(warnings, fieldName + " entry " + index + " must not be empty in "
                        + sourceFile.getPath());
                index++;
                continue;
            }
            result.add(trimmed);
            index++;
        }

        return result;
    }

    // ========== String Utilities ==========

    /**
     * Checks if a string represents an integer value.
     */
    public static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * Normalizes a namespaced value (removes namespace prefix, uppercase, replace dashes/spaces).
     */
    public static String normalizeNamespacedValue(String raw) {
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

    /**
     * Normalizes an enum key by converting to uppercase and replacing hyphens/spaces with underscores.
     */
    public static String normalizeEnumKey(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        int firstColon = trimmed.indexOf(':');
        if (firstColon >= 0 && trimmed.indexOf(':', firstColon + 1) >= 0) return null;
        String core = firstColon >= 0 ? trimmed.substring(firstColon + 1) : trimmed;
        if (core.isEmpty()) return null;
        String normalized = core.trim().toUpperCase(Locale.ROOT);
        return normalized.replace('-', '_')
                .replace(' ', '_')
                .replace('.', '_')
                .replace('/', '_');
    }

    /**
     * Returns true if this looks like a vanilla material (heuristic).
     */
    public static boolean isProbablyVanillaMaterial(String raw) {
        int colonIndex = raw.indexOf(':');
        if (colonIndex >= 0) {
            String namespace = raw.substring(0, colonIndex).trim();
            return namespace.equalsIgnoreCase("minecraft");
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isUpperCase(raw.charAt(i))) return true;
        }
        return false;
    }

    /**
     * Converts a raw object to a map if possible.
     */
    public static Map<?, ?> toMap(Object rawValue) {
        if (rawValue instanceof ConfigurationSection) {
            return ((ConfigurationSection) rawValue).getValues(false);
        }
        if (rawValue instanceof Map<?, ?>) return (Map<?, ?>) rawValue;
        return null;
    }

    // ========== Range Parsing ==========

    public static FloatRange parseOptionalRange(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (rawValue instanceof Number || rawValue instanceof String) {
            Float value = parseFloatInRange(rawValue, -Float.MAX_VALUE, Float.MAX_VALUE,
                    fieldName, sourceFile, warnings);
            if (value == null) return null;
            return new FloatRange(value, value);
        }
        ConfigurationSection section = rawValue instanceof ConfigurationSection ? (ConfigurationSection) rawValue : null;
        Map<?, ?> map = rawValue instanceof Map<?, ?> ? (Map<?, ?>) rawValue : null;
        if (section == null && map == null) {
            warnOptional(warnings, fieldName + " must be a number or map in " + sourceFile.getPath());
            return null;
        }
        Object rawMin = section != null ? section.get("min") : map.get("min");
        Object rawMax = section != null ? section.get("max") : map.get("max");
        Float min = parseFloatInRange(rawMin, -Float.MAX_VALUE, Float.MAX_VALUE,
                fieldName + ".min", sourceFile, warnings);
        Float max = parseFloatInRange(rawMax, -Float.MAX_VALUE, Float.MAX_VALUE,
                fieldName + ".max", sourceFile, warnings);
        if (min == null || max == null) {
            warnOptional(warnings, fieldName + " requires min and max in " + sourceFile.getPath());
            return null;
        }
        return new FloatRange(min, max);
    }

    public static class FloatRange {
        public final Float min;
        public final Float max;

        public FloatRange(Float min, Float max) {
            this.min = min;
            this.max = max;
        }
    }

    // ========== Version Requirements ==========

    public static boolean matchesRequires(
            ConfigurationSection requiresSection, String fieldPrefix, File sourceFile, List<String> warnings
    ) {
        if (requiresSection == null) return true;
        String raw = parseOptionalString(
                requiresSection.get("mc"), fieldPrefix + ".mc", sourceFile, warnings
        );
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

        Integer version = parseMcVersion(versionPart.trim(), fieldPrefix + ".mc", sourceFile, warnings);
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
                warnOptional(warnings, "Invalid " + fieldPrefix + ".mc operator in " + sourceFile.getPath());
                return true;
        }
    }

    public static Integer parseMcVersion(
            String raw, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (raw == null || raw.isEmpty()) {
            warnOptional(warnings, fieldName + " is empty in " + sourceFile.getPath());
            return null;
        }
        String trimmed = raw.trim();
        Integer parsedVersion = MCVersions.parseVersion(trimmed);
        if (parsedVersion == null && isInteger(trimmed)) {
            parsedVersion = MCVersions.normalizeLowerBound(Integer.parseInt(trimmed));
        }
        if (parsedVersion == null) {
            warnOptional(warnings, "Invalid " + fieldName + " '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        if (!MCVersions.isSupported(parsedVersion)) {
            warnOptional(warnings, "Unsupported " + fieldName + " '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        return parsedVersion;
    }

    // ========== Enum Parsers ==========

    public static VMaterial parseVMaterial(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add(fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            errors.add("Invalid " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VMaterial material = VMaterial.valueOf(normalized);
            if (mcVersion < material.firstVersion || mcVersion > material.lastVersion) {
                errors.add(fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return material;
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown " + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    public static VMaterial parseOptionalVMaterial(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VMaterial material = VMaterial.valueOf(normalized);
            if (mcVersion < material.firstVersion || mcVersion > material.lastVersion) {
                warnOptional(warnings, fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return material;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown " + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    public static VBiome parseVBiome(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add(fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            errors.add("Invalid " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VBiome biome = VBiome.valueOf(normalized);
            if (mcVersion < biome.firstVersion || mcVersion > biome.lastVersion) {
                errors.add(fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return biome;
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown " + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    public static VBiome parseOptionalVBiome(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VBiome biome = VBiome.valueOf(normalized);
            if (mcVersion < biome.firstVersion || mcVersion > biome.lastVersion) {
                warnOptional(warnings, fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return biome;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown " + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    public static VSoundType parseSoundTypeOptional(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid " + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VSoundType sound = VSoundType.valueOf(normalized);
            if (mcVersion < sound.firstVersion || mcVersion > sound.lastVersion) {
                warnOptional(warnings, fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return sound;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown " + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    public static YamlSoundDefinition parseSoundDefinition(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings, boolean required
    ) {
        if (rawValue == null) {
            if (required) {
                warnOptional(warnings, fieldName + " is required in " + sourceFile.getPath());
            }
            return null;
        }
        if (rawValue instanceof String) {
            VSoundType sound = parseSoundTypeOptional(rawValue, fieldName, sourceFile, warnings);
            return sound == null ? null : new YamlSoundDefinition(sound, 1f, 1f);
        }
        ConfigurationSection section = rawValue instanceof ConfigurationSection ? (ConfigurationSection) rawValue : null;
        Map<?, ?> map = rawValue instanceof Map<?, ?> ? (Map<?, ?>) rawValue : null;
        if (section == null && map == null) {
            warnOptional(warnings, fieldName + " must be a string or map in " + sourceFile.getPath());
            return null;
        }

        Object rawSound = section != null ? section.get("sound") : map.get("sound");
        Object rawVolume = section != null ? section.get("volume") : map.get("volume");
        Object rawPitch = section != null ? section.get("pitch") : map.get("pitch");
        VSoundType sound = parseSoundTypeOptional(rawSound, fieldName + ".sound", sourceFile, warnings);
        Float volume = parseFloatInRange(rawVolume, 0.001f, Float.MAX_VALUE, fieldName + ".volume",
                sourceFile, warnings);
        Float pitch = parseFloatInRange(rawPitch, 0.001f, Float.MAX_VALUE, fieldName + ".pitch",
                sourceFile, warnings);
        if (sound == null) return null;
        return new YamlSoundDefinition(sound, volume == null ? 1f : volume, pitch == null ? 1f : pitch);
    }

    // ========== Color Parsing ==========

    public static String translateColors(
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
                    errors.add(fieldName + " has invalid hex color in " + sourceFile.getPath());
                    return null;
                }
                String hex = rawText.substring(ampIndex + 2, ampIndex + 8);
                if (!isHex(hex)) {
                    errors.add(fieldName + " has invalid hex color in " + sourceFile.getPath());
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

    public static String translateColorsOptional(
            String rawText, String fieldName, File sourceFile, List<String> warnings
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
                    warnOptional(warnings, fieldName + " has invalid hex color in " + sourceFile.getPath());
                    return null;
                }
                String hex = rawText.substring(ampIndex + 2, ampIndex + 8);
                if (!isHex(hex)) {
                    warnOptional(warnings, fieldName + " has invalid hex color in " + sourceFile.getPath());
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
                result.append('&').append(next);
                index = ampIndex + 2;
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

    // ========== Warning Utilities ==========

    /**
     * Adds an optional warning message with standard suffix.
     */
    public static void warnOptional(List<String> warnings, String message) {
        if (warnings == null) return;
        warnings.add("WARNING - " + message + ". (This field will be ignored.)");
    }
}
