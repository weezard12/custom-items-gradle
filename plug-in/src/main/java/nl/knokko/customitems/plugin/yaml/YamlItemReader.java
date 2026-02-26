package nl.knokko.customitems.plugin.yaml;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.KciItemType.Category;
import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.item.enchantment.VEnchantmentType;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static nl.knokko.customitems.plugin.yaml.YamlVersionContext.mcVersion;
import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

class YamlItemReader {

    static List<YamlItemDefinition> readItems(YamlPackDefinition pack, List<String> errors, List<String> warnings) {
        List<YamlItemDefinition> items = new ArrayList<>();
        forEachYamlDocument(pack, errors, (file, config) -> {
            ConfigurationSection itemSection = config.getConfigurationSection("item");
            if (itemSection == null && YamlDefinitionDetector.shouldUseImplicitRoot(
                    config, file, YamlDefinitionDetector.DefinitionType.ITEM
            )) {
                itemSection = config;
            }
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

            ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors, "item");
            if (parsedId == null) return;

            int errorCountBefore = errors.size();

            ConfigurationSection toolSection = getChildSection(itemSection, "tool", "item.", file, warnings);
            ConfigurationSection armorSection = getChildSection(itemSection, "armor", "item.", file, warnings);
            ConfigurationSection wandSection = getChildSection(itemSection, "wand", "item.", file, warnings);
            ConfigurationSection foodSection = getChildSection(itemSection, "food", "item.", file, warnings);
            ConfigurationSection modelSection = getChildSection(itemSection, "model", "item.", file, warnings);
            Object rawBlock = itemSection.get("block");
            if (rawBlock == null) rawBlock = itemSection.get("block_id");
            String blockId = parseOptionalString(rawBlock, "item.block", file, warnings);
            ParsedId parsedBlockId = null;
            if (blockId != null) {
                parsedBlockId = parseOptionalId(blockId, pack.namespace, file, warnings, "item.block");
                if (parsedBlockId == null) blockId = null;
            }

            YamlItemType type = parseItemType(itemSection.get("type"), file, warnings);
            if (type == null) {
                type = inferItemType(toolSection, armorSection, wandSection, foodSection, blockId != null, file, errors);
            }

            String displayName = translateColors(name, "item.name", file, errors);
            List<String> lore = parseLore(itemSection.get("lore"), file, warnings);
            YamlMaterialDefinition material = parseMaterial(itemSection.get("material"), file, warnings);
            List<YamlEnchantmentDefinition> enchantments = parseEnchantments(itemSection.get("enchantments"), file, warnings);
            Integer stackSize = parseInteger(itemSection.get("stack_size"), 1, 64, "item.stack_size", file, warnings);
            Integer damageValue = parseInteger(itemSection.get("damage_value"), 0, Short.MAX_VALUE, "item.damage_value", file, warnings);
            Boolean unbreakable = parseBoolean(itemSection.get("unbreakable"), "item.unbreakable", file, warnings);
            Double attackDamage = parseDouble(itemSection.get("attack_damage"), "item.attack_damage", file, warnings);
            Double attackSpeed = parseDouble(itemSection.get("attack_speed"), "item.attack_speed", file, warnings);

            if (errors.size() != errorCountBefore) return;

            if (type == null) return;

            YamlItemCustomModelDefinition customModelDefinition = parseCustomModelDefinition(
                    modelSection, parsedId, pack.directory, type, file, warnings
            );

            validateTypeSections(
                    type, toolSection, armorSection, wandSection, foodSection,
                    blockId != null, customModelDefinition != null, file, warnings
            );
            material = validateMaterialForType(type, material, file, warnings);
            if ((type == YamlItemType.TOOL || type == YamlItemType.ARMOR || type == YamlItemType.WAND)
                    && stackSize != null) {
                warnOptional(warnings, "item.stack_size is not supported for type "
                        + type.name().toLowerCase(Locale.ROOT) + " in " + file.getPath());
                stackSize = null;
            }

            YamlToolDefinition toolDefinition = null;
            YamlArmorDefinition armorDefinition = null;
            YamlWandDefinition wandDefinition = null;
            YamlFoodDefinition foodDefinition = null;
            if (type == YamlItemType.TOOL) {
                toolDefinition = parseToolDefinition(toolSection, file, warnings);
            } else if (type == YamlItemType.ARMOR) {
                armorDefinition = parseArmorDefinition(armorSection, file, warnings);
            } else if (type == YamlItemType.WAND) {
                wandDefinition = parseWandDefinition(wandSection, pack, parsedId.fullId, file, errors, warnings);
            } else if (type == YamlItemType.FOOD) {
                foodDefinition = parseFoodDefinition(foodSection, file, warnings);
            }

            if (errors.size() != errorCountBefore) return;

            String blockInternalName = null;
            if (type == YamlItemType.BLOCK) {
                ParsedId parsedBlock = parsedBlockId != null ? parsedBlockId : parsedId;
                blockInternalName = parsedBlock.internalName;
            }

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
                    wandDefinition,
                    foodDefinition,
                    customModelDefinition,
                    blockInternalName,
                    material,
                    enchantments,
                    stackSize,
                    damageValue,
                    unbreakable,
                    attackDamage,
                    attackSpeed
            ));
        });

        return items;
    }


    private static YamlItemType parseItemType(Object rawType, File sourceFile, List<String> warnings) {
        if (rawType == null) return null;
        if (!(rawType instanceof String)) {
            warnOptional(warnings, "item.type must be a string in " + sourceFile.getPath());
            return null;
        }

        String trimmed = ((String) rawType).trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, "item.type must not be empty in " + sourceFile.getPath());
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
            case "wand":
                return YamlItemType.WAND;
            case "food":
                return YamlItemType.FOOD;
            case "block":
            case "block_item":
                return YamlItemType.BLOCK;
            default:
                warnOptional(warnings, "Unknown item.type '" + trimmed + "' in " + sourceFile.getPath());
                return null;
        }
    }

    private static YamlItemType inferItemType(
            ConfigurationSection toolSection,
            ConfigurationSection armorSection,
            ConfigurationSection wandSection,
            ConfigurationSection foodSection,
            boolean hasBlockReference,
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
        if (wandSection != null) {
            count++;
            inferred = YamlItemType.WAND;
        }
        if (foodSection != null) {
            count++;
            inferred = YamlItemType.FOOD;
        }
        if (hasBlockReference) {
            count++;
            inferred = YamlItemType.BLOCK;
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
            ConfigurationSection wandSection,
            ConfigurationSection foodSection,
            boolean hasBlockReference,
            boolean hasCustomModel,
            File sourceFile,
            List<String> warnings
    ) {
        if (type != YamlItemType.TOOL && toolSection != null) {
            warnOptional(warnings, "item.tool is only allowed for type tool in " + sourceFile.getPath());
        }
        if (type != YamlItemType.ARMOR && armorSection != null) {
            warnOptional(warnings, "item.armor is only allowed for type armor in " + sourceFile.getPath());
        }
        if (type != YamlItemType.WAND && wandSection != null) {
            warnOptional(warnings, "item.wand is only allowed for type wand in " + sourceFile.getPath());
        }
        if (type != YamlItemType.FOOD && foodSection != null) {
            warnOptional(warnings, "item.food is only allowed for type food in " + sourceFile.getPath());
        }
        if (type != YamlItemType.BLOCK && hasBlockReference) {
            warnOptional(warnings, "item.block is only allowed for type block in " + sourceFile.getPath());
        }
        if (type == YamlItemType.BLOCK && hasCustomModel) {
            warnOptional(warnings, "item.model is ignored for type block in " + sourceFile.getPath());
        }
    }

    private static YamlItemCustomModelDefinition parseCustomModelDefinition(
            ConfigurationSection modelSection,
            ParsedId parsedId,
            File packDirectory,
            YamlItemType itemType,
            File sourceFile,
            List<String> warnings
    ) {
        if (modelSection != null) {
            return parseExplicitCustomModelDefinition(modelSection, sourceFile, warnings);
        }
        if (itemType == YamlItemType.BLOCK) return null;
        return parseAutoCustomModelDefinition(parsedId, packDirectory, sourceFile, warnings);
    }

    private static YamlItemCustomModelDefinition parseExplicitCustomModelDefinition(
            ConfigurationSection modelSection, File sourceFile, List<String> warnings
    ) {

        boolean hasJson = modelSection.isSet("json");
        boolean hasModel = modelSection.isSet("model");
        String modelPath = parseOptionalString(modelSection.get("json"), "item.model.json", sourceFile, warnings);
        if (modelPath == null) {
            modelPath = parseOptionalString(modelSection.get("model"), "item.model.model", sourceFile, warnings);
        }
        if (modelPath == null) {
            if (!hasJson && !hasModel) {
                warnOptional(warnings, "item.model.json is required when item.model is present in " + sourceFile.getPath());
            }
            return null;
        }

        ConfigurationSection texturesSection = getChildSection(modelSection, "textures", "item.model.", sourceFile, warnings);
        if (texturesSection == null) {
            if (!modelSection.isSet("textures")) {
                warnOptional(warnings, "item.model.textures is required when item.model is present in " + sourceFile.getPath());
            }
            return null;
        }

        Map<String, String> texturePaths = new HashMap<>();
        for (String key : texturesSection.getKeys(false)) {
            String value = parseOptionalString(
                    texturesSection.get(key), "item.model.textures." + key, sourceFile, warnings
            );
            if (value == null) {
                return null;
            }
            texturePaths.put(key, value);
        }

        return new YamlItemCustomModelDefinition(modelPath, texturePaths);
    }

    private static YamlItemCustomModelDefinition parseAutoCustomModelDefinition(
            ParsedId parsedId, File packDirectory, File sourceFile, List<String> warnings
    ) {
        if (parsedId == null) return null;

        File globalItemAssets = resolveGlobalItemAssetsDirectory(packDirectory);
        if (globalItemAssets == null) return null;

        File modelFile = new File(globalItemAssets, parsedId.internalName + ".json");
        if (!modelFile.isFile()) return null;

        byte[] rawModel;
        try {
            rawModel = Files.readAllBytes(modelFile.toPath());
        } catch (IOException ex) {
            warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": failed to read "
                    + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
            return null;
        }

        JsonObject modelJson;
        try {
            modelJson = (JsonObject) Jsoner.deserialize(new String(rawModel, StandardCharsets.UTF_8));
        } catch (JsonException ex) {
            warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": invalid JSON in "
                    + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
            return null;
        }
        if (modelJson == null) {
            warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": empty model JSON in "
                    + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
            return null;
        }

        Map<String, String> textureMap = parseTextureMap(modelJson.get("textures"));
        if (textureMap == null) {
            warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": model has no valid textures map in "
                    + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
            return null;
        }

        Map<String, String> resolvedTextures = new HashMap<>();
        for (Map.Entry<String, String> entry : textureMap.entrySet()) {
            String textureKey = entry.getKey();
            String textureValue = entry.getValue();
            if (textureValue == null) {
                warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": texture key '"
                        + textureKey + "' has no value in " + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
                return null;
            }
            String trimmedValue = textureValue.trim();
            if (trimmedValue.isEmpty()) {
                warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": texture key '"
                        + textureKey + "' is empty in " + modelFile.getPath() + " (" + sourceFile.getPath() + ")");
                return null;
            }
            if (trimmedValue.startsWith("#")) continue;

            String resolvedToken = resolveAutoTextureToken(trimmedValue, globalItemAssets);
            if (resolvedToken != null) {
                resolvedTextures.put(textureKey, resolvedToken);
                continue;
            }
            if (!isVanillaTextureReference(trimmedValue)) {
                warnOptional(warnings, "Auto item model fallback for " + parsedId.fullId + ": missing texture '"
                        + trimmedValue + "' for key '" + textureKey + "' in " + modelFile.getPath()
                        + " (" + sourceFile.getPath() + ")");
                return null;
            }
        }

        return new YamlItemCustomModelDefinition(modelFile.getAbsolutePath(), resolvedTextures);
    }

    private static File resolveGlobalItemAssetsDirectory(File packDirectory) {
        if (packDirectory == null) return null;
        File parent = packDirectory.getParentFile();
        if (parent == null) return null;
        return new File(new File(parent, "assets"), "item");
    }

    private static Map<String, String> parseTextureMap(Object rawTextures) {
        if (!(rawTextures instanceof Map)) return null;

        Map<?, ?> rawMap = (Map<?, ?>) rawTextures;
        Map<String, String> textureMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                return null;
            }
            textureMap.put((String) entry.getKey(), (String) entry.getValue());
        }
        return textureMap;
    }

    private static String resolveAutoTextureToken(String rawValue, File globalItemAssetsDirectory) {
        if (globalItemAssetsDirectory == null) return null;

        for (String candidate : createAutoTextureCandidates(rawValue)) {
            String normalized = candidate.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isEmpty()) continue;
            String withExtension = normalized.toLowerCase(Locale.ROOT).endsWith(".png")
                    ? normalized
                    : normalized + ".png";
            File candidateFile = new File(globalItemAssetsDirectory, withExtension);
            if (candidateFile.isFile()) {
                return withExtension;
            }
        }
        return null;
    }

    private static List<String> createAutoTextureCandidates(String rawValue) {
        String value = rawValue.trim();
        String namespace = null;
        int colonIndex = value.indexOf(':');
        if (colonIndex >= 0) {
            namespace = value.substring(0, colonIndex).trim();
            value = value.substring(colonIndex + 1);
        }

        String normalized = value.replace('\\', '/');
        Set<String> candidates = new LinkedHashSet<>();
        if (!normalized.isEmpty()) {
            candidates.add(normalized);

            int slashIndex = normalized.lastIndexOf('/');
            String baseName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
            if (!baseName.isEmpty()) {
                candidates.add(baseName);
            }

            if (namespace != null && !namespace.isEmpty()) {
                // Support both namespace folders and namespace_internal-name style files.
                candidates.add(namespace + "/" + normalized);

                String flattened = normalized.replace('/', '_');
                if (!flattened.isEmpty()) {
                    candidates.add(namespace + "_" + flattened);
                }
                if (!baseName.isEmpty()) {
                    candidates.add(namespace + "_" + baseName);
                }
            }
        }

        return new ArrayList<>(candidates);
    }

    private static boolean isVanillaTextureReference(String textureValue) {
        String normalized = textureValue.toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:")
                || normalized.startsWith("item/")
                || normalized.startsWith("block/");
    }

    private static YamlMaterialDefinition validateMaterialForType(
            YamlItemType type,
            YamlMaterialDefinition material,
            File sourceFile,
            List<String> warnings
    ) {
        if (material == null) return null;

        if (type == YamlItemType.BLOCK) {
            warnOptional(warnings, "item.material is not supported for type block in " + sourceFile.getPath());
            return null;
        }

        if (type == YamlItemType.SIMPLE) return material;

        if (type == YamlItemType.FOOD) {
            if (material.otherMaterial != null) return material;
            if (!material.itemType.canServe(Category.FOOD)) {
                warnOptional(warnings, "item.material must be food-compatible for type food in " + sourceFile.getPath());
                return null;
            }
            return material;
        }

        if (type == YamlItemType.WAND) {
            if (material.otherMaterial != null) {
                warnOptional(warnings, "item.material must be a custom item type for type wand in "
                        + sourceFile.getPath());
                return null;
            }
            if (!material.itemType.canServe(Category.WAND)) {
                warnOptional(warnings, "item.material must be wand-compatible for type wand in "
                        + sourceFile.getPath());
                return null;
            }
            return material;
        }

        if (material.otherMaterial != null) {
            if (type == YamlItemType.TOOL && material.otherMaterial == VMaterial.MACE) {
                return material;
            }
            warnOptional(warnings, "item.material must be a custom item type for type "
                    + type.name().toLowerCase(Locale.ROOT) + " in " + sourceFile.getPath());
            return null;
        }

        if (type == YamlItemType.TOOL && !isToolItemType(material.itemType)) {
            warnOptional(warnings, "item.material must be a tool material for type tool in " + sourceFile.getPath());
            return null;
        } else if (type == YamlItemType.ARMOR && !isArmorItemType(material.itemType)) {
            warnOptional(warnings, "item.material must be an armor material for type armor in " + sourceFile.getPath());
            return null;
        }
        return material;
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
            ConfigurationSection toolSection, File sourceFile, List<String> warnings
    ) {
        if (toolSection == null) return new YamlToolDefinition(null, null, null);
        Integer maxDurability = parseInteger(toolSection.get("max_durability"), 1, Integer.MAX_VALUE,
                "item.tool.max_durability", sourceFile, warnings);
        Integer entityHitLoss = parseInteger(toolSection.get("entity_hit_durability_loss"), 0, Integer.MAX_VALUE,
                "item.tool.entity_hit_durability_loss", sourceFile, warnings);
        Integer blockBreakLoss = parseInteger(toolSection.get("block_break_durability_loss"), 0, Integer.MAX_VALUE,
                "item.tool.block_break_durability_loss", sourceFile, warnings);
        return new YamlToolDefinition(maxDurability, entityHitLoss, blockBreakLoss);
    }

    private static YamlArmorDefinition parseArmorDefinition(
            ConfigurationSection armorSection, File sourceFile, List<String> warnings
    ) {
        if (armorSection == null) return new YamlArmorDefinition(null, null, null, null, null);
        Integer maxDurability = parseInteger(armorSection.get("max_durability"), 1, Integer.MAX_VALUE,
                "item.armor.max_durability", sourceFile, warnings);
        Integer entityHitLoss = parseInteger(armorSection.get("entity_hit_durability_loss"), 0, Integer.MAX_VALUE,
                "item.armor.entity_hit_durability_loss", sourceFile, warnings);
        Integer blockBreakLoss = parseInteger(armorSection.get("block_break_durability_loss"), 0, Integer.MAX_VALUE,
                "item.armor.block_break_durability_loss", sourceFile, warnings);
        Double armorValue = parseDouble(armorSection.get("armor_value"), "item.armor.armor_value", sourceFile, warnings);
        Double armorToughness = parseDouble(armorSection.get("armor_toughness"), "item.armor.armor_toughness", sourceFile, warnings);
        if (armorValue != null && armorValue < 0.0) {
            warnOptional(warnings, "item.armor.armor_value must be non-negative in " + sourceFile.getPath());
            armorValue = null;
        }
        if (armorToughness != null && armorToughness < 0.0) {
            warnOptional(warnings, "item.armor.armor_toughness must be non-negative in " + sourceFile.getPath());
            armorToughness = null;
        }
        return new YamlArmorDefinition(maxDurability, entityHitLoss, blockBreakLoss, armorValue, armorToughness);
    }

    private static YamlFoodDefinition parseFoodDefinition(
            ConfigurationSection foodSection, File sourceFile, List<String> warnings
    ) {
        if (foodSection == null) return new YamlFoodDefinition(null, null);
        Integer foodValue = parseInteger(foodSection.get("food_value"), 0, Integer.MAX_VALUE,
                "item.food.food_value", sourceFile, warnings);
        Integer eatTime = parseInteger(foodSection.get("eat_time"), 1, Integer.MAX_VALUE,
                "item.food.eat_time", sourceFile, warnings);
        return new YamlFoodDefinition(foodValue, eatTime);
    }

    private static YamlWandDefinition parseWandDefinition(
            ConfigurationSection wandSection,
            YamlPackDefinition pack,
            String itemId,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        if (wandSection == null) {
            errors.add("Missing item.wand for wand item " + itemId + " in " + sourceFile.getPath());
            return null;
        }

        Object rawProjectile = wandSection.get("projectile");
        if (rawProjectile == null) rawProjectile = wandSection.get("projectile_id");
        String projectileId = parseOptionalString(rawProjectile, "item.wand.projectile", sourceFile, warnings);
        String projectileInternalName = null;
        if (projectileId != null) {
            ParsedId parsedProjectile = parseOptionalId(
                    projectileId, pack.namespace, sourceFile, warnings, "item.wand.projectile"
            );
            if (parsedProjectile != null) {
                projectileInternalName = parsedProjectile.internalName;
            }
        }

        Integer cooldown = parseInteger(wandSection.get("cooldown"), 1, Integer.MAX_VALUE,
                "item.wand.cooldown", sourceFile, warnings);
        Integer amountPerShot = parseInteger(wandSection.get("amount_per_shot"), 1, Integer.MAX_VALUE,
                "item.wand.amount_per_shot", sourceFile, warnings);
        Double manaCostValue = parseDouble(wandSection.get("mana_cost"), "item.wand.mana_cost", sourceFile, warnings);
        Float manaCost = null;
        if (manaCostValue != null) {
            if (manaCostValue < 0.0) {
                warnOptional(warnings, "item.wand.mana_cost must be non-negative in " + sourceFile.getPath());
            } else {
                manaCost = manaCostValue.floatValue();
            }
        }
        Boolean requiresPermission = parseBoolean(
                wandSection.get("requires_permission"), "item.wand.requires_permission", sourceFile, warnings
        );

        Object rawSpells = wandSection.get("magic_spells");
        if (rawSpells == null) rawSpells = wandSection.get("spells");
        List<String> magicSpells = parseStringList(rawSpells, "item.wand.magic_spells", sourceFile, warnings);

        YamlWandChargesDefinition charges = parseWandCharges(wandSection.get("charges"), sourceFile, warnings);

        if (projectileInternalName == null && magicSpells.isEmpty()) {
            errors.add("Wand item " + itemId + " must define wand.projectile or wand.magic_spells in "
                    + sourceFile.getPath());
            return null;
        }

        return new YamlWandDefinition(
                projectileInternalName,
                cooldown,
                amountPerShot,
                charges,
                manaCost,
                requiresPermission,
                magicSpells
        );
    }

    private static YamlWandChargesDefinition parseWandCharges(
            Object rawCharges, File sourceFile, List<String> warnings
    ) {
        if (rawCharges == null) return null;
        if (!(rawCharges instanceof ConfigurationSection)) {
            warnOptional(warnings, "item.wand.charges must be a map in " + sourceFile.getPath());
            return null;
        }
        ConfigurationSection chargesSection = (ConfigurationSection) rawCharges;

        Integer maxCharges = parseInteger(
                chargesSection.get("max_charges"), 2, Integer.MAX_VALUE,
                "item.wand.charges.max_charges", sourceFile, warnings
        );
        if (maxCharges == null && chargesSection.isSet("max")) {
            maxCharges = parseInteger(
                    chargesSection.get("max"), 2, Integer.MAX_VALUE,
                    "item.wand.charges.max", sourceFile, warnings
            );
        }

        Integer rechargeTime = parseInteger(
                chargesSection.get("recharge_time"), 1, Integer.MAX_VALUE,
                "item.wand.charges.recharge_time", sourceFile, warnings
        );
        if (rechargeTime == null && chargesSection.isSet("recharge")) {
            rechargeTime = parseInteger(
                    chargesSection.get("recharge"), 1, Integer.MAX_VALUE,
                    "item.wand.charges.recharge", sourceFile, warnings
            );
        }

        if (maxCharges == null || rechargeTime == null) {
            warnOptional(warnings, "item.wand.charges requires max_charges and recharge_time in " + sourceFile.getPath());
            return null;
        }

        return new YamlWandChargesDefinition(maxCharges, rechargeTime);
    }

    private static YamlMaterialDefinition parseMaterial(Object rawMaterial, File sourceFile, List<String> warnings) {
        if (rawMaterial == null) return null;
        if (!(rawMaterial instanceof String)) {
            warnOptional(warnings, "item.material must be a string in " + sourceFile.getPath());
            return null;
        }

        String raw = ((String) rawMaterial).trim();
        if (raw.isEmpty()) return null;

        String normalized = normalizeNamespacedValue(raw);
        if (normalized == null) {
            warnOptional(warnings, "Invalid item.material '" + raw + "' in " + sourceFile.getPath());
            return null;
        }

        KciItemType itemType = resolveItemType(normalized);
        if (itemType != null) {
            if (mcVersion < itemType.firstVersion || mcVersion > itemType.lastVersion) {
                warnOptional(warnings, "item.material '" + raw + "' is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return new YamlMaterialDefinition(itemType, null);
        }

        try {
            VMaterial material = VMaterial.valueOf(normalized);
            if (mcVersion < material.firstVersion || mcVersion > material.lastVersion) {
                warnOptional(warnings, "item.material '" + raw + "' is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            if (mcVersion < MCVersions.VERSION1_14) {
                warnOptional(warnings, "item.material '" + raw + "' requires MC 1.14+ (" + sourceFile.getPath() + ")");
                return null;
            }
            return new YamlMaterialDefinition(KciItemType.OTHER, material);
        } catch (IllegalArgumentException notMaterial) {
            warnOptional(warnings, "Unknown item.material '" + raw + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static KciItemType resolveItemType(String normalized) {
        KciItemType direct = tryParseItemType(normalized);
        if (direct != null) return direct;

        if ("WOODEN_SPEAR".equals(normalized)) {
            return tryParseItemType("WOOD_SPEAR");
        }

        return null;
    }

    private static KciItemType tryParseItemType(String name) {
        try {
            return KciItemType.valueOf(name);
        } catch (IllegalArgumentException notFound) {
            return null;
        }
    }

    private static List<YamlEnchantmentDefinition> parseEnchantments(
            Object rawEnchantments, File sourceFile, List<String> warnings
    ) {
        if (rawEnchantments == null) return Collections.emptyList();
        if (!(rawEnchantments instanceof List<?>)) {
            warnOptional(warnings, "item.enchantments must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlEnchantmentDefinition> result = new ArrayList<>();
        for (Object entry : (List<?>) rawEnchantments) {
            ParsedEnchantment parsed = parseEnchantmentEntry(entry, sourceFile, warnings);
            if (parsed != null) {
                result.add(new YamlEnchantmentDefinition(parsed.type, parsed.level));
            }
        }

        return result;
    }

    private static ParsedEnchantment parseEnchantmentEntry(
            Object entry, File sourceFile, List<String> warnings
    ) {
        if (entry instanceof String) {
            return parseEnchantmentString((String) entry, sourceFile, warnings);
        }

        if (entry instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) entry;
            Object idValue = map.get("id");
            Object levelValue = map.get("level");
            if (idValue == null) {
                warnOptional(warnings, "item.enchantments entry is missing id in " + sourceFile.getPath());
                return null;
            }
            if (!(idValue instanceof String)) {
                warnOptional(warnings, "item.enchantments id must be a string in " + sourceFile.getPath());
                return null;
            }
            String id = ((String) idValue).trim();
            if (id.isEmpty()) {
                warnOptional(warnings, "item.enchantments id is empty in " + sourceFile.getPath());
                return null;
            }
            int level = 1;
            if (levelValue != null) {
                Integer parsedLevel = parseInteger(levelValue, 1, Integer.MAX_VALUE,
                        "enchantments.level", sourceFile, warnings);
                if (parsedLevel == null) return null;
                level = parsedLevel;
            }
            VEnchantmentType type = parseEnchantmentType(id);
            if (type == null) {
                warnOptional(warnings, "Unknown enchantment id '" + id + "' in " + sourceFile.getPath());
                return null;
            }
            return new ParsedEnchantment(type, level);
        }

        warnOptional(warnings, "item.enchantments entry must be a string or map in " + sourceFile.getPath());
        return null;
    }

    private static ParsedEnchantment parseEnchantmentString(
            String raw, File sourceFile, List<String> warnings
    ) {
        String value = raw.trim();
        if (value.isEmpty()) {
            warnOptional(warnings, "item.enchantments entry is empty in " + sourceFile.getPath());
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
            warnOptional(warnings, "Unknown enchantment id '" + id + "' in " + sourceFile.getPath());
            return null;
        }

        int finalLevel = level != null ? level : 1;
        if (finalLevel < 1) {
            warnOptional(warnings, "enchantment level must be positive in " + sourceFile.getPath());
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

    private static List<String> parseLore(Object rawLore, File sourceFile, List<String> warnings) {
        if (rawLore == null) return Collections.emptyList();
        if (!(rawLore instanceof List<?>)) {
            warnOptional(warnings, "item.lore must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawLore) {
            if (!(entry instanceof String)) {
                warnOptional(warnings, "item.lore entry " + index + " must be a string in " + sourceFile.getPath());
                index++;
                continue;
            }
            String translated = translateColorsOptional((String) entry, "item.lore", sourceFile, warnings);
            if (translated != null) {
                result.add(translated);
            }
            index++;
        }

        return result;
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
