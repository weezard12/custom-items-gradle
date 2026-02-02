package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.block.drop.SilkTouchRequirement;
import nl.knokko.customitems.drops.VBiome;
import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.plugin.yaml.YamlParseUtils.ParsedId;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

class YamlBlockReader {

    private static final int MIN_MINING_SPEED = -5;
    private static final int MAX_MINING_SPEED = 25;

    static List<YamlBlockDefinition> readBlocks(
            YamlPackDefinition pack, List<String> errors, List<String> warnings
    ) {
        List<YamlBlockDefinition> blocks = new ArrayList<>();
        forEachYamlDocument(pack, errors, (file, config) -> {
            ConfigurationSection blockSection = config.getConfigurationSection("block");
            if (blockSection == null) return;

            int errorCountBefore = errors.size();
            YamlBlockDefinition block = parseBlockDefinition(pack, file, blockSection, errors, warnings);
            if (errors.size() != errorCountBefore) return;
            if (block != null) {
                blocks.add(block);
            }
        });
        return blocks;
    }

    private static YamlBlockDefinition parseBlockDefinition(
            YamlPackDefinition pack,
            File file,
            ConfigurationSection blockSection,
            List<String> errors,
            List<String> warnings
    ) {
        String rawId = blockSection.getString("id");
        if (rawId == null || rawId.trim().isEmpty()) {
            errors.add("Missing block.id in " + file.getPath());
            return null;
        }

        ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors);
        if (parsedId == null) return null;

        int errorCountBefore = errors.size();

        ConfigurationSection requiresSection = getChildSection(blockSection, "requires", file, errors, warnings);
        if (!matchesRequires(requiresSection, file, warnings)) {
            return null;
        }

        ConfigurationSection modelSection = getChildSection(blockSection, "model", file, errors, warnings);
        ConfigurationSection miningSection = getChildSection(blockSection, "mining_speed", file, errors, warnings);
        ConfigurationSection soundsSection = getChildSection(blockSection, "sounds", file, errors, warnings);

        YamlBlockModelDefinition modelDefinition = parseModel(modelSection, file, errors, warnings);
        YamlBlockMiningSpeedDefinition miningSpeed = parseMiningSpeed(miningSection, pack, file, errors, warnings);
        YamlBlockSoundsDefinition sounds = parseSounds(soundsSection, file, warnings);
        List<YamlBlockDropDefinition> drops = parseDrops(blockSection.get("drops"), pack, file, errors, warnings);

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
            ConfigurationSection modelSection, File sourceFile, List<String> errors, List<String> warnings
    ) {
        if (modelSection == null) {
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }

        YamlBlockModelType type = parseModelType(modelSection.get("type"), sourceFile, warnings);
        if (type == null) {
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }

        if (type == YamlBlockModelType.SIMPLE) {
            String texture = parseOptionalString(modelSection.get("texture"), "model.texture", sourceFile, warnings);
            return new YamlBlockModelDefinition(type, texture, null, null);
        }

        if (type == YamlBlockModelType.SIDED) {
            ConfigurationSection texturesSection = getChildSection(modelSection, "textures", sourceFile, errors, warnings);
            if (texturesSection == null) {
                if (!modelSection.isSet("textures")) {
                    warnOptional(warnings, "block.model.textures is required for type sided in " + sourceFile.getPath());
                }
                return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
            }
            Map<String, String> textures = new HashMap<>();
            String[] directions = { "north", "east", "south", "west", "up", "down" };
            for (String direction : directions) {
                String value = parseOptionalString(texturesSection.get(direction), "model.textures." + direction, sourceFile, warnings);
                if (value == null) {
                    if (!texturesSection.isSet(direction)) {
                        warnOptional(warnings, "block.model.textures." + direction + " is required for type sided in "
                                + sourceFile.getPath());
                    }
                    return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
                }
                textures.put(direction, value);
            }
            return new YamlBlockModelDefinition(type, null, textures, null);
        }

        boolean hasJson = modelSection.isSet("json");
        boolean hasModel = modelSection.isSet("model");
        String modelPath = parseOptionalString(modelSection.get("json"), "model.json", sourceFile, warnings);
        if (modelPath == null) {
            modelPath = parseOptionalString(modelSection.get("model"), "model.model", sourceFile, warnings);
        }
        if (modelPath == null) {
            if (!hasJson && !hasModel) {
                warnOptional(warnings, "block.model.json is required for type custom in " + sourceFile.getPath());
            }
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }
        String editorTexture = parseOptionalString(
                modelSection.get("editor_texture"), "model.editor_texture", sourceFile, warnings
        );
        if (editorTexture == null) {
            if (!modelSection.isSet("editor_texture")) {
                warnOptional(warnings, "block.model.editor_texture is required for type custom in " + sourceFile.getPath());
            }
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }
        ConfigurationSection texturesSection = getChildSection(modelSection, "textures", sourceFile, errors, warnings);
        if (texturesSection == null) {
            if (!modelSection.isSet("textures")) {
                warnOptional(warnings, "block.model.textures is required for type custom in " + sourceFile.getPath());
            }
            return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
        }

        Map<String, String> texturePaths = new HashMap<>();
        for (String key : texturesSection.getKeys(false)) {
            String value = parseOptionalString(texturesSection.get(key), "model.textures." + key, sourceFile, warnings);
            if (value == null) {
                return new YamlBlockModelDefinition(YamlBlockModelType.SIMPLE, null, null, null);
            }
            texturePaths.put(key, value);
        }

        YamlBlockCustomModelDefinition customModel = new YamlBlockCustomModelDefinition(modelPath, editorTexture, texturePaths);
        return new YamlBlockModelDefinition(type, null, null, customModel);
    }

    private static YamlBlockModelType parseModelType(Object rawType, File sourceFile, List<String> warnings) {
        if (rawType == null) return YamlBlockModelType.SIMPLE;
        if (!(rawType instanceof String)) {
            warnOptional(warnings, "block.model.type must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawType).trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, "block.model.type must not be empty in " + sourceFile.getPath());
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
                warnOptional(warnings, "Unknown block.model.type '" + trimmed + "' in " + sourceFile.getPath());
                return null;
        }
    }

    private static YamlBlockMiningSpeedDefinition parseMiningSpeed(
            ConfigurationSection miningSection,
            YamlPackDefinition pack,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        if (miningSection == null) return null;

        Integer defaultValue = parseOptionalInteger(miningSection.get("default"), MIN_MINING_SPEED, MAX_MINING_SPEED,
                "mining_speed.default", sourceFile, warnings);

        List<YamlBlockVanillaMiningSpeedEntry> vanillaEntries = new ArrayList<>();
        if (miningSection.isSet("vanilla")) {
            Object rawVanilla = miningSection.get("vanilla");
            if (!(rawVanilla instanceof List<?>)) {
                warnOptional(warnings, "block.mining_speed.vanilla must be a list in " + sourceFile.getPath());
                rawVanilla = null;
            }
            if (rawVanilla instanceof List<?>) {
                int index = 0;
                for (Object entry : (List<?>) rawVanilla) {
                    if (!(entry instanceof Map<?, ?>)) {
                        warnOptional(warnings, "block.mining_speed.vanilla entry " + index + " must be a map in "
                                + sourceFile.getPath());
                        index++;
                        continue;
                    }
                    Map<?, ?> map = (Map<?, ?>) entry;
                    Object materialValue = map.get("tool");
                    if (materialValue == null) materialValue = map.get("material");
                    VMaterial material = parseOptionalVMaterial(materialValue,
                            "mining_speed.vanilla[" + index + "].tool", sourceFile, warnings);
                    Object rawValue = map.containsKey("value") ? map.get("value") : map.get("speed");
                    String valueField = map.containsKey("value")
                            ? "mining_speed.vanilla[" + index + "].value"
                            : "mining_speed.vanilla[" + index + "].speed";
                    Integer value = parseOptionalInteger(rawValue, MIN_MINING_SPEED, MAX_MINING_SPEED,
                            valueField, sourceFile, warnings);
                    Boolean allowCustom = parseOptionalBoolean(map.get("allow_custom_items"),
                            "mining_speed.vanilla[" + index + "].allow_custom_items", sourceFile, warnings);
                    if (material == null || value == null) {
                        index++;
                        continue;
                    }
                    vanillaEntries.add(new YamlBlockVanillaMiningSpeedEntry(
                            material, value, allowCustom == null || allowCustom
                    ));
                    index++;
                }
            }
        }

        List<YamlBlockCustomMiningSpeedEntry> customEntries = new ArrayList<>();
        if (miningSection.isSet("custom")) {
            Object rawCustom = miningSection.get("custom");
            if (!(rawCustom instanceof List<?>)) {
                warnOptional(warnings, "block.mining_speed.custom must be a list in " + sourceFile.getPath());
                rawCustom = null;
            }
            if (rawCustom instanceof List<?>) {
                int index = 0;
                for (Object entry : (List<?>) rawCustom) {
                    if (!(entry instanceof Map<?, ?>)) {
                        warnOptional(warnings, "block.mining_speed.custom entry " + index + " must be a map in "
                                + sourceFile.getPath());
                        index++;
                        continue;
                    }
                    Map<?, ?> map = (Map<?, ?>) entry;
                    Object itemValue = map.get("item");
                    String itemId = parseOptionalString(itemValue, "mining_speed.custom[" + index + "].item",
                            sourceFile, warnings);
                    Object rawValue = map.containsKey("value") ? map.get("value") : map.get("speed");
                    String valueField = map.containsKey("value")
                            ? "mining_speed.custom[" + index + "].value"
                            : "mining_speed.custom[" + index + "].speed";
                    Integer value = parseOptionalInteger(rawValue, MIN_MINING_SPEED, MAX_MINING_SPEED,
                            valueField, sourceFile, warnings);
                    if (itemId == null || value == null) {
                        index++;
                        continue;
                    }
                    ParsedId parsedItem = parseOptionalId(itemId, pack.namespace, sourceFile, warnings);
                    if (parsedItem == null) {
                        index++;
                        continue;
                    }
                    customEntries.add(new YamlBlockCustomMiningSpeedEntry(parsedItem.internalName, value));
                    index++;
                }
            }
        }

        if (defaultValue == null && vanillaEntries.isEmpty() && customEntries.isEmpty()) return null;
        return new YamlBlockMiningSpeedDefinition(defaultValue, vanillaEntries, customEntries);
    }

    private static YamlBlockSoundsDefinition parseSounds(
            ConfigurationSection soundsSection, File sourceFile, List<String> warnings
    ) {
        if (soundsSection == null) return null;

        YamlSoundDefinition leftClick = parseSoundEntry(soundsSection.get("left_click"), "sounds.left_click",
                sourceFile, warnings);
        YamlSoundDefinition rightClick = parseSoundEntry(soundsSection.get("right_click"), "sounds.right_click",
                sourceFile, warnings);
        YamlSoundDefinition breakSound = parseSoundEntry(soundsSection.get("break"), "sounds.break",
                sourceFile, warnings);
        YamlSoundDefinition step = parseSoundEntry(soundsSection.get("step"), "sounds.step",
                sourceFile, warnings);

        if (leftClick == null && rightClick == null && breakSound == null && step == null) return null;
        return new YamlBlockSoundsDefinition(leftClick, rightClick, breakSound, step);
    }

    private static YamlSoundDefinition parseSoundEntry(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseSoundDefinition(rawValue, "block." + fieldName, sourceFile, warnings, false);
    }

    private static List<YamlBlockDropDefinition> parseDrops(
            Object rawDrops, YamlPackDefinition pack, File sourceFile, List<String> errors, List<String> warnings
    ) {
        if (rawDrops == null) return Collections.emptyList();
        if (!(rawDrops instanceof List<?>)) {
            warnOptional(warnings, "block.drops must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlBlockDropDefinition> drops = new ArrayList<>();
        int dropIndex = 0;
        for (Object rawEntry : (List<?>) rawDrops) {
            if (!(rawEntry instanceof Map<?, ?>)) {
                warnOptional(warnings, "block.drops entry " + dropIndex + " must be a map in " + sourceFile.getPath());
                dropIndex++;
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) rawEntry;

            List<YamlBlockDropOutputDefinition> outputs = parseDropOutputs(
                    map.get("outputs"), pack, sourceFile, errors, warnings, dropIndex
            );
            if (outputs == null || outputs.isEmpty()) {
                warnOptional(warnings, "block.drops[" + dropIndex + "].outputs is invalid in " + sourceFile.getPath());
                dropIndex++;
                continue;
            }

            SilkTouchRequirement silkTouch = parseSilkTouch(map.get("silk_touch"), sourceFile, warnings);
            Integer minFortune = null;
            Integer maxFortune = null;
            Object fortuneValue = map.get("fortune");
            if (fortuneValue != null) {
                if (!(fortuneValue instanceof Map<?, ?>)) {
                    warnOptional(warnings, "block.drops[" + dropIndex + "].fortune must be a map in " + sourceFile.getPath());
                } else {
                    Map<?, ?> fortuneMap = (Map<?, ?>) fortuneValue;
                    minFortune = parseOptionalInteger(fortuneMap.get("min"), 0, Integer.MAX_VALUE,
                            "drops[" + dropIndex + "].fortune.min", sourceFile, warnings);
                    maxFortune = parseOptionalInteger(fortuneMap.get("max"), 0, Integer.MAX_VALUE,
                            "drops[" + dropIndex + "].fortune.max", sourceFile, warnings);
                    if (minFortune != null && maxFortune != null && maxFortune < minFortune) {
                        warnOptional(warnings, "block.drops[" + dropIndex + "].fortune.max must be >= min in "
                                + sourceFile.getPath());
                        minFortune = null;
                        maxFortune = null;
                    }
                }
            }

            Boolean cancelNormalDrops = parseOptionalBoolean(map.get("cancel_normal_drops"),
                    "drops[" + dropIndex + "].cancel_normal_drops", sourceFile, warnings);
            YamlRequiredItemsDefinition requiredItems = parseRequiredItems(
                    map.get("required_held_items"), pack, sourceFile, warnings, dropIndex
            );
            YamlAllowedBiomesDefinition allowedBiomes = parseAllowedBiomes(
                    map.get("biomes"), sourceFile, warnings, dropIndex
            );

            drops.add(new YamlBlockDropDefinition(outputs, silkTouch, minFortune, maxFortune,
                    cancelNormalDrops, requiredItems, allowedBiomes));
            dropIndex++;
        }

        return drops;
    }

    private static List<YamlBlockDropOutputDefinition> parseDropOutputs(
            Object rawOutputs,
            YamlPackDefinition pack,
            File sourceFile,
            List<String> errors,
            List<String> warnings,
            int dropIndex
    ) {
        if (rawOutputs == null) {
            warnOptional(warnings, "block.drops[" + dropIndex + "].outputs is required in " + sourceFile.getPath());
            return Collections.emptyList();
        }
        if (!(rawOutputs instanceof List<?>)) {
            warnOptional(warnings, "block.drops[" + dropIndex + "].outputs must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlBlockDropOutputDefinition> outputs = new ArrayList<>();
        int outputIndex = 0;
        for (Object rawEntry : (List<?>) rawOutputs) {
            if (!(rawEntry instanceof Map<?, ?>)) {
                warnOptional(warnings, "block.drops[" + dropIndex + "].outputs[" + outputIndex + "] must be a map in "
                        + sourceFile.getPath());
                outputIndex++;
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) rawEntry;

            String itemId = parseOptionalString(map.get("item"),
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].item", sourceFile, warnings);
            Object rawMaterial = map.get("material");
            VMaterial material = rawMaterial != null ? parseOptionalVMaterial(rawMaterial,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].material", sourceFile, warnings) : null;
            if ((itemId == null) == (material == null)) {
                warnOptional(warnings, "block.drops[" + dropIndex + "].outputs[" + outputIndex
                        + "] must define exactly one of item or material in " + sourceFile.getPath());
                outputIndex++;
                continue;
            }

            Integer amount = parseOptionalInteger(map.get("amount"), 1, 64,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].amount", sourceFile, warnings);
            Double chance = parseOptionalDouble(map.get("chance"), 0.0, 100.0,
                    "drops[" + dropIndex + "].outputs[" + outputIndex + "].chance", sourceFile, warnings);
            if (amount == null) amount = 1;
            if (chance == null) chance = 100.0;

            String customInternalName = null;
            if (itemId != null) {
                ParsedId parsedItem = parseOptionalId(itemId, pack.namespace, sourceFile, warnings);
                if (parsedItem == null) {
                    outputIndex++;
                    continue;
                }
                customInternalName = parsedItem.internalName;
            }

            outputs.add(new YamlBlockDropOutputDefinition(material, customInternalName, amount, chance));
            outputIndex++;
        }

        return outputs;
    }

    private static YamlRequiredItemsDefinition parseRequiredItems(
            Object rawValue, YamlPackDefinition pack, File sourceFile, List<String> warnings, int dropIndex
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof Map<?, ?>)) {
            warnOptional(warnings, "block.drops[" + dropIndex + "].required_held_items must be a map in "
                    + sourceFile.getPath());
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) rawValue;

        Boolean enabled = parseOptionalBoolean(map.get("enabled"),
                "drops[" + dropIndex + "].required_held_items.enabled", sourceFile, warnings);
        Boolean invert = parseOptionalBoolean(map.get("invert"),
                "drops[" + dropIndex + "].required_held_items.invert", sourceFile, warnings);

        List<YamlRequiredVanillaItemDefinition> vanillaItems = new ArrayList<>();
        Object rawVanilla = map.get("vanilla");
        if (rawVanilla != null) {
            if (!(rawVanilla instanceof List<?>)) {
                warnOptional(warnings, "block.drops[" + dropIndex + "].required_held_items.vanilla must be a list in "
                        + sourceFile.getPath());
            } else {
                int index = 0;
                for (Object entry : (List<?>) rawVanilla) {
                    if (!(entry instanceof Map<?, ?>)) {
                        warnOptional(warnings, "block.drops[" + dropIndex + "].required_held_items.vanilla[" + index
                                + "] must be a map in " + sourceFile.getPath());
                        index++;
                        continue;
                    }
                    Map<?, ?> entryMap = (Map<?, ?>) entry;
                    VMaterial material = parseOptionalVMaterial(entryMap.get("material"),
                            "drops[" + dropIndex + "].required_held_items.vanilla[" + index + "].material",
                            sourceFile, warnings);
                    Boolean allowCustom = parseOptionalBoolean(entryMap.get("allow_custom_items"),
                            "drops[" + dropIndex + "].required_held_items.vanilla[" + index + "].allow_custom_items",
                            sourceFile, warnings);
                    if (material != null) {
                        vanillaItems.add(new YamlRequiredVanillaItemDefinition(
                                material, allowCustom == null || allowCustom
                        ));
                    }
                    index++;
                }
            }
        }

        List<String> customItems = new ArrayList<>();
        Object rawCustom = map.get("custom");
        if (rawCustom != null) {
            if (!(rawCustom instanceof List<?>)) {
                warnOptional(warnings, "block.drops[" + dropIndex + "].required_held_items.custom must be a list in "
                        + sourceFile.getPath());
            } else {
                int index = 0;
                for (Object entry : (List<?>) rawCustom) {
                    String id = parseOptionalString(entry,
                            "drops[" + dropIndex + "].required_held_items.custom[" + index + "]",
                            sourceFile, warnings);
                    if (id == null) {
                        index++;
                        continue;
                    }
                    ParsedId parsedId = parseOptionalId(id, pack.namespace, sourceFile, warnings);
                    if (parsedId != null) {
                        customItems.add(parsedId.internalName);
                    }
                    index++;
                }
            }
        }

        return new YamlRequiredItemsDefinition(enabled == null || enabled, invert != null && invert, vanillaItems, customItems);
    }

    private static YamlAllowedBiomesDefinition parseAllowedBiomes(
            Object rawValue, File sourceFile, List<String> warnings, int dropIndex
    ) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof Map<?, ?>)) {
            warnOptional(warnings, "block.drops[" + dropIndex + "].biomes must be a map in " + sourceFile.getPath());
            return null;
        }

        Map<?, ?> map = (Map<?, ?>) rawValue;
        List<VBiome> whitelist = parseBiomeList(map.get("whitelist"),
                "drops[" + dropIndex + "].biomes.whitelist", sourceFile, warnings);
        List<VBiome> blacklist = parseBiomeList(map.get("blacklist"),
                "drops[" + dropIndex + "].biomes.blacklist", sourceFile, warnings);
        return new YamlAllowedBiomesDefinition(whitelist, blacklist);
    }

    private static List<VBiome> parseBiomeList(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            warnOptional(warnings, "block." + fieldName + " must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<VBiome> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            VBiome biome = parseOptionalVBiome(entry, fieldName + "[" + index + "]", sourceFile, warnings);
            if (biome != null) {
                result.add(biome);
            }
            index++;
        }
        return result;
    }

    private static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseId(rawId, defaultNamespace, sourceFile, errors, "block.id");
    }

    private static ParsedId parseOptionalId(
            String rawId, String defaultNamespace, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalId(rawId, defaultNamespace, sourceFile, warnings, "id");
    }

    private static boolean matchesRequires(
            ConfigurationSection requiresSection, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.matchesRequires(requiresSection, "block.requires", sourceFile, warnings);
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> errors, List<String> warnings
    ) {
        return YamlParseUtils.getChildSection(parent, name, "block.", sourceFile, warnings);
    }

    private static String parseRequiredString(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseRequiredString(rawValue, "block." + fieldName, sourceFile, errors);
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalString(rawValue, "block." + fieldName, sourceFile, warnings);
    }

    private static Integer parseInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseOptionalInteger(rawValue, min, max, "block." + fieldName, sourceFile, errors);
    }

    private static Integer parseOptionalInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseInteger(rawValue, min, max, "block." + fieldName, sourceFile, warnings);
    }

    private static Double parseOptionalDouble(
            Object rawValue, double min, double max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseDoubleInRange(rawValue, min, max, "block." + fieldName, sourceFile, warnings);
    }

    private static Float parseOptionalFloat(
            Object rawValue, float min, float max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseFloatInRange(rawValue, min, max, "block." + fieldName, sourceFile, warnings);
    }

    private static Boolean parseOptionalBoolean(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseBoolean(rawValue, "block." + fieldName, sourceFile, warnings);
    }

    private static VMaterial parseVMaterial(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseVMaterial(rawValue, "block." + fieldName, sourceFile, errors);
    }

    private static VMaterial parseOptionalVMaterial(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalVMaterial(rawValue, "block." + fieldName, sourceFile, warnings);
    }

    private static VBiome parseVBiome(
            Object rawValue, String fieldName, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseVBiome(rawValue, "block." + fieldName, sourceFile, errors);
    }

    private static VBiome parseOptionalVBiome(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalVBiome(rawValue, "block." + fieldName, sourceFile, warnings);
    }

    private static SilkTouchRequirement parseSilkTouch(
            Object rawValue, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return SilkTouchRequirement.OPTIONAL;
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, "block.drops.silk_touch must be a string in " + sourceFile.getPath());
            return SilkTouchRequirement.OPTIONAL;
        }
        String normalized = ((String) rawValue).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return SilkTouchRequirement.OPTIONAL;
        try {
            return SilkTouchRequirement.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown block.drops.silk_touch '" + rawValue + "' in " + sourceFile.getPath());
            return SilkTouchRequirement.OPTIONAL;
        }
    }
}
