package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.block.drop.SilkTouchRequirement;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

class YamlBlockReader {

    private static final int MIN_MINING_SPEED = -5;
    private static final int MAX_MINING_SPEED = 25;

    static List<YamlBlockDefinition> readBlocks(YamlPackDefinition pack, List<String> errors) {
        List<YamlBlockDefinition> blocks = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(pack.directory.toPath())) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml")) return;
                if (fileName.equals("pack.yml")) return;

                File file = path.toFile();
                List<YamlConfiguration> configs = YamlDocumentReader.loadDocuments(file, errors);
                for (YamlConfiguration config : configs) {
                    ConfigurationSection blockSection = config.getConfigurationSection("block");
                    if (blockSection == null) continue;

                    int errorCountBefore = errors.size();
                    YamlBlockDefinition block = parseBlockDefinition(pack, file, blockSection, errors);
                    if (errors.size() != errorCountBefore) return;
                    if (block != null) {
                        blocks.add(block);
                    }
                }
            });
        } catch (IOException ex) {
            errors.add("Failed to scan pack folder " + pack.directory.getPath() + ": " + ex.getMessage());
        }
        return blocks;
    }

    private static YamlBlockDefinition parseBlockDefinition(
            YamlPackDefinition pack,
            File file,
            ConfigurationSection blockSection,
            List<String> errors
    ) {
        String rawId = blockSection.getString("id");
        if (rawId == null || rawId.trim().isEmpty()) {
            errors.add("Missing block.id in " + file.getPath());
            return null;
        }

        ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors);
        if (parsedId == null) return null;

        int errorCountBefore = errors.size();

        ConfigurationSection requiresSection = getChildSection(blockSection, "requires", file, errors);
        if (!matchesRequires(requiresSection, file, errors)) {
            return null;
        }

        ConfigurationSection modelSection = getChildSection(blockSection, "model", file, errors);
        ConfigurationSection miningSection = getChildSection(blockSection, "mining_speed", file, errors);
        ConfigurationSection soundsSection = getChildSection(blockSection, "sounds", file, errors);

        YamlBlockModelDefinition modelDefinition = parseModel(modelSection, file, errors);
        YamlBlockMiningSpeedDefinition miningSpeed = parseMiningSpeed(miningSection, pack, file, errors);
        YamlBlockSoundsDefinition sounds = parseSounds(soundsSection, file, errors);
        List<YamlBlockDropDefinition> drops = parseDrops(blockSection.get("drops"), pack, file, errors);

        if (errors.size() != errorCountBefore) return null;

        return new YamlBlockDefinition(
                parsedId.fullId,
                parsedId.internalName,
                parsedId.name,
                pack.directory,
                file,
                modelDefinition,
                miningSpeed,
                sounds,
                drops
        );
    }

    private static YamlBlockModelDefinition parseModel(
            ConfigurationSection modelSection, File sourceFile, List<String> errors
    ) {
        if (modelSection == null) {
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }

        YamlBlockModelType type = parseModelType(modelSection.get("type"), sourceFile, errors);
        if (type == null) return null;

        if (type == YamlBlockModelType.SIMPLE) {
            String texture = parseOptionalString(modelSection.get("texture"), "model.texture", sourceFile, errors);
            return new YamlBlockModelDefinition(type, texture, null, null);
        }

        if (type == YamlBlockModelType.SIDED) {
            ConfigurationSection texturesSection = getChildSection(modelSection, "textures", sourceFile, errors);
            if (texturesSection == null) {
                errors.add("block.model.textures is required for type sided in " + sourceFile.getPath());
                return null;
            }
            Map<String, String> textures = new HashMap<>();
            String[] directions = { "north", "east", "south", "west", "up", "down" };
            for (String direction : directions) {
                String value = parseOptionalString(texturesSection.get(direction), "model.textures." + direction, sourceFile, errors);
                if (value == null) {
                    errors.add("block.model.textures." + direction + " is required in " + sourceFile.getPath());
                    return null;
                }
                textures.put(direction, value);
            }
            return new YamlBlockModelDefinition(type, null, textures, null);
        }

        String modelPath = parseOptionalString(modelSection.get("json"), "model.json", sourceFile, errors);
        if (modelPath == null) {
            modelPath = parseOptionalString(modelSection.get("model"), "model.model", sourceFile, errors);
        }
        if (modelPath == null) {
            errors.add("block.model.json is required for type custom in " + sourceFile.getPath());
            return null;
        }
        String editorTexture = parseRequiredString(modelSection.get("editor_texture"), "model.editor_texture", sourceFile, errors);
        ConfigurationSection texturesSection = getChildSection(modelSection, "textures", sourceFile, errors);
        if (texturesSection == null) {
            errors.add("block.model.textures is required for type custom in " + sourceFile.getPath());
            return null;
        }

        Map<String, String> texturePaths = new HashMap<>();
        for (String key : texturesSection.getKeys(false)) {
            String value = parseRequiredString(texturesSection.get(key), "model.textures." + key, sourceFile, errors);
            if (value == null) return null;
            texturePaths.put(key, value);
        }

        if (modelPath == null || editorTexture == null) return null;
        YamlBlockCustomModelDefinition customModel = new YamlBlockCustomModelDefinition(modelPath, editorTexture, texturePaths);
        return new YamlBlockModelDefinition(type, null, null, customModel);
    }

    private static YamlBlockModelType parseModelType(Object rawType, File sourceFile, List<String> errors) {
        if (rawType == null) return YamlBlockModelType.SIMPLE;
        if (!(rawType instanceof String)) {
            errors.add("block.model.type must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawType).trim();
        if (trimmed.isEmpty()) {
            errors.add("block.model.type must not be empty in " + sourceFile.getPath());
            return null;
        }
        switch (trimmed.toLowerCase(Locale.ROOT)) {
            case "simple":
                return YamlBlockModelType.SIMPLE;
            case "sided":
                return YamlBlockModelType.SIDED;
            case "custom":
                return YamlBlockModelType.CUSTOM;
            default:
                errors.add("Unknown block.model.type '" + trimmed + "' in " + sourceFile.getPath());
                return null;
        }
    }

    private static YamlBlockMiningSpeedDefinition parseMiningSpeed(
            ConfigurationSection miningSection, YamlPackDefinition pack, File sourceFile, List<String> errors
    ) {
        if (miningSection == null) return null;

        Integer defaultValue = parseInteger(miningSection.get("default"), MIN_MINING_SPEED, MAX_MINING_SPEED,
                "mining_speed.default", sourceFile, errors);

        List<YamlBlockVanillaMiningSpeedEntry> vanillaEntries = new ArrayList<>();
        if (miningSection.isSet("vanilla")) {
            Object rawVanilla = miningSection.get("vanilla");
            if (!(rawVanilla instanceof List<?>)) {
                errors.add("block.mining_speed.vanilla must be a list in " + sourceFile.getPath());
                return null;
            }
            int index = 0;
            for (Object entry : (List<?>) rawVanilla) {
                if (!(entry instanceof Map<?, ?>)) {
                    errors.add("block.mining_speed.vanilla entry " + index + " must be a map in " + sourceFile.getPath());
                    return null;
                }
                Map<?, ?> map = (Map<?, ?>) entry;
                Object materialValue = map.get("tool");
                if (materialValue == null) materialValue = map.get("material");
                VMaterial material = parseVMaterial(materialValue, "mining_speed.vanilla[" + index + "].tool", sourceFile, errors);
                Integer value = parseInteger(map.get("value"), MIN_MINING_SPEED, MAX_MINING_SPEED,
                        "mining_speed.vanilla[" + index + "].value", sourceFile, errors);
                Boolean allowCustom = parseBoolean(map.get("allow_custom_items"),
                        "mining_speed.vanilla[" + index + "].allow_custom_items", sourceFile, errors);
                if (material == null || value == null) return null;
                vanillaEntries.add(new YamlBlockVanillaMiningSpeedEntry(material, value, allowCustom == null || allowCustom));
                index++;
            }
        }

        List<YamlBlockCustomMiningSpeedEntry> customEntries = new ArrayList<>();
        if (miningSection.isSet("custom")) {
            Object rawCustom = miningSection.get("custom");
            if (!(rawCustom instanceof List<?>)) {
                errors.add("block.mining_speed.custom must be a list in " + sourceFile.getPath());
                return null;
            }
            int index = 0;
            for (Object entry : (List<?>) rawCustom) {
                if (!(entry instanceof Map<?, ?>)) {
                    errors.add("block.mining_speed.custom entry " + index + " must be a map in " + sourceFile.getPath());
                    return null;
                }
                Map<?, ?> map = (Map<?, ?>) entry;
                Object itemValue = map.get("item");
                String itemId = parseRequiredString(itemValue, "mining_speed.custom[" + index + "].item", sourceFile, errors);
                Integer value = parseInteger(map.get("value"), MIN_MINING_SPEED, MAX_MINING_SPEED,
                        "mining_speed.custom[" + index + "].value", sourceFile, errors);
                if (itemId == null || value == null) return null;
                ParsedId parsedItem = parseId(itemId, pack.namespace, sourceFile, errors);
                if (parsedItem == null) return null;
                customEntries.add(new YamlBlockCustomMiningSpeedEntry(parsedItem.internalName, value));
                index++;
            }
        }

        if (defaultValue == null && vanillaEntries.isEmpty() && customEntries.isEmpty()) return null;
        return new YamlBlockMiningSpeedDefinition(defaultValue, vanillaEntries, customEntries);
    }

    private static YamlBlockSoundsDefinition parseSounds(
            ConfigurationSection soundsSection, File sourceFile, List<String> errors
    ) {
        if (soundsSection == null) return null;

        YamlSoundDefinition leftClick = parseSoundEntry(soundsSection.get("left_click"), "sounds.left_click", sourceFile, errors);
        YamlSoundDefinition rightClick = parseSoundEntry(soundsSection.get("right_click"), "sounds.right_click", sourceFile, errors);
        YamlSoundDefinition breakSound = parseSoundEntry(soundsSection.get("break"), "sounds.break", sourceFile, errors);
        YamlSoundDefinition step = parseSoundEntry(soundsSection.get("step"), "sounds.step", sourceFile, errors);

        if (leftClick == null && rightClick == null && breakSound == null && step == null) return null;
        return new YamlBlockSoundsDefinition(leftClick, rightClick, breakSound, step);
    }

    private static YamlSoundDefinition parseSoundEntry(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        if (rawValue instanceof String) {
            VSoundType sound = parseSoundType(rawValue, fieldName, sourceFile, errors);
            return sound == null ? null : new YamlSoundDefinition(sound, 1f, 1f);
        }
        if (!(rawValue instanceof Map<?, ?>)) {
            errors.add("block." + fieldName + " must be a string or map in " + sourceFile.getPath());
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) rawValue;
        VSoundType sound = parseSoundType(map.get("sound"), fieldName + ".sound", sourceFile, errors);
        Float volume = parseFloat(map.get("volume"), 0.001f, Float.MAX_VALUE, fieldName + ".volume", sourceFile, errors);
        Float pitch = parseFloat(map.get("pitch"), 0.001f, Float.MAX_VALUE, fieldName + ".pitch", sourceFile, errors);
        if (sound == null) return null;
        return new YamlSoundDefinition(sound, volume == null ? 1f : volume, pitch == null ? 1f : pitch);
    }

    private static List<YamlBlockDropDefinition> parseDrops(
            Object rawDrops, YamlPackDefinition pack, File sourceFile, List<String> errors
    ) {
        if (rawDrops == null) return Collections.emptyList();
        if (!(rawDrops instanceof List<?>)) {
            errors.add("block.drops must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlBlockDropDefinition> drops = new ArrayList<>();
        int dropIndex = 0;
        for (Object rawEntry : (List<?>) rawDrops) {
            if (!(rawEntry instanceof Map<?, ?>)) {
                errors.add("block.drops entry " + dropIndex + " must be a map in " + sourceFile.getPath());
                return Collections.emptyList();
            }
            Map<?, ?> map = (Map<?, ?>) rawEntry;

            List<YamlBlockDropOutputDefinition> outputs = parseDropOutputs(map.get("outputs"),
                    pack, sourceFile, errors, dropIndex);
            if (outputs.isEmpty()) return Collections.emptyList();

            SilkTouchRequirement silkTouch = parseSilkTouch(map.get("silk_touch"), sourceFile, errors);
            Integer minFortune = null;
            Integer maxFortune = null;
            Object fortuneValue = map.get("fortune");
            if (fortuneValue != null) {
                if (!(fortuneValue instanceof Map<?, ?>)) {
                    errors.add("block.drops[" + dropIndex + "].fortune must be a map in " + sourceFile.getPath());
                    return Collections.emptyList();
                }
                Map<?, ?> fortuneMap = (Map<?, ?>) fortuneValue;
                minFortune = parseInteger(fortuneMap.get("min"), 0, Integer.MAX_VALUE,
                        "drops[" + dropIndex + "].fortune.min", sourceFile, errors);
                maxFortune = parseInteger(fortuneMap.get("max"), 0, Integer.MAX_VALUE,
                        "drops[" + dropIndex + "].fortune.max", sourceFile, errors);
                if (minFortune != null && maxFortune != null && maxFortune < minFortune) {
                    errors.add("block.drops[" + dropIndex + "].fortune.max must be >= min in " + sourceFile.getPath());
                    return Collections.emptyList();
                }
            }

            Boolean cancelNormalDrops = parseBoolean(map.get("cancel_normal_drops"),
                    "drops[" + dropIndex + "].cancel_normal_drops", sourceFile, errors);
            YamlRequiredItemsDefinition requiredItems = parseRequiredItems(map.get("required_held_items"),
                    pack, sourceFile, errors, dropIndex);
            YamlAllowedBiomesDefinition allowedBiomes = parseAllowedBiomes(map.get("biomes"),
                    sourceFile, errors, dropIndex);

            drops.add(new YamlBlockDropDefinition(outputs, silkTouch, minFortune, maxFortune,
                    cancelNormalDrops, requiredItems, allowedBiomes));
            dropIndex++;
        }

        return drops;
    }

    private static List<YamlBlockDropOutputDefinition> parseDropOutputs(
            Object rawOutputs, YamlPackDefinition pack, File sourceFile, List<String> errors, int dropIndex
    ) {
        if (rawOutputs == null) {
            errors.add("block.drops[" + dropIndex + "].outputs is required in " + sourceFile.getPath());
            return Collections.emptyList();
        }
        if (!(rawOutputs instanceof List<?>)) {
            errors.add("block.drops[" + dropIndex + "].outputs must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlBlockDropOutputDefinition> outputs = new ArrayList<>();
        int outputIndex = 0;
        for (Object rawEntry : (List<?>) rawOutputs) {
            if (!(rawEntry instanceof Map<?, ?>)) {
                errors.add("block.drops[" + dropIndex + "].outputs[" + outputIndex + "] must be a map in "
                        + sourceFile.getPath());
                return Collections.emptyList();
            }
            Map<?, ?> map = (Map<?, ?>) rawEntry;

            String itemId = parseOptionalString(map.get("item"), "drops[" + dropIndex + "].outputs[" + outputIndex + "].item", sourceFile, errors);
            Object rawMaterial = map.get("material");
            VMaterial material = rawMaterial != null ? parseVMaterial(rawMaterial,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].material", sourceFile, errors) : null;
            if ((itemId == null) == (material == null)) {
                errors.add("block.drops[" + dropIndex + "].outputs[" + outputIndex + "] must define exactly one of item or material in "
                        + sourceFile.getPath());
                return Collections.emptyList();
            }

            Integer amount = parseInteger(map.get("amount"), 1, 64,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].amount", sourceFile, errors);
            Double chance = parseDouble(map.get("chance"), 0.0, 100.0,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].chance", sourceFile, errors);
            if (amount == null) amount = 1;
            if (chance == null) chance = 100.0;

            String customInternalName = null;
            if (itemId != null) {
                ParsedId parsedItem = parseId(itemId, pack.namespace, sourceFile, errors);
                if (parsedItem == null) return Collections.emptyList();
                customInternalName = parsedItem.internalName;
            }

            outputs.add(new YamlBlockDropOutputDefinition(material, customInternalName, amount, chance));
            outputIndex++;
        }

        return outputs;
    }

    private static YamlRequiredItemsDefinition parseRequiredItems(
            Object rawValue, YamlPackDefinition pack, File sourceFile, List<String> errors, int dropIndex
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof Map<?, ?>)) {
            errors.add("block.drops[" + dropIndex + "].required_held_items must be a map in " + sourceFile.getPath());
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) rawValue;

        Boolean enabled = parseBoolean(map.get("enabled"),
                "drops[" + dropIndex + "].required_held_items.enabled", sourceFile, errors);
        Boolean invert = parseBoolean(map.get("invert"),
                "drops[" + dropIndex + "].required_held_items.invert", sourceFile, errors);

        List<YamlRequiredVanillaItemDefinition> vanillaItems = new ArrayList<>();
        Object rawVanilla = map.get("vanilla");
        if (rawVanilla != null) {
            if (!(rawVanilla instanceof List<?>)) {
                errors.add("block.drops[" + dropIndex + "].required_held_items.vanilla must be a list in " + sourceFile.getPath());
                return null;
            }
            int index = 0;
            for (Object entry : (List<?>) rawVanilla) {
                if (!(entry instanceof Map<?, ?>)) {
                    errors.add("block.drops[" + dropIndex + "].required_held_items.vanilla[" + index + "] must be a map in "
                            + sourceFile.getPath());
                    return null;
                }
                Map<?, ?> entryMap = (Map<?, ?>) entry;
                VMaterial material = parseVMaterial(entryMap.get("material"),
                        "drops[" + dropIndex + "].required_held_items.vanilla[" + index + "].material", sourceFile, errors);
                Boolean allowCustom = parseBoolean(entryMap.get("allow_custom_items"),
                        "drops[" + dropIndex + "].required_held_items.vanilla[" + index + "].allow_custom_items", sourceFile, errors);
                if (material == null) return null;
                vanillaItems.add(new YamlRequiredVanillaItemDefinition(material, allowCustom == null || allowCustom));
                index++;
            }
        }

        List<String> customItems = new ArrayList<>();
        Object rawCustom = map.get("custom");
        if (rawCustom != null) {
            if (!(rawCustom instanceof List<?>)) {
                errors.add("block.drops[" + dropIndex + "].required_held_items.custom must be a list in " + sourceFile.getPath());
                return null;
            }
            int index = 0;
            for (Object entry : (List<?>) rawCustom) {
                String id = parseRequiredString(entry, "drops[" + dropIndex + "].required_held_items.custom[" + index + "]",
                        sourceFile, errors);
                if (id == null) return null;
                ParsedId parsedId = parseId(id, pack.namespace, sourceFile, errors);
                if (parsedId == null) return null;
                customItems.add(parsedId.internalName);
                index++;
            }
        }

        return new YamlRequiredItemsDefinition(enabled == null || enabled, invert != null && invert, vanillaItems, customItems);
    }

    private static YamlAllowedBiomesDefinition parseAllowedBiomes(
            Object rawValue, File sourceFile, List<String> errors, int dropIndex
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof Map<?, ?>)) {
            errors.add("block.drops[" + dropIndex + "].biomes must be a map in " + sourceFile.getPath());
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) rawValue;
        List<VBiome> whitelist = parseBiomeList(map.get("whitelist"),
                "drops[" + dropIndex + "].biomes.whitelist", sourceFile, errors);
        List<VBiome> blacklist = parseBiomeList(map.get("blacklist"),
                "drops[" + dropIndex + "].biomes.blacklist", sourceFile, errors);
        if (whitelist == null || blacklist == null) return null;
        return new YamlAllowedBiomesDefinition(whitelist, blacklist);
    }

    private static List<VBiome> parseBiomeList(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            errors.add("block." + fieldName + " must be a list in " + sourceFile.getPath());
            return null;
        }

        List<VBiome> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            VBiome biome = parseVBiome(entry, fieldName + "[" + index + "]", sourceFile, errors);
            if (biome == null) return null;
            result.add(biome);
            index++;
        }
        return result;
    }

    private static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors
    ) {
        String namespace;
        String name;

        int colonIndex = rawId.indexOf(':');
        if (colonIndex >= 0) {
            if (rawId.indexOf(':', colonIndex + 1) >= 0) {
                errors.add("Invalid id '" + rawId + "' in " + sourceFile.getPath() + ": too many ':' characters");
                return null;
            }
            namespace = rawId.substring(0, colonIndex);
            name = rawId.substring(colonIndex + 1);
        } else {
            namespace = defaultNamespace;
            name = rawId;
        }

        if (namespace == null || namespace.isEmpty()) {
            errors.add("Missing namespace for id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }
        if (name.isEmpty()) {
            errors.add("Missing name for id '" + rawId + "' in " + sourceFile.getPath());
            return null;
        }

        try {
            Validation.safeName(namespace);
            Validation.safeName(name);
        } catch (ValidationException | ProgrammingValidationException ex) {
            errors.add("Invalid id '" + rawId + "' in " + sourceFile.getPath() + ": " + ex.getMessage());
            return null;
        }

        String fullId = namespace + ":" + name;
        String internalName = namespace + "_" + name;
        return new ParsedId(fullId, internalName, name);
    }

    private static boolean matchesRequires(
            ConfigurationSection requiresSection, File sourceFile, List<String> errors
    ) {
        if (requiresSection == null) return true;
        String raw = requiresSection.getString("mc");
        if (raw == null || raw.trim().isEmpty()) return true;
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

        Integer version = parseMcVersion(versionPart.trim(), sourceFile, errors);
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
                errors.add("Invalid requires.mc operator in " + sourceFile.getPath());
                return true;
        }
    }

    private static Integer parseMcVersion(String raw, File sourceFile, List<String> errors) {
        if (raw == null || raw.isEmpty()) {
            errors.add("requires.mc is empty in " + sourceFile.getPath());
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("1.")) trimmed = trimmed.substring(2);
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex >= 0) trimmed = trimmed.substring(0, dotIndex);
        if (!isInteger(trimmed)) {
            errors.add("Invalid requires.mc '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        int version = Integer.parseInt(trimmed);
        if (version < MCVersions.FIRST_VERSION || version > MCVersions.LAST_VERSION) {
            errors.add("Unsupported requires.mc '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
        return version;
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> errors
    ) {
        if (parent == null) return null;
        if (!parent.isSet(name)) return null;
        ConfigurationSection section = parent.getConfigurationSection(name);
        if (section == null) {
            errors.add("block." + name + " must be a map in " + sourceFile.getPath());
            return null;
        }
        return section;
    }

    private static String parseRequiredString(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) {
            errors.add("block." + fieldName + " is required in " + sourceFile.getPath());
            return null;
        }
        if (!(rawValue instanceof String)) {
            errors.add("block." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) {
            errors.add("block." + fieldName + " must not be empty in " + sourceFile.getPath());
            return null;
        }
        return trimmed;
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String)) {
            errors.add("block." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) return null;
        return trimmed;
    }

    private static Integer parseInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return null;
        Integer value = null;
        if (rawValue instanceof Number) {
            double numeric = ((Number) rawValue).doubleValue();
            if (numeric % 1 != 0) {
                errors.add("block." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = (int) numeric;
        } else if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (!isInteger(trimmed)) {
                errors.add("block." + fieldName + " must be an integer in " + sourceFile.getPath());
                return null;
            }
            value = Integer.parseInt(trimmed);
        } else {
            errors.add("block." + fieldName + " must be an integer in " + sourceFile.getPath());
            return null;
        }

        if (value < min || value > max) {
            errors.add("block." + fieldName + " must be between " + min + " and " + max + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    private static Double parseDouble(
            Object rawValue, double min, double max, String fieldName, File sourceFile, List<String> errors
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
                errors.add("block." + fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            errors.add("block." + fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Double.isFinite(value)) {
            errors.add("block." + fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }
        if (value < min || value > max) {
            errors.add("block." + fieldName + " must be between " + min + " and " + max + " in " + sourceFile.getPath());
            return null;
        }
        return value;
    }

    private static Float parseFloat(
            Object rawValue, float min, float max, String fieldName, File sourceFile, List<String> errors
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
                errors.add("block." + fieldName + " must be a number in " + sourceFile.getPath());
                return null;
            }
        } else {
            errors.add("block." + fieldName + " must be a number in " + sourceFile.getPath());
            return null;
        }

        if (!Float.isFinite(value)) {
            errors.add("block." + fieldName + " must be finite in " + sourceFile.getPath());
            return null;
        }
        if (value < min || value > max) {
            errors.add("block." + fieldName + " must be between " + min + " and " + max + " in " + sourceFile.getPath());
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
        errors.add("block." + fieldName + " must be true or false in " + sourceFile.getPath());
        return null;
    }

    private static VMaterial parseVMaterial(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add("block." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            errors.add("Invalid block." + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VMaterial material = VMaterial.valueOf(normalized);
            if (mcVersion < material.firstVersion || mcVersion > material.lastVersion) {
                errors.add("block." + fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return material;
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown block." + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static VBiome parseVBiome(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add("block." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            errors.add("Invalid block." + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VBiome biome = VBiome.valueOf(normalized);
            if (mcVersion < biome.firstVersion || mcVersion > biome.lastVersion) {
                errors.add("block." + fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return biome;
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown block." + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static VSoundType parseSoundType(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add("block." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            errors.add("Invalid block." + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VSoundType sound = VSoundType.valueOf(normalized);
            if (mcVersion < sound.firstVersion || mcVersion > sound.lastVersion) {
                errors.add("block." + fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return sound;
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown block." + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static SilkTouchRequirement parseSilkTouch(
            Object rawValue, File sourceFile, List<String> errors
    ) {
        if (rawValue == null) return SilkTouchRequirement.OPTIONAL;
        if (!(rawValue instanceof String)) {
            errors.add("block.drops.silk_touch must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = ((String) rawValue).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return SilkTouchRequirement.OPTIONAL;
        try {
            return SilkTouchRequirement.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown block.drops.silk_touch '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
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
