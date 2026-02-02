package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.plugin.yaml.YamlParseUtils.ParsedId;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

class YamlProjectileCoverReader {

    static List<YamlProjectileCoverDefinition> readProjectileCovers(
            YamlPackDefinition pack, List<String> errors, List<String> warnings
    ) {
        List<YamlProjectileCoverDefinition> covers = new ArrayList<>();
        forEachYamlDocument(pack, errors, (file, config) -> {
            ConfigurationSection coverSection = config.getConfigurationSection("projectile_cover");
            if (coverSection == null) coverSection = config.getConfigurationSection("projectile-cover");
            if (coverSection == null) return;

            int errorCountBefore = errors.size();
            YamlProjectileCoverDefinition cover = parseCoverDefinition(pack, file, coverSection, errors, warnings);
            if (errors.size() != errorCountBefore) return;
            if (cover != null) {
                covers.add(cover);
            }
        });
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
        return YamlParseUtils.parseId(rawId, defaultNamespace, sourceFile, errors, "projectile_cover.id");
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.getChildSection(parent, name, "projectile_cover.", sourceFile, warnings);
    }

    private static boolean matchesRequires(
            ConfigurationSection requiresSection, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.matchesRequires(requiresSection, "projectile_cover.requires", sourceFile, warnings);
    }

    private static Integer parseOptionalInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseInteger(rawValue, min, max, "projectile_cover." + fieldName, sourceFile, warnings);
    }

    private static Double parseOptionalDouble(
            Object rawValue, double min, double max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseDoubleInRange(rawValue, min, max, "projectile_cover." + fieldName, sourceFile, warnings);
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalString(rawValue, "projectile_cover." + fieldName, sourceFile, warnings);
    }
}
