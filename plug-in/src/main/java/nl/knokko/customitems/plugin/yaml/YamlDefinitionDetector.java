package nl.knokko.customitems.plugin.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class YamlDefinitionDetector {

    enum DefinitionType {
        ITEM,
        BLOCK,
        RECIPE,
        PROJECTILE,
        PROJECTILE_COVER,
        ABILITY,
        POWER
    }

    private static final Map<String, DefinitionType> EXPLICIT_KEY_TO_TYPE;
    private static final Map<String, DefinitionType> FILE_KEYWORD_TO_TYPE;
    private static final Set<DefinitionType> ALL_DEFINITION_TYPES;
    private static final Set<DefinitionType> RESOURCEPACK_DEFINITION_TYPES;

    static {
        Map<String, DefinitionType> explicit = new LinkedHashMap<>();
        explicit.put("item", DefinitionType.ITEM);
        explicit.put("block", DefinitionType.BLOCK);
        explicit.put("recipe", DefinitionType.RECIPE);
        explicit.put("projectile", DefinitionType.PROJECTILE);
        explicit.put("projectile_cover", DefinitionType.PROJECTILE_COVER);
        explicit.put("projectile-cover", DefinitionType.PROJECTILE_COVER);
        explicit.put("ability", DefinitionType.ABILITY);
        explicit.put("abilities", DefinitionType.ABILITY);
        explicit.put("power", DefinitionType.POWER);
        explicit.put("powers", DefinitionType.POWER);
        EXPLICIT_KEY_TO_TYPE = Collections.unmodifiableMap(explicit);

        Map<String, DefinitionType> fileKeywords = new LinkedHashMap<>();
        fileKeywords.put("item", DefinitionType.ITEM);
        fileKeywords.put("items", DefinitionType.ITEM);
        fileKeywords.put("block", DefinitionType.BLOCK);
        fileKeywords.put("blocks", DefinitionType.BLOCK);
        fileKeywords.put("recipe", DefinitionType.RECIPE);
        fileKeywords.put("recipes", DefinitionType.RECIPE);
        fileKeywords.put("projectile", DefinitionType.PROJECTILE);
        fileKeywords.put("projectiles", DefinitionType.PROJECTILE);
        fileKeywords.put("projectile_cover", DefinitionType.PROJECTILE_COVER);
        fileKeywords.put("projectile_covers", DefinitionType.PROJECTILE_COVER);
        fileKeywords.put("projectile-cover", DefinitionType.PROJECTILE_COVER);
        fileKeywords.put("projectile-covers", DefinitionType.PROJECTILE_COVER);
        fileKeywords.put("ability", DefinitionType.ABILITY);
        fileKeywords.put("abilities", DefinitionType.ABILITY);
        fileKeywords.put("power", DefinitionType.POWER);
        fileKeywords.put("powers", DefinitionType.POWER);
        FILE_KEYWORD_TO_TYPE = Collections.unmodifiableMap(fileKeywords);

        ALL_DEFINITION_TYPES = Collections.unmodifiableSet(EnumSet.allOf(DefinitionType.class));
        RESOURCEPACK_DEFINITION_TYPES = Collections.unmodifiableSet(EnumSet.of(
                DefinitionType.ITEM,
                DefinitionType.BLOCK,
                DefinitionType.RECIPE,
                DefinitionType.PROJECTILE,
                DefinitionType.PROJECTILE_COVER
        ));
    }

    private YamlDefinitionDetector() {}

    static Set<DefinitionType> allDefinitionTypes() {
        return ALL_DEFINITION_TYPES;
    }

    static Set<DefinitionType> resourcepackDefinitionTypes() {
        return RESOURCEPACK_DEFINITION_TYPES;
    }

    static boolean hasExplicitDefinitionRoot(YamlConfiguration config) {
        if (config == null) return false;
        for (String key : EXPLICIT_KEY_TO_TYPE.keySet()) {
            if (!config.isSet(key)) continue;
            if ("abilities".equals(key)) {
                if (isAbilityDefinitionRootValue(config.get(key))) return true;
                continue;
            }
            if ("powers".equals(key)) {
                if (isPowerDefinitionRootValue(config.get(key))) return true;
                continue;
            }
            return true;
        }
        return false;
    }

    static boolean shouldUseImplicitRoot(
            YamlConfiguration config, File sourceFile, DefinitionType expectedType
    ) {
        if (config == null || sourceFile == null || expectedType == null) return false;
        if (hasExplicitDefinitionRoot(config)) return false;
        DefinitionType inferred = determineFileDefinitionType(sourceFile.getName());
        return inferred == expectedType;
    }

    static DefinitionType determineFileDefinitionType(File sourceFile) {
        if (sourceFile == null) return null;
        return determineFileDefinitionType(sourceFile.getName());
    }

    static DefinitionType determineFileDefinitionType(String fileNameOrPath) {
        if (fileNameOrPath == null) return null;

        String name = fileNameOrPath;
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex + 1 < name.length()) {
            name = name.substring(slashIndex + 1);
        }

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        String lowered = name.toLowerCase(Locale.ROOT);
        return FILE_KEYWORD_TO_TYPE.get(lowered);
    }

    static DefinitionType detectExplicitTypeFromFirstMeaningfulLine(String firstMeaningfulLine) {
        if (firstMeaningfulLine == null) return null;
        String trimmed = firstMeaningfulLine.trim();
        if (trimmed.isEmpty()) return null;

        if (trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).trim();
            if (trimmed.isEmpty()) return null;
        }

        String lowered = trimmed.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, DefinitionType> entry : EXPLICIT_KEY_TO_TYPE.entrySet()) {
            if (lowered.startsWith(entry.getKey() + ":")) {
                return entry.getValue();
            }
        }
        return null;
    }

    static boolean looksLikeYamlDefinition(
            String fileNameOrPath, String firstMeaningfulLine, Set<DefinitionType> allowedTypes
    ) {
        if (allowedTypes == null || allowedTypes.isEmpty()) return false;
        if (firstMeaningfulLine == null || firstMeaningfulLine.trim().isEmpty()) return false;

        DefinitionType explicit = detectExplicitTypeFromFirstMeaningfulLine(firstMeaningfulLine);
        if (explicit != null) {
            return allowedTypes.contains(explicit);
        }

        DefinitionType byFileName = determineFileDefinitionType(fileNameOrPath);
        return byFileName != null && allowedTypes.contains(byFileName);
    }

    static boolean isAbilityDefinitionRootValue(Object rawValue) {
        return isDefinitionListOrMap(rawValue);
    }

    static boolean isPowerDefinitionRootValue(Object rawValue) {
        return isDefinitionListOrMap(rawValue);
    }

    private static boolean isDefinitionListOrMap(Object rawValue) {
        if (isMapLike(rawValue)) return true;
        if (!(rawValue instanceof List<?>)) return false;

        for (Object entry : (List<?>) rawValue) {
            if (isMapLike(entry)) return true;
        }
        return false;
    }

    private static boolean isMapLike(Object rawValue) {
        return rawValue instanceof ConfigurationSection || rawValue instanceof Map<?, ?>;
    }
}
