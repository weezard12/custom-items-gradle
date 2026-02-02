package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.item.enchantment.VEnchantmentType;
import nl.knokko.customitems.recipe.ingredient.constraint.ConstraintOperator;
import nl.knokko.customitems.plugin.yaml.YamlParseUtils.ParsedId;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

class YamlRecipeReader {

    static List<YamlRecipeDefinition> readRecipes(
            YamlPackDefinition pack, List<String> errors, List<String> warnings
    ) {
        List<YamlRecipeDefinition> recipes = new ArrayList<>();
        forEachYamlDocument(pack, errors, (file, config) -> {
            ConfigurationSection recipeSection = config.getConfigurationSection("recipe");
            if (recipeSection == null) return;

            int errorCountBefore = errors.size();
            YamlRecipeDefinition definition = parseRecipeDefinition(pack, file, recipeSection, errors, warnings);
            if (errors.size() != errorCountBefore) return;
            if (definition != null) recipes.add(definition);
        });
        return recipes;
    }

    private static YamlRecipeDefinition parseRecipeDefinition(
            YamlPackDefinition pack,
            File file,
            ConfigurationSection section,
            List<String> errors,
            List<String> warnings
    ) {
        String rawId = section.getString("id");
        if (rawId == null || rawId.trim().isEmpty()) {
            errors.add("Missing recipe.id in " + file.getPath());
            return null;
        }

        ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors, "recipe.id");
        if (parsedId == null) return null;

        YamlRecipeType type = parseRecipeType(section, file, errors);
        if (type == null) return null;

        boolean ignoreDisplacement = parseBoolean(section.get("ignore_displacement"), true,
                "recipe.ignore_displacement", file, warnings);

        String permission = parseOptionalString(section.get("permission"), "recipe.permission", file, warnings);
        if (permission == null) {
            permission = parseOptionalString(section.get("required_permission"), "recipe.required_permission", file, warnings);
        }

        YamlRecipeResultDefinition result = parseResultRoot(section, pack, file, errors);
        if (result == null) return null;

        if (type == YamlRecipeType.SHAPED) {
            YamlRecipeIngredientDefinition[] ingredients = parseShapedIngredients(section, pack, file, errors);
            if (ingredients == null) return null;
            return new YamlRecipeDefinition(
                    parsedId.fullId,
                    parsedId.internalName,
                    parsedId.name,
                    pack.directory,
                    file,
                    type,
                    ignoreDisplacement,
                    permission,
                    result,
                    ingredients,
                    null
            );
        }

        List<YamlRecipeIngredientDefinition> ingredients = parseShapelessIngredients(section, pack, file, errors);
        if (ingredients == null) return null;
        return new YamlRecipeDefinition(
                parsedId.fullId,
                parsedId.internalName,
                parsedId.name,
                pack.directory,
                file,
                type,
                ignoreDisplacement,
                permission,
                result,
                null,
                ingredients
        );
    }

    private static YamlRecipeType parseRecipeType(
            ConfigurationSection section, File file, List<String> errors
    ) {
        Object rawType = section.get("type");
        if (rawType instanceof String) {
            String trimmed = ((String) rawType).trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                errors.add("recipe.type must not be empty in " + file.getPath());
                return null;
            }
            if (trimmed.equals("shaped")) return YamlRecipeType.SHAPED;
            if (trimmed.equals("shapeless")) return YamlRecipeType.SHAPELESS;
            errors.add("Unknown recipe.type '" + rawType + "' in " + file.getPath());
            return null;
        }

        if (section.isSet("shape")) return YamlRecipeType.SHAPED;
        if (section.isSet("ingredients")) return YamlRecipeType.SHAPELESS;

        errors.add("recipe.type is required in " + file.getPath());
        return null;
    }

    private static YamlRecipeResultDefinition parseResultRoot(
            ConfigurationSection section, YamlPackDefinition pack, File file, List<String> errors
    ) {
        Object rawResult = section.get("result");
        if (rawResult == null) {
            rawResult = section.get("output");
        }
        if (rawResult == null) {
            Object itemId = section.get("item");
            if (itemId != null) {
                YamlRecipeResultDefinition simpleResult = parseResultDefinition(
                        itemId, pack, file, errors, "recipe.item"
                );
                if (simpleResult != null && simpleResult.type != YamlRecipeResultType.CUSTOM) {
                    errors.add("recipe.item must reference a custom item in " + file.getPath());
                    return null;
                }
                return simpleResult;
            }
            errors.add("Missing recipe.result in " + file.getPath());
            return null;
        }
        return parseResultDefinition(rawResult, pack, file, errors, "recipe.result");
    }

    private static YamlRecipeIngredientDefinition[] parseShapedIngredients(
            ConfigurationSection section, YamlPackDefinition pack, File file, List<String> errors
    ) {
        Object rawShape = section.get("shape");
        if (!(rawShape instanceof List<?>)) {
            errors.add("recipe.shape must be a list in " + file.getPath());
            return null;
        }
        List<?> shapeList = (List<?>) rawShape;
        if (shapeList.isEmpty()) {
            errors.add("recipe.shape must not be empty in " + file.getPath());
            return null;
        }
        if (shapeList.size() > 3) {
            errors.add("recipe.shape can have at most 3 rows in " + file.getPath());
            return null;
        }

        List<String> shape = new ArrayList<>(shapeList.size());
        int maxWidth = 0;
        for (int index = 0; index < shapeList.size(); index++) {
            Object entry = shapeList.get(index);
            if (!(entry instanceof String)) {
                errors.add("recipe.shape row " + index + " must be a string in " + file.getPath());
                return null;
            }
            String row = (String) entry;
            if (row.length() > 3) {
                errors.add("recipe.shape row " + index + " can have at most 3 characters in " + file.getPath());
                return null;
            }
            maxWidth = Math.max(maxWidth, row.length());
            shape.add(row);
        }
        if (maxWidth == 0) {
            errors.add("recipe.shape must not be empty in " + file.getPath());
            return null;
        }

        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection == null) {
            errors.add("recipe.ingredients must be a map in " + file.getPath());
            return null;
        }

        Map<Character, YamlRecipeIngredientDefinition> ingredientsByKey = new HashMap<>();
        for (String key : ingredientSection.getKeys(false)) {
            if (key == null || key.length() != 1) {
                errors.add("recipe.ingredients key '" + key + "' must be a single character in " + file.getPath());
                return null;
            }
            char symbol = key.charAt(0);
            Object rawValue = ingredientSection.get(key);
            String context = "recipe.ingredients." + key;
            YamlRecipeIngredientDefinition ingredient = parseIngredientDefinition(rawValue, pack, file, errors, context);
            if (ingredient == null) return null;
            ingredientsByKey.put(symbol, ingredient);
        }

        Set<Character> usedKeys = new HashSet<>();
        YamlRecipeIngredientDefinition[] grid = new YamlRecipeIngredientDefinition[9];
        YamlRecipeIngredientDefinition empty = new YamlRecipeIngredientDefinition(
                YamlRecipeIngredientType.NONE, null, null, null, null, null,
                1, null, null
        );
        Arrays.fill(grid, empty);

        for (int y = 0; y < 3; y++) {
            String row = y < shape.size() ? shape.get(y) : "";
            for (int x = 0; x < 3; x++) {
                char symbol = x < row.length() ? row.charAt(x) : ' ';
                if (symbol == ' ' || symbol == '.') continue;
                YamlRecipeIngredientDefinition ingredient = ingredientsByKey.get(symbol);
                if (ingredient == null) {
                    errors.add("recipe.shape uses '" + symbol + "' without a matching ingredient in " + file.getPath());
                    return null;
                }
                usedKeys.add(symbol);
                grid[x + 3 * y] = ingredient;
            }
        }

        for (Character key : ingredientsByKey.keySet()) {
            if (!usedKeys.contains(key)) {
                errors.add("recipe.ingredients key '" + key + "' is not used in recipe.shape (" + file.getPath() + ")");
                return null;
            }
        }

        return grid;
    }

    private static List<YamlRecipeIngredientDefinition> parseShapelessIngredients(
            ConfigurationSection section, YamlPackDefinition pack, File file, List<String> errors
    ) {
        Object rawIngredients = section.get("ingredients");
        if (!(rawIngredients instanceof List<?>)) {
            errors.add("recipe.ingredients must be a list in " + file.getPath());
            return null;
        }
        List<?> list = (List<?>) rawIngredients;
        if (list.isEmpty()) {
            errors.add("recipe.ingredients must not be empty in " + file.getPath());
            return null;
        }

        List<YamlRecipeIngredientDefinition> ingredients = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            String context = "recipe.ingredients[" + index + "]";
            YamlRecipeIngredientDefinition ingredient = parseIngredientDefinition(entry, pack, file, errors, context);
            if (ingredient == null) return null;
            if (ingredient.type == YamlRecipeIngredientType.NONE) {
                errors.add("recipe.ingredients[" + index + "] must not be empty in " + file.getPath());
                return null;
            }
            ingredients.add(ingredient);
        }

        return ingredients;
    }

    private static YamlRecipeIngredientDefinition parseIngredientDefinition(
            Object rawValue,
            YamlPackDefinition pack,
            File file,
            List<String> errors,
            String context
    ) {
        if (rawValue == null) {
            return new YamlRecipeIngredientDefinition(
                    YamlRecipeIngredientType.NONE, null, null, null, null, null,
                    1, null, null
            );
        }

        if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (trimmed.isEmpty()) {
                errors.add(context + " must not be empty in " + file.getPath());
                return null;
            }
            String lowered = trimmed.toLowerCase(Locale.ROOT);
            if (lowered.equals("none") || lowered.equals("empty") || lowered.equals("air")) {
                return new YamlRecipeIngredientDefinition(
                        YamlRecipeIngredientType.NONE, null, null, null, null, null,
                        1, null, null
                );
            }

            if (isProbablyVanillaMaterial(trimmed)) {
                VMaterial material = parseVMaterial(trimmed, context, file, errors);
                if (material == null) return null;
                return new YamlRecipeIngredientDefinition(
                        YamlRecipeIngredientType.VANILLA,
                        material,
                        null,
                        null,
                        null,
                        null,
                        1,
                        null,
                        null
                );
            }

            ParsedId parsed = parseId(trimmed, pack.namespace, file, errors, context);
            if (parsed == null) return null;
            return new YamlRecipeIngredientDefinition(
                    YamlRecipeIngredientType.CUSTOM,
                    null,
                    null,
                    parsed.internalName,
                    null,
                    null,
                    1,
                    null,
                    null
            );
        }

        Map<?, ?> map = toMap(rawValue);
        if (map == null) {
            errors.add(context + " must be a string or map in " + file.getPath());
            return null;
        }

        YamlRecipeIngredientType type = null;
        Object rawType = map.get("type");
        if (rawType != null) {
            if (!(rawType instanceof String)) {
                errors.add(context + ".type must be a string in " + file.getPath());
                return null;
            }
            type = parseIngredientType((String) rawType, context, file, errors);
            if (type == null) return null;
        }

        if (type == null) {
            if (map.containsKey("item") || map.containsKey("custom")) {
                type = YamlRecipeIngredientType.CUSTOM;
            } else if (map.containsKey("material") || map.containsKey("vanilla")) {
                type = map.containsKey("data_value") || map.containsKey("data") || map.containsKey("damage")
                        ? YamlRecipeIngredientType.VANILLA_DATA
                        : YamlRecipeIngredientType.VANILLA;
            } else if (map.containsKey("mimic")) {
                type = YamlRecipeIngredientType.MIMIC;
            } else if (map.containsKey("item_bridge") || map.containsKey("itembridge")) {
                type = YamlRecipeIngredientType.ITEM_BRIDGE;
            } else if (map.containsKey("copied") || map.containsKey("encoded")) {
                type = YamlRecipeIngredientType.COPIED;
            } else if (map.containsKey("none")) {
                type = YamlRecipeIngredientType.NONE;
            }
        }

        if (type == null) {
            errors.add(context + " is missing type/material/item in " + file.getPath());
            return null;
        }

        Integer amount = parseInteger(map.get("amount"), 1, 64, context + ".amount", file, errors);
        if (amount == null) amount = 1;

        YamlRecipeResultDefinition remainingItem = null;
        Object rawRemaining = map.get("remaining_item");
        if (rawRemaining == null) rawRemaining = map.get("remaining");
        if (rawRemaining == null) rawRemaining = map.get("remainder");
        if (rawRemaining != null) {
            remainingItem = parseResultDefinition(rawRemaining, pack, file, errors, context + ".remaining_item");
            if (remainingItem == null) return null;
        }

        YamlRecipeConstraintsDefinition constraints = null;
        if (map.containsKey("constraints")) {
            constraints = parseConstraints(map.get("constraints"), file, errors, context + ".constraints");
            if (constraints == null) return null;
        }

        if (type == YamlRecipeIngredientType.NONE) {
            return new YamlRecipeIngredientDefinition(
                    YamlRecipeIngredientType.NONE, null, null, null, null, null, 1, null, null
            );
        }

        if (type == YamlRecipeIngredientType.VANILLA || type == YamlRecipeIngredientType.VANILLA_DATA) {
            Object rawMaterial = map.get("material");
            if (rawMaterial == null) rawMaterial = map.get("vanilla");
            VMaterial material = parseVMaterial(rawMaterial, context + ".material", file, errors);
            if (material == null) return null;
            Integer dataValue = null;
            if (type == YamlRecipeIngredientType.VANILLA_DATA) {
                dataValue = parseInteger(map.get("data_value"), 0, 15, context + ".data_value", file, errors);
                if (dataValue == null) {
                    dataValue = parseInteger(map.get("data"), 0, 15, context + ".data", file, errors);
                }
                if (dataValue == null) {
                    dataValue = parseInteger(map.get("damage"), 0, 15, context + ".damage", file, errors);
                }
                if (dataValue == null) {
                    errors.add(context + ".data_value is required for vanilla_data in " + file.getPath());
                    return null;
                }
            }

            return new YamlRecipeIngredientDefinition(
                    type,
                    material,
                    dataValue,
                    null,
                    null,
                    null,
                    amount,
                    remainingItem,
                    constraints
            );
        }

        if (type == YamlRecipeIngredientType.CUSTOM) {
            Object rawItem = map.get("item");
            if (rawItem == null) rawItem = map.get("custom");
            if (!(rawItem instanceof String)) {
                errors.add(context + ".item must be a string in " + file.getPath());
                return null;
            }
            String rawId = ((String) rawItem).trim();
            if (rawId.isEmpty()) {
                errors.add(context + ".item must not be empty in " + file.getPath());
                return null;
            }
            ParsedId parsed = parseId(rawId, pack.namespace, file, errors, context + ".item");
            if (parsed == null) return null;
            return new YamlRecipeIngredientDefinition(
                    type,
                    null,
                    null,
                    parsed.internalName,
                    null,
                    null,
                    amount,
                    remainingItem,
                    constraints
            );
        }

        if (type == YamlRecipeIngredientType.MIMIC || type == YamlRecipeIngredientType.ITEM_BRIDGE) {
            Object rawId = map.get(type == YamlRecipeIngredientType.MIMIC ? "mimic" : "item_bridge");
            if (rawId == null && type == YamlRecipeIngredientType.ITEM_BRIDGE) rawId = map.get("itembridge");
            if (!(rawId instanceof String)) {
                errors.add(context + "." + (type == YamlRecipeIngredientType.MIMIC ? "mimic" : "item_bridge")
                        + " must be a string in " + file.getPath());
                return null;
            }
            String id = ((String) rawId).trim();
            if (id.isEmpty()) {
                errors.add(context + " id must not be empty in " + file.getPath());
                return null;
            }
            return new YamlRecipeIngredientDefinition(
                    type,
                    null,
                    null,
                    null,
                    id,
                    null,
                    amount,
                    remainingItem,
                    constraints
            );
        }

        if (type == YamlRecipeIngredientType.COPIED) {
            Object rawEncoded = map.get("encoded");
            if (rawEncoded == null) rawEncoded = map.get("copied");
            if (!(rawEncoded instanceof String)) {
                errors.add(context + ".encoded must be a string in " + file.getPath());
                return null;
            }
            String encoded = ((String) rawEncoded).trim();
            if (encoded.isEmpty()) {
                errors.add(context + ".encoded must not be empty in " + file.getPath());
                return null;
            }
            return new YamlRecipeIngredientDefinition(
                    type,
                    null,
                    null,
                    null,
                    null,
                    encoded,
                    amount,
                    remainingItem,
                    constraints
            );
        }

        errors.add("Unsupported ingredient type " + type + " in " + file.getPath());
        return null;
    }

    private static YamlRecipeResultDefinition parseResultDefinition(
            Object rawValue,
            YamlPackDefinition pack,
            File file,
            List<String> errors,
            String context
    ) {
        if (rawValue == null) return null;

        if (rawValue instanceof String) {
            String trimmed = ((String) rawValue).trim();
            if (trimmed.isEmpty()) {
                errors.add(context + " must not be empty in " + file.getPath());
                return null;
            }
            if (isProbablyVanillaMaterial(trimmed)) {
                VMaterial material = parseVMaterial(trimmed, context, file, errors);
                if (material == null) return null;
                return new YamlRecipeResultDefinition(
                        YamlRecipeResultType.VANILLA,
                        material,
                        null,
                        null,
                        null,
                        null,
                        1,
                        null
                );
            }

            ParsedId parsed = parseId(trimmed, pack.namespace, file, errors, context);
            if (parsed == null) return null;
            return new YamlRecipeResultDefinition(
                    YamlRecipeResultType.CUSTOM,
                    null,
                    null,
                    parsed.internalName,
                    null,
                    null,
                    1,
                    null
            );
        }

        Map<?, ?> map = toMap(rawValue);
        if (map == null) {
            errors.add(context + " must be a string or map in " + file.getPath());
            return null;
        }

        YamlRecipeResultType type = null;
        Object rawType = map.get("type");
        if (rawType != null) {
            if (!(rawType instanceof String)) {
                errors.add(context + ".type must be a string in " + file.getPath());
                return null;
            }
            type = parseResultType((String) rawType, context, file, errors);
            if (type == null) return null;
        }

        if (type == null) {
            if (map.containsKey("item") || map.containsKey("custom")) {
                type = YamlRecipeResultType.CUSTOM;
            } else if (map.containsKey("material") || map.containsKey("vanilla")) {
                type = map.containsKey("data_value") || map.containsKey("data") || map.containsKey("damage")
                        ? YamlRecipeResultType.VANILLA_DATA
                        : YamlRecipeResultType.VANILLA;
            } else if (map.containsKey("mimic")) {
                type = YamlRecipeResultType.MIMIC;
            } else if (map.containsKey("item_bridge") || map.containsKey("itembridge")) {
                type = YamlRecipeResultType.ITEM_BRIDGE;
            } else if (map.containsKey("copied") || map.containsKey("encoded")) {
                type = YamlRecipeResultType.COPIED;
            } else if (map.containsKey("upgrade") || map.containsKey("ingredient_index") || map.containsKey("input_slot")) {
                type = YamlRecipeResultType.UPGRADE;
            }
        }

        if (type == null) {
            errors.add(context + " is missing type/material/item in " + file.getPath());
            return null;
        }

        Integer amount = parseInteger(map.get("amount"), 1, 64, context + ".amount", file, errors);
        if (amount == null) amount = 1;

        if (type == YamlRecipeResultType.VANILLA || type == YamlRecipeResultType.VANILLA_DATA) {
            Object rawMaterial = map.get("material");
            if (rawMaterial == null) rawMaterial = map.get("vanilla");
            VMaterial material = parseVMaterial(rawMaterial, context + ".material", file, errors);
            if (material == null) return null;
            Integer dataValue = null;
            if (type == YamlRecipeResultType.VANILLA_DATA) {
                dataValue = parseInteger(map.get("data_value"), 0, 15, context + ".data_value", file, errors);
                if (dataValue == null) {
                    dataValue = parseInteger(map.get("data"), 0, 15, context + ".data", file, errors);
                }
                if (dataValue == null) {
                    dataValue = parseInteger(map.get("damage"), 0, 15, context + ".damage", file, errors);
                }
                if (dataValue == null) {
                    errors.add(context + ".data_value is required for vanilla_data in " + file.getPath());
                    return null;
                }
            }
            return new YamlRecipeResultDefinition(type, material, dataValue, null, null, null, amount, null);
        }

        if (type == YamlRecipeResultType.CUSTOM) {
            Object rawItem = map.get("item");
            if (rawItem == null) rawItem = map.get("custom");
            if (!(rawItem instanceof String)) {
                errors.add(context + ".item must be a string in " + file.getPath());
                return null;
            }
            String rawId = ((String) rawItem).trim();
            if (rawId.isEmpty()) {
                errors.add(context + ".item must not be empty in " + file.getPath());
                return null;
            }
            ParsedId parsed = parseId(rawId, pack.namespace, file, errors, context + ".item");
            if (parsed == null) return null;
            return new YamlRecipeResultDefinition(type, null, null, parsed.internalName, null, null, amount, null);
        }

        if (type == YamlRecipeResultType.MIMIC || type == YamlRecipeResultType.ITEM_BRIDGE) {
            Object rawId = map.get(type == YamlRecipeResultType.MIMIC ? "mimic" : "item_bridge");
            if (rawId == null && type == YamlRecipeResultType.ITEM_BRIDGE) rawId = map.get("itembridge");
            if (!(rawId instanceof String)) {
                errors.add(context + "." + (type == YamlRecipeResultType.MIMIC ? "mimic" : "item_bridge")
                        + " must be a string in " + file.getPath());
                return null;
            }
            String id = ((String) rawId).trim();
            if (id.isEmpty()) {
                errors.add(context + " id must not be empty in " + file.getPath());
                return null;
            }
            return new YamlRecipeResultDefinition(type, null, null, null, id, null, amount, null);
        }

        if (type == YamlRecipeResultType.COPIED) {
            Object rawEncoded = map.get("encoded");
            if (rawEncoded == null) rawEncoded = map.get("copied");
            if (!(rawEncoded instanceof String)) {
                errors.add(context + ".encoded must be a string in " + file.getPath());
                return null;
            }
            String encoded = ((String) rawEncoded).trim();
            if (encoded.isEmpty()) {
                errors.add(context + ".encoded must not be empty in " + file.getPath());
                return null;
            }
            return new YamlRecipeResultDefinition(type, null, null, null, null, encoded, amount, null);
        }

        if (type == YamlRecipeResultType.UPGRADE) {
            Object rawUpgrade = map.get("upgrade");
            if (rawUpgrade == null) rawUpgrade = map;
            YamlUpgradeResultDefinition upgrade = parseUpgradeDefinition(
                    rawUpgrade, pack, file, errors, null, context + ".upgrade"
            );
            if (upgrade == null) return null;
            return new YamlRecipeResultDefinition(type, null, null, null, null, null, amount, upgrade);
        }

        errors.add("Unsupported result type " + type + " in " + file.getPath());
        return null;
    }

    private static YamlUpgradeResultDefinition parseUpgradeDefinition(
            Object rawValue,
            YamlPackDefinition pack,
            File file,
            List<String> errors,
            List<String> warnings,
            String context
    ) {
        Map<?, ?> map = toMap(rawValue);
        if (map == null) {
            errors.add(context + " must be a map in " + file.getPath());
            return null;
        }

        Integer ingredientIndex = parseInteger(map.get("ingredient_index"), 0, 8,
                context + ".ingredient_index", file, errors);
        if (ingredientIndex == null) {
            ingredientIndex = parseInteger(map.get("ingredient"), 0, 8,
                    context + ".ingredient", file, errors);
        }

        String inputSlotName = parseOptionalString(map.get("input_slot"), context + ".input_slot", file, warnings);
        if (inputSlotName == null) {
            inputSlotName = parseOptionalString(map.get("input_slot_name"), context + ".input_slot_name", file, warnings);
        }

        Object rawUpgrades = map.get("upgrades");
        List<String> upgrades = Collections.emptyList();
        if (rawUpgrades != null) {
            if (!(rawUpgrades instanceof List<?>)) {
                errors.add(context + ".upgrades must be a list in " + file.getPath());
                return null;
            }
            List<?> list = (List<?>) rawUpgrades;
            List<String> parsedUpgrades = new ArrayList<>(list.size());
            for (Object entry : list) {
                if (!(entry instanceof String)) {
                    errors.add(context + ".upgrades must contain strings in " + file.getPath());
                    return null;
                }
                String name = ((String) entry).trim();
                if (name.isEmpty()) {
                    errors.add(context + ".upgrades entry must not be empty in " + file.getPath());
                    return null;
                }
                parsedUpgrades.add(name);
            }
            upgrades = parsedUpgrades;
        }

        Float repairPercentage = parseFloat(map.get("repair_percentage"), context + ".repair_percentage", file, errors);
        if (repairPercentage == null) {
            repairPercentage = parseFloat(map.get("repair"), context + ".repair", file, errors);
        }

        YamlRecipeResultDefinition newType = null;
        Object rawNewType = map.get("new_type");
        if (rawNewType == null) rawNewType = map.get("new_result");
        if (rawNewType == null) rawNewType = map.get("new");
        if (rawNewType != null) {
            newType = parseResultDefinition(rawNewType, pack, file, errors, context + ".new_type");
            if (newType == null) return null;
        }

        Boolean keepOldUpgrades = parseBoolean(map.get("keep_old_upgrades"), true,
                context + ".keep_old_upgrades", file, warnings);
        Boolean keepOldEnchantments = parseBoolean(map.get("keep_old_enchantments"), true,
                context + ".keep_old_enchantments", file, warnings);

        if (ingredientIndex == null && (inputSlotName == null || inputSlotName.isEmpty())) {
            errors.add(context + " must set ingredient_index or input_slot in " + file.getPath());
            return null;
        }

        return new YamlUpgradeResultDefinition(
                ingredientIndex,
                inputSlotName,
                upgrades,
                repairPercentage,
                newType,
                keepOldUpgrades,
                keepOldEnchantments
        );
    }

    private static YamlRecipeConstraintsDefinition parseConstraints(
            Object rawValue, File file, List<String> errors, String context
    ) {
        Map<?, ?> map = toMap(rawValue);
        if (map == null) {
            errors.add(context + " must be a map in " + file.getPath());
            return null;
        }

        List<YamlDurabilityConstraintDefinition> durabilityConstraints = parseDurabilityConstraints(
                map.get("durability"), file, errors, context + ".durability"
        );
        if (durabilityConstraints == null) return null;

        List<YamlEnchantmentConstraintDefinition> enchantmentConstraints = parseEnchantmentConstraints(
                map.get("enchantments"), file, errors, context + ".enchantments"
        );
        if (enchantmentConstraints == null) return null;

        List<YamlVariableConstraintDefinition> variableConstraints = parseVariableConstraints(
                map.get("variables"), file, errors, context + ".variables"
        );
        if (variableConstraints == null) return null;

        return new YamlRecipeConstraintsDefinition(durabilityConstraints, enchantmentConstraints, variableConstraints);
    }

    private static List<YamlDurabilityConstraintDefinition> parseDurabilityConstraints(
            Object rawValue, File file, List<String> errors, String context
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            errors.add(context + " must be a list in " + file.getPath());
            return null;
        }

        List<YamlDurabilityConstraintDefinition> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            String entryContext = context + "[" + index + "]";
            if (entry instanceof String) {
                String raw = ((String) entry).trim();
                if (raw.isEmpty()) {
                    errors.add(entryContext + " must not be empty in " + file.getPath());
                    return null;
                }
                ParsedConstraint parsed = parseConstraintString(raw, entryContext, file, errors);
                if (parsed == null) return null;
                result.add(new YamlDurabilityConstraintDefinition(parsed.operator, parsed.floatValue));
            } else {
                Map<?, ?> map = toMap(entry);
                if (map == null) {
                    errors.add(entryContext + " must be a string or map in " + file.getPath());
                    return null;
                }
                ConstraintOperator operator = parseConstraintOperator(
                        map.get("operator"), entryContext + ".operator", file, errors
                );
                if (operator == null) return null;
                Float percentage = parseFloat(map.get("percentage"), entryContext + ".percentage", file, errors);
                if (percentage == null) {
                    percentage = parseFloat(map.get("percent"), entryContext + ".percent", file, errors);
                }
                if (percentage == null) {
                    errors.add(entryContext + ".percentage is required in " + file.getPath());
                    return null;
                }
                result.add(new YamlDurabilityConstraintDefinition(operator, percentage));
            }
            index++;
        }

        return result;
    }

    private static List<YamlEnchantmentConstraintDefinition> parseEnchantmentConstraints(
            Object rawValue, File file, List<String> errors, String context
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            errors.add(context + " must be a list in " + file.getPath());
            return null;
        }

        List<YamlEnchantmentConstraintDefinition> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            String entryContext = context + "[" + index + "]";
            Map<?, ?> map = toMap(entry);
            if (map == null) {
                errors.add(entryContext + " must be a map in " + file.getPath());
                return null;
            }
            Object rawEnchant = map.get("enchantment");
            if (rawEnchant == null) rawEnchant = map.get("id");
            if (!(rawEnchant instanceof String)) {
                errors.add(entryContext + ".enchantment must be a string in " + file.getPath());
                return null;
            }
            VEnchantmentType enchantment = parseEnchantmentType((String) rawEnchant);
            if (enchantment == null) {
                errors.add(entryContext + ".enchantment is unknown in " + file.getPath());
                return null;
            }
            ConstraintOperator operator = parseConstraintOperator(
                    map.get("operator"), entryContext + ".operator", file, errors
            );
            if (operator == null) return null;
            Integer level = parseInteger(map.get("level"), 0, Integer.MAX_VALUE,
                    entryContext + ".level", file, errors);
            if (level == null) return null;
            result.add(new YamlEnchantmentConstraintDefinition(enchantment, operator, level));
            index++;
        }

        return result;
    }

    private static List<YamlVariableConstraintDefinition> parseVariableConstraints(
            Object rawValue, File file, List<String> errors, String context
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            errors.add(context + " must be a list in " + file.getPath());
            return null;
        }

        List<YamlVariableConstraintDefinition> result = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            String entryContext = context + "[" + index + "]";
            Map<?, ?> map = toMap(entry);
            if (map == null) {
                errors.add(entryContext + " must be a map in " + file.getPath());
                return null;
            }
            Object rawVariable = map.get("variable");
            if (rawVariable == null) rawVariable = map.get("name");
            if (!(rawVariable instanceof String)) {
                errors.add(entryContext + ".variable must be a string in " + file.getPath());
                return null;
            }
            String variable = ((String) rawVariable).trim();
            if (variable.isEmpty()) {
                errors.add(entryContext + ".variable must not be empty in " + file.getPath());
                return null;
            }
            ConstraintOperator operator = parseConstraintOperator(
                    map.get("operator"), entryContext + ".operator", file, errors
            );
            if (operator == null) return null;
            Integer value = parseInteger(map.get("value"), Integer.MIN_VALUE, Integer.MAX_VALUE,
                    entryContext + ".value", file, errors);
            if (value == null) return null;
            result.add(new YamlVariableConstraintDefinition(variable, operator, value));
            index++;
        }

        return result;
    }

    private static ConstraintOperator parseConstraintOperator(
            Object rawValue, String fieldName, File file, List<String> errors
    ) {
        if (!(rawValue instanceof String)) {
            errors.add(fieldName + " must be a string in " + file.getPath());
            return null;
        }
        String trimmed = ((String) rawValue).trim();
        if (trimmed.isEmpty()) {
            errors.add(fieldName + " must not be empty in " + file.getPath());
            return null;
        }
        for (ConstraintOperator operator : ConstraintOperator.values()) {
            if (operator.token.equals(trimmed)) {
                return operator;
            }
        }
        try {
            return ConstraintOperator.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add("Unknown operator '" + trimmed + "' in " + file.getPath());
            return null;
        }
    }

    private static ParsedConstraint parseConstraintString(
            String rawValue, String context, File file, List<String> errors
    ) {
        String trimmed = rawValue.trim();
        for (String token : new String[]{">=", "<=", ">", "<", "="}) {
            if (trimmed.startsWith(token)) {
                String rest = trimmed.substring(token.length()).trim();
                Float value = parseFloat(rest, context, file, errors);
                if (value == null) return null;
                ConstraintOperator operator = parseConstraintOperator(token, context, file, errors);
                if (operator == null) return null;
                return new ParsedConstraint(operator, value, value.intValue());
            }
        }
        errors.add(context + " must start with a comparison operator in " + file.getPath());
        return null;
    }

    private static class ParsedConstraint {
        final ConstraintOperator operator;
        final float floatValue;
        final int intValue;

        ParsedConstraint(ConstraintOperator operator, float floatValue, int intValue) {
            this.operator = operator;
            this.floatValue = floatValue;
            this.intValue = intValue;
        }
    }

    private static YamlRecipeIngredientType parseIngredientType(
            String rawType, String context, File file, List<String> errors
    ) {
        String trimmed = rawType.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            errors.add(context + ".type must not be empty in " + file.getPath());
            return null;
        }
        switch (trimmed) {
            case "none":
            case "empty":
                return YamlRecipeIngredientType.NONE;
            case "vanilla":
                return YamlRecipeIngredientType.VANILLA;
            case "vanilla_data":
            case "vanilla-data":
            case "data":
                return YamlRecipeIngredientType.VANILLA_DATA;
            case "custom":
            case "item":
                return YamlRecipeIngredientType.CUSTOM;
            case "mimic":
                return YamlRecipeIngredientType.MIMIC;
            case "item_bridge":
            case "itembridge":
                return YamlRecipeIngredientType.ITEM_BRIDGE;
            case "copied":
            case "encoded":
                return YamlRecipeIngredientType.COPIED;
            default:
                errors.add("Unknown " + context + ".type '" + rawType + "' in " + file.getPath());
                return null;
        }
    }

    private static YamlRecipeResultType parseResultType(
            String rawType, String context, File file, List<String> errors
    ) {
        String trimmed = rawType.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            errors.add(context + ".type must not be empty in " + file.getPath());
            return null;
        }
        switch (trimmed) {
            case "custom":
            case "item":
                return YamlRecipeResultType.CUSTOM;
            case "vanilla":
                return YamlRecipeResultType.VANILLA;
            case "vanilla_data":
            case "vanilla-data":
            case "data":
                return YamlRecipeResultType.VANILLA_DATA;
            case "mimic":
                return YamlRecipeResultType.MIMIC;
            case "item_bridge":
            case "itembridge":
                return YamlRecipeResultType.ITEM_BRIDGE;
            case "copied":
            case "encoded":
                return YamlRecipeResultType.COPIED;
            case "upgrade":
                return YamlRecipeResultType.UPGRADE;
            default:
                errors.add("Unknown " + context + ".type '" + rawType + "' in " + file.getPath());
                return null;
        }
    }

    private static ParsedId parseId(
            String rawId,
            String defaultNamespace,
            File sourceFile,
            List<String> errors,
            String context
    ) {
        return YamlParseUtils.parseId(rawId, defaultNamespace, sourceFile, errors, context);
    }

    private static VMaterial parseVMaterial(
            Object rawValue, String fieldName, File file, List<String> errors
    ) {
        return YamlParseUtils.parseVMaterial(rawValue, fieldName, file, errors);
    }

    private static Integer parseInteger(
            Object rawValue, int min, int max, String fieldName, File file, List<String> errors
    ) {
        return YamlParseUtils.parseOptionalInteger(rawValue, min, max, fieldName, file, errors);
    }

    private static Float parseFloat(
            Object rawValue, String fieldName, File file, List<String> errors
    ) {
        return YamlParseUtils.parseOptionalFloat(rawValue, fieldName, file, errors);
    }

    private static Boolean parseBoolean(
            Object rawValue, boolean defaultValue, String fieldName, File file, List<String> warnings
    ) {
        return YamlParseUtils.parseBoolean(rawValue, defaultValue, fieldName, file, warnings);
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File file, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalString(rawValue, fieldName, file, warnings);
    }

    private static VEnchantmentType parseEnchantmentType(String rawId) {
        String normalized = normalizeEnumKey(rawId);
        if (normalized == null) return null;
        try {
            return VEnchantmentType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // continue
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        for (VEnchantmentType type : VEnchantmentType.values()) {
            if (type.getKey().equalsIgnoreCase(lowered)) return type;
        }
        return null;
    }
}
