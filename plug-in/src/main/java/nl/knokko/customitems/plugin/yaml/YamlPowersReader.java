package nl.knokko.customitems.plugin.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

public class YamlPowersReader {

    private static final String TRIGGER_RIGHT_CLICK = "RIGHT_CLICK";
    private static final String TRIGGER_SHIFT_RIGHT_CLICK = "SHIFT_RIGHT_CLICK";
    private static final String TRIGGER_ANY_RIGHT_CLICK = "ANY_RIGHT_CLICK";
    private static final String TRIGGER_LEFT_CLICK = "LEFT_CLICK";
    private static final String TRIGGER_SHIFT_LEFT_CLICK = "SHIFT_LEFT_CLICK";
    private static final String TRIGGER_ANY_LEFT_CLICK = "ANY_LEFT_CLICK";
    private static final String TRIGGER_START_HOLD_HAND = "START_HOLD_HAND";
    private static final String TRIGGER_START_HOLD_OFF_HAND = "START_HOLD_OFF_HAND";
    private static final String TRIGGER_START_HOLD_ANY_HAND = "START_HOLD_ANY_HAND";
    private static final String TRIGGER_FINISH_HOLD_HAND = "FINISH_HOLD_HAND";
    private static final String TRIGGER_FINISH_HOLD_OFF_HAND = "FINISH_HOLD_OFF_HAND";
    private static final String TRIGGER_FINISH_HOLD_ANY_HAND = "FINISH_HOLD_ANY_HAND";
    private static final String TRIGGER_HOLD_INVENTORY = "HOLD_INVENTORY";
    private static final String TRIGGER_DROP = "DROP";
    private static final String TRIGGER_PICK_UP = "PICK_UP";

    private YamlPowersReader() {}

    public static YamlPowersDefinition readDefinitions(File dataFolder, List<String> errors, List<String> warnings) {
        File[] packDirs = dataFolder.listFiles(File::isDirectory);
        if (packDirs == null) return YamlPowersDefinition.empty();

        Map<String, YamlPowerAbilityDefinition> abilitiesById = new LinkedHashMap<>();
        Map<String, YamlPowerDefinition> powersById = new LinkedHashMap<>();
        Map<String, ItemPowerReferences> itemReferencesByInternalName = new LinkedHashMap<>();

        for (File packDir : packDirs) {
            YamlPackDefinition pack = YamlPackReader.readPack(packDir, errors);
            if (pack == null) continue;

            forEachYamlDocument(pack, errors, (file, config) -> {
                parseAbilityDefinitions(pack, file, config, abilitiesById, errors, warnings);
                parsePowerDefinitions(pack, file, config, powersById, errors, warnings);
                parseItemReferences(pack, file, config, itemReferencesByInternalName, errors, warnings);
            });
        }

        return resolveDefinitions(abilitiesById, powersById, itemReferencesByInternalName, errors, warnings);
    }

    private static void parseAbilityDefinitions(
            YamlPackDefinition pack,
            File sourceFile,
            YamlConfiguration config,
            Map<String, YamlPowerAbilityDefinition> abilitiesById,
            List<String> errors,
            List<String> warnings
    ) {
        boolean hasExplicitAbilityRoot = false;
        ConfigurationSection singleSection = config.getConfigurationSection("ability");
        if (singleSection != null) {
            parseAbilityDefinition(pack, sourceFile, singleSection, "ability", abilitiesById, errors, warnings);
            hasExplicitAbilityRoot = true;
        } else if (config.isSet("ability")) {
            warnOptional(warnings, "ability must be a map in " + sourceFile.getPath());
            hasExplicitAbilityRoot = true;
        }

        Object rawAbilities = config.get("abilities");
        if (YamlDefinitionDetector.isAbilityDefinitionRootValue(rawAbilities)) {
            hasExplicitAbilityRoot = true;
            if (rawAbilities instanceof List<?>) {
                int index = 0;
                for (Object rawEntry : (List<?>) rawAbilities) {
                    parseAbilityDefinition(pack, sourceFile, rawEntry, "abilities[" + index + "]",
                            abilitiesById, errors, warnings);
                    index++;
                }
                return;
            }

            parseAbilityDefinition(pack, sourceFile, rawAbilities, "abilities", abilitiesById, errors, warnings);
            return;
        }

        if (!hasExplicitAbilityRoot && YamlDefinitionDetector.shouldUseImplicitRoot(
                config, sourceFile, YamlDefinitionDetector.DefinitionType.ABILITY
        )) {
            parseAbilityDefinition(pack, sourceFile, config, "ability", abilitiesById, errors, warnings);
        }
    }

    private static void parseAbilityDefinition(
            YamlPackDefinition pack,
            File sourceFile,
            Object rawDefinition,
            String context,
            Map<String, YamlPowerAbilityDefinition> abilitiesById,
            List<String> errors,
            List<String> warnings
    ) {
        Map<?, ?> map = toMap(rawDefinition);
        if (map == null) {
            warnOptional(warnings, context + " must be a map in " + sourceFile.getPath());
            return;
        }

        String rawId = parseRequiredString(map.get("id"), context + ".id", sourceFile, errors);
        if (rawId == null) return;

        ParsedId parsedId = parseId(rawId, pack.namespace, sourceFile, errors, context);
        if (parsedId == null) return;

        String rawType = parseOptionalString(map.get("type"), context + ".type", sourceFile, warnings);
        YamlPowerAbilityType abilityType = parseAbilityType(rawType, context, sourceFile, errors);
        if (abilityType == null) return;

        String name = parseOptionalString(map.get("name"), context + ".name", sourceFile, warnings);
        if (name == null) name = parsedId.name;

        YamlPowerAbilityDefinition definition;
        if (abilityType == YamlPowerAbilityType.PASSIVE_POTION_EFFECT) {
            definition = parsePassivePotionAbility(parsedId, name, pack, map, context, sourceFile, errors, warnings);
        } else {
            errors.add("Unsupported ability type for " + parsedId.fullId + " in " + sourceFile.getPath());
            return;
        }
        if (definition == null) return;

        YamlPowerAbilityDefinition existing = abilitiesById.putIfAbsent(definition.fullId, definition);
        if (existing != null) {
            errors.add("Duplicate ability id '" + definition.fullId + "' in "
                    + existing.sourceFile.getPath() + " and " + sourceFile.getPath());
        }
    }

    private static YamlPowerAbilityType parseAbilityType(
            String rawType, String context, File sourceFile, List<String> errors
    ) {
        if (rawType == null) return YamlPowerAbilityType.PASSIVE_POTION_EFFECT;

        String normalized = rawType.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.equals("passive_potion_effect")
                || normalized.equals("potion_effect_passive")
                || normalized.equals("potion_passive")) {
            return YamlPowerAbilityType.PASSIVE_POTION_EFFECT;
        }

        errors.add("Unknown " + context + ".type '" + rawType + "' in " + sourceFile.getPath());
        return null;
    }

    private static YamlPowerAbilityDefinition parsePassivePotionAbility(
            ParsedId parsedId,
            String name,
            YamlPackDefinition pack,
            Map<?, ?> map,
            String context,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        Object rawEffect = map.get("potion_effect");
        if (rawEffect == null) rawEffect = map.get("effect");
        if (rawEffect == null) rawEffect = map.get("type_name");

        String effectName = parseRequiredString(rawEffect, context + ".potion_effect", sourceFile, errors);
        if (effectName == null) return null;

        Integer amplifier = parseInteger(map.get("amplifier"), 0, 255,
                context + ".amplifier", sourceFile, warnings);
        if (amplifier == null) {
            amplifier = parseInteger(map.get("level"), 0, 255,
                    context + ".level", sourceFile, warnings);
        }
        if (amplifier == null) amplifier = 0;

        Integer durationTicks = parseInteger(map.get("duration"), 1, Integer.MAX_VALUE,
                context + ".duration", sourceFile, warnings);
        if (durationTicks == null) durationTicks = 200;

        Integer tickInterval = parseInteger(map.get("tick_interval"), 1, Integer.MAX_VALUE,
                context + ".tick_interval", sourceFile, warnings);
        if (tickInterval == null) tickInterval = parseInteger(map.get("interval"), 1, Integer.MAX_VALUE,
                context + ".interval", sourceFile, warnings);
        if (tickInterval == null) tickInterval = 20;

        Boolean ambient = parseBoolean(map.get("ambient"), context + ".ambient", sourceFile, warnings);
        Boolean particles = parseBoolean(map.get("particles"), context + ".particles", sourceFile, warnings);
        Boolean icon = parseBoolean(map.get("icon"), context + ".icon", sourceFile, warnings);
        List<YamlPowerConditionDefinition> conditions = parseConditionDefinitions(
                pack, map.get("conditions"), context + ".conditions", sourceFile, errors, warnings
        );
        Boolean defaultEnabled = parseBoolean(
                map.get("conditions_default_enabled"),
                context + ".conditions_default_enabled",
                sourceFile,
                warnings
        );

        return new YamlPowerAbilityDefinition(
                parsedId.fullId,
                name,
                YamlPowerAbilityType.PASSIVE_POTION_EFFECT,
                effectName,
                amplifier,
                durationTicks,
                ambient != null && ambient,
                particles == null || particles,
                icon == null || icon,
                tickInterval.longValue(),
                conditions,
                defaultEnabled != null && defaultEnabled,
                sourceFile
        );
    }

    private static List<YamlPowerConditionDefinition> parseConditionDefinitions(
            YamlPackDefinition pack,
            Object rawConditions,
            String context,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        if (rawConditions == null) return Collections.emptyList();

        List<YamlPowerConditionDefinition> result = new ArrayList<>();
        if (rawConditions instanceof List<?>) {
            int index = 0;
            for (Object rawEntry : (List<?>) rawConditions) {
                YamlPowerConditionDefinition condition = parseConditionDefinition(
                        pack, rawEntry, context + "[" + index + "]", sourceFile, errors, warnings
                );
                if (condition != null) result.add(condition);
                index++;
            }
            return result;
        }

        YamlPowerConditionDefinition condition = parseConditionDefinition(
                pack, rawConditions, context, sourceFile, errors, warnings
        );
        if (condition != null) result.add(condition);
        return result;
    }

    private static YamlPowerConditionDefinition parseConditionDefinition(
            YamlPackDefinition pack,
            Object rawCondition,
            String context,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        String rawTrigger = null;
        String rawAction = null;
        String rawItemId = null;

        if (rawCondition instanceof String) {
            rawTrigger = ((String) rawCondition).trim();
        } else {
            Map<?, ?> map = toMap(rawCondition);
            if (map == null) {
                warnOptional(warnings, context + " must be a string or map in " + sourceFile.getPath());
                return null;
            }

            Object rawTriggerValue = map.get("trigger");
            if (rawTriggerValue == null) rawTriggerValue = map.get("on");
            // YAML 1.1 may interpret key "on" as boolean true.
            if (rawTriggerValue == null) rawTriggerValue = map.get(Boolean.TRUE);
            if (rawTriggerValue == null) rawTriggerValue = map.get("type");
            rawTrigger = parseOptionalString(rawTriggerValue, context + ".trigger", sourceFile, warnings);
            rawAction = parseOptionalString(map.get("action"), context + ".action", sourceFile, warnings);

            rawItemId = parseOptionalString(map.get("item"), context + ".item", sourceFile, warnings);
            if (rawItemId == null) {
                rawItemId = parseOptionalString(map.get("custom_item"), context + ".custom_item", sourceFile, warnings);
            }
            if (rawItemId == null) {
                rawItemId = parseOptionalString(map.get("item_id"), context + ".item_id", sourceFile, warnings);
            }
        }

        if (rawTrigger == null || rawTrigger.isEmpty()) {
            errors.add("Missing " + context + ".trigger in " + sourceFile.getPath());
            return null;
        }

        String trigger = normalizeConditionTrigger(rawTrigger);
        if (trigger == null) {
            errors.add("Unknown " + context + ".trigger '" + rawTrigger + "' in " + sourceFile.getPath());
            return null;
        }

        String action = parseConditionAction(rawAction, trigger, context, sourceFile, warnings);
        if (action == null) return null;

        String customItemInternalName = null;
        if (rawItemId != null) {
            ParsedId parsedItemId = parseId(rawItemId, pack.namespace, sourceFile, errors, context + ".item");
            if (parsedItemId == null) return null;
            customItemInternalName = parsedItemId.internalName;
        }

        return new YamlPowerConditionDefinition(trigger, action, customItemInternalName);
    }

    private static String parseConditionAction(
            String rawAction, String trigger, String context, File sourceFile, List<String> warnings
    ) {
        if (rawAction == null || rawAction.trim().isEmpty()) {
            return defaultConditionAction(trigger);
        }

        String normalized = rawAction.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("ENABLE".equals(normalized) || "DISABLE".equals(normalized)
                || "TRIGGER".equals(normalized) || "SYNC".equals(normalized)) {
            return normalized;
        }

        warnOptional(warnings, "Unknown " + context + ".action '" + rawAction + "' in " + sourceFile.getPath());
        return null;
    }

    private static String defaultConditionAction(String trigger) {
        if (TRIGGER_START_HOLD_HAND.equals(trigger)
                || TRIGGER_START_HOLD_OFF_HAND.equals(trigger)
                || TRIGGER_START_HOLD_ANY_HAND.equals(trigger)
                || TRIGGER_PICK_UP.equals(trigger)) {
            return "ENABLE";
        }

        if (TRIGGER_FINISH_HOLD_HAND.equals(trigger)
                || TRIGGER_FINISH_HOLD_OFF_HAND.equals(trigger)
                || TRIGGER_FINISH_HOLD_ANY_HAND.equals(trigger)
                || TRIGGER_DROP.equals(trigger)) {
            return "DISABLE";
        }

        if (TRIGGER_HOLD_INVENTORY.equals(trigger)) return "SYNC";
        return "TRIGGER";
    }

    private static String normalizeConditionTrigger(String rawTrigger) {
        if (rawTrigger == null) return null;
        String normalized = rawTrigger.trim();
        if (normalized.isEmpty()) return null;

        normalized = normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ON_")) normalized = normalized.substring(3);
        if ("PICKUP".equals(normalized)) normalized = TRIGGER_PICK_UP;

        if (TRIGGER_RIGHT_CLICK.equals(normalized)
                || TRIGGER_SHIFT_RIGHT_CLICK.equals(normalized)
                || TRIGGER_ANY_RIGHT_CLICK.equals(normalized)
                || TRIGGER_LEFT_CLICK.equals(normalized)
                || TRIGGER_SHIFT_LEFT_CLICK.equals(normalized)
                || TRIGGER_ANY_LEFT_CLICK.equals(normalized)
                || TRIGGER_START_HOLD_HAND.equals(normalized)
                || TRIGGER_START_HOLD_OFF_HAND.equals(normalized)
                || TRIGGER_START_HOLD_ANY_HAND.equals(normalized)
                || TRIGGER_FINISH_HOLD_HAND.equals(normalized)
                || TRIGGER_FINISH_HOLD_OFF_HAND.equals(normalized)
                || TRIGGER_FINISH_HOLD_ANY_HAND.equals(normalized)
                || TRIGGER_HOLD_INVENTORY.equals(normalized)
                || TRIGGER_DROP.equals(normalized)
                || TRIGGER_PICK_UP.equals(normalized)) {
            return normalized;
        }

        return null;
    }

    private static void parsePowerDefinitions(
            YamlPackDefinition pack,
            File sourceFile,
            YamlConfiguration config,
            Map<String, YamlPowerDefinition> powersById,
            List<String> errors,
            List<String> warnings
    ) {
        boolean hasExplicitPowerRoot = false;
        ConfigurationSection singleSection = config.getConfigurationSection("power");
        if (singleSection != null) {
            parsePowerDefinition(pack, sourceFile, singleSection, "power", powersById, errors, warnings);
            hasExplicitPowerRoot = true;
        } else if (config.isSet("power")) {
            warnOptional(warnings, "power must be a map in " + sourceFile.getPath());
            hasExplicitPowerRoot = true;
        }

        Object rawPowers = config.get("powers");
        if (YamlDefinitionDetector.isPowerDefinitionRootValue(rawPowers)) {
            hasExplicitPowerRoot = true;
            if (rawPowers instanceof List<?>) {
                int index = 0;
                for (Object rawEntry : (List<?>) rawPowers) {
                    parsePowerDefinition(pack, sourceFile, rawEntry, "powers[" + index + "]",
                            powersById, errors, warnings);
                    index++;
                }
                return;
            }

            parsePowerDefinition(pack, sourceFile, rawPowers, "powers", powersById, errors, warnings);
            return;
        }

        if (!hasExplicitPowerRoot && YamlDefinitionDetector.shouldUseImplicitRoot(
                config, sourceFile, YamlDefinitionDetector.DefinitionType.POWER
        )) {
            parsePowerDefinition(pack, sourceFile, config, "power", powersById, errors, warnings);
        }
    }

    private static void parsePowerDefinition(
            YamlPackDefinition pack,
            File sourceFile,
            Object rawDefinition,
            String context,
            Map<String, YamlPowerDefinition> powersById,
            List<String> errors,
            List<String> warnings
    ) {
        Map<?, ?> map = toMap(rawDefinition);
        if (map == null) {
            warnOptional(warnings, context + " must be a map in " + sourceFile.getPath());
            return;
        }

        String rawId = parseRequiredString(map.get("id"), context + ".id", sourceFile, errors);
        if (rawId == null) return;

        ParsedId parsedId = parseId(rawId, pack.namespace, sourceFile, errors, context);
        if (parsedId == null) return;

        String name = parseOptionalString(map.get("name"), context + ".name", sourceFile, warnings);
        if (name == null) name = parsedId.name;

        List<String> parsedAbilityIds = parsePowerAbilityIds(pack, map, context, sourceFile, errors, warnings);
        if (parsedAbilityIds.isEmpty()) {
            errors.add("Power " + parsedId.fullId + " must define at least 1 ability in " + sourceFile.getPath());
            return;
        }

        String rarity = parseOptionalString(map.get("rarity"), context + ".rarity", sourceFile, warnings);
        String alignment = parseAlignment(parseOptionalString(
                map.get("alignment"), context + ".alignment", sourceFile, warnings
        ), context, sourceFile, warnings);
        String icon = parseOptionalString(map.get("icon"), context + ".icon", sourceFile, warnings);
        if (icon == null) {
            icon = parseOptionalString(map.get("icon_key"), context + ".icon_key", sourceFile, warnings);
        }
        List<String> description = parseStringList(map.get("description"), context + ".description", sourceFile, warnings);

        YamlPowerDefinition definition = new YamlPowerDefinition(
                parsedId.fullId, name, parsedAbilityIds, rarity, alignment, icon, description, sourceFile
        );
        YamlPowerDefinition existing = powersById.putIfAbsent(definition.fullId, definition);
        if (existing != null) {
            errors.add("Duplicate power id '" + definition.fullId + "' in "
                    + existing.sourceFile.getPath() + " and " + sourceFile.getPath());
        }
    }

    private static List<String> parsePowerAbilityIds(
            YamlPackDefinition pack,
            Map<?, ?> map,
            String context,
            File sourceFile,
            List<String> errors,
            List<String> warnings
    ) {
        Object rawAbilities = map.get("abilities");
        if (rawAbilities == null) rawAbilities = map.get("passive_abilities");
        List<String> abilityRefs = parseStringList(rawAbilities, context + ".abilities", sourceFile, warnings);
        if (abilityRefs.isEmpty()) {
            String singleAbility = parseOptionalString(map.get("ability"), context + ".ability", sourceFile, warnings);
            if (singleAbility != null) {
                abilityRefs = Collections.singletonList(singleAbility);
            }
        }

        List<String> abilityIds = new ArrayList<>(abilityRefs.size());
        for (String rawAbilityId : abilityRefs) {
            ParsedId parsed = parseId(rawAbilityId, pack.namespace, sourceFile, errors, context + ".abilities");
            if (parsed != null) {
                abilityIds.add(parsed.fullId);
            }
        }
        return abilityIds;
    }

    private static String parseAlignment(
            String rawAlignment, String context, File sourceFile, List<String> warnings
    ) {
        if (rawAlignment == null) return null;
        String normalized = normalizeEnumKey(rawAlignment);
        if (normalized == null) {
            warnOptional(warnings, "Invalid " + context + ".alignment in " + sourceFile.getPath());
            return null;
        }
        if (!"NEUTRAL".equals(normalized) && !"LIGHT".equals(normalized) && !"DARK".equals(normalized)) {
            warnOptional(warnings, "Unknown " + context + ".alignment '" + rawAlignment + "' in " + sourceFile.getPath());
            return null;
        }
        return normalized;
    }

    private static void parseItemReferences(
            YamlPackDefinition pack,
            File sourceFile,
            YamlConfiguration config,
            Map<String, ItemPowerReferences> itemReferencesByInternalName,
            List<String> errors,
            List<String> warnings
    ) {
        ConfigurationSection itemSection = config.getConfigurationSection("item");
        if (itemSection == null && YamlDefinitionDetector.shouldUseImplicitRoot(
                config, sourceFile, YamlDefinitionDetector.DefinitionType.ITEM
        )) {
            itemSection = config;
        }
        if (itemSection == null) return;

        Object rawPowerRefs = itemSection.get("powers");
        Object rawAbilityRefs = itemSection.get("abilities");
        if (rawPowerRefs == null && rawAbilityRefs == null) return;

        String rawItemId = parseRequiredString(itemSection.get("id"), "item.id", sourceFile, errors);
        if (rawItemId == null) return;
        ParsedId parsedItemId = parseId(rawItemId, pack.namespace, sourceFile, errors, "item");
        if (parsedItemId == null) return;

        LinkedHashSet<String> powerIds = parseOptionalIdList(
                parseStringList(rawPowerRefs, "item.powers", sourceFile, warnings),
                pack.namespace, sourceFile, errors, "item.powers"
        );
        LinkedHashSet<String> abilityIds = parseOptionalIdList(
                parseStringList(rawAbilityRefs, "item.abilities", sourceFile, warnings),
                pack.namespace, sourceFile, errors, "item.abilities"
        );
        if (powerIds.isEmpty() && abilityIds.isEmpty()) return;

        ItemPowerReferences references = itemReferencesByInternalName.computeIfAbsent(
                parsedItemId.internalName, ignored -> new ItemPowerReferences(parsedItemId.internalName, sourceFile)
        );
        references.powerIds.addAll(powerIds);
        references.abilityIds.addAll(abilityIds);
    }

    private static LinkedHashSet<String> parseOptionalIdList(
            List<String> rawIds,
            String defaultNamespace,
            File sourceFile,
            List<String> errors,
            String context
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>(rawIds.size());
        for (String rawId : rawIds) {
            ParsedId parsed = parseId(rawId, defaultNamespace, sourceFile, errors, context);
            if (parsed != null) result.add(parsed.fullId);
        }
        return result;
    }

    private static YamlPowersDefinition resolveDefinitions(
            Map<String, YamlPowerAbilityDefinition> abilitiesById,
            Map<String, YamlPowerDefinition> rawPowersById,
            Map<String, ItemPowerReferences> itemReferencesByInternalName,
            List<String> errors,
            List<String> warnings
    ) {
        Map<String, YamlPowerDefinition> powersById = new LinkedHashMap<>();
        for (YamlPowerDefinition rawPower : rawPowersById.values()) {
            boolean valid = true;
            for (String abilityId : rawPower.abilityIds) {
                if (!abilitiesById.containsKey(abilityId)) {
                    errors.add("Power " + rawPower.fullId + " references unknown ability " + abilityId
                            + " in " + rawPower.sourceFile.getPath());
                    valid = false;
                }
            }
            if (valid) {
                powersById.put(rawPower.fullId, rawPower);
            }
        }

        Map<String, Set<String>> itemPowersByInternalName = new LinkedHashMap<>();
        for (ItemPowerReferences references : itemReferencesByInternalName.values()) {
            LinkedHashSet<String> resolvedPowerIds = new LinkedHashSet<>();

            for (String powerId : references.powerIds) {
                if (powersById.containsKey(powerId)) {
                    resolvedPowerIds.add(powerId);
                } else {
                    warnOptional(warnings, "Item " + references.itemInternalName + " references unknown power "
                            + powerId + " in " + references.sourceFile.getPath());
                }
            }

            LinkedHashSet<String> resolvedAbilityIds = new LinkedHashSet<>();
            for (String abilityId : references.abilityIds) {
                if (abilitiesById.containsKey(abilityId)) {
                    resolvedAbilityIds.add(abilityId);
                } else {
                    warnOptional(warnings, "Item " + references.itemInternalName + " references unknown ability "
                            + abilityId + " in " + references.sourceFile.getPath());
                }
            }

            if (!resolvedAbilityIds.isEmpty()) {
                String syntheticPowerId = "kci_item:" + references.itemInternalName;
                YamlPowerDefinition syntheticPower = new YamlPowerDefinition(
                        syntheticPowerId,
                        "Item power " + references.itemInternalName,
                        new ArrayList<>(resolvedAbilityIds),
                        null,
                        "NEUTRAL",
                        null,
                        Collections.singletonList("Auto-generated from item.abilities"),
                        references.sourceFile
                );
                YamlPowerDefinition existing = powersById.putIfAbsent(syntheticPowerId, syntheticPower);
                if (existing != null && existing != syntheticPower) {
                    errors.add("Can't generate synthetic power " + syntheticPowerId
                            + " for item " + references.itemInternalName
                            + " because this power id is already in use ("
                            + existing.sourceFile.getPath() + ")");
                } else {
                    resolvedPowerIds.add(syntheticPowerId);
                }
            }

            if (!resolvedPowerIds.isEmpty()) {
                itemPowersByInternalName.put(references.itemInternalName, resolvedPowerIds);
            }
        }

        return new YamlPowersDefinition(abilitiesById, powersById, itemPowersByInternalName);
    }

    private static class ItemPowerReferences {

        final String itemInternalName;
        final File sourceFile;
        final Set<String> powerIds = new LinkedHashSet<>();
        final Set<String> abilityIds = new LinkedHashSet<>();

        ItemPowerReferences(String itemInternalName, File sourceFile) {
            this.itemInternalName = itemInternalName;
            this.sourceFile = sourceFile;
        }
    }
}
