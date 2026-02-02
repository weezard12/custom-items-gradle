
package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.MCVersions;
import nl.knokko.customitems.effect.VEffectType;
import nl.knokko.customitems.particle.VParticle;
import nl.knokko.customitems.projectile.effect.PEExecuteCommand;
import nl.knokko.customitems.projectile.effect.PEShowFireworks;
import nl.knokko.customitems.plugin.yaml.YamlParseUtils.FloatRange;
import nl.knokko.customitems.plugin.yaml.YamlParseUtils.ParsedId;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static nl.knokko.customitems.nms.KciNms.mcVersion;
import static nl.knokko.customitems.plugin.yaml.YamlParseUtils.*;

class YamlProjectileReader {

    static List<YamlProjectileDefinition> readProjectiles(
            YamlPackDefinition pack, List<String> errors, List<String> warnings
    ) {
        List<YamlProjectileDefinition> projectiles = new ArrayList<>();
        forEachYamlDocument(pack, errors, (file, config) -> {
            ConfigurationSection projectileSection = config.getConfigurationSection("projectile");
            if (projectileSection == null) return;

            int errorCountBefore = errors.size();
            YamlProjectileDefinition projectile = parseProjectileDefinition(
                    pack, file, projectileSection, errors, warnings
            );
            if (errors.size() != errorCountBefore) return;
            if (projectile != null) {
                projectiles.add(projectile);
            }
        });
        return projectiles;
    }

    private static YamlProjectileDefinition parseProjectileDefinition(
            YamlPackDefinition pack,
            File file,
            ConfigurationSection projectileSection,
            List<String> errors,
            List<String> warnings
    ) {
        String rawId = projectileSection.getString("id");
        if (rawId == null || rawId.trim().isEmpty()) {
            errors.add("Missing projectile.id in " + file.getPath());
            return null;
        }

        ParsedId parsedId = parseId(rawId.trim(), pack.namespace, file, errors);
        if (parsedId == null) return null;

        int errorCountBefore = errors.size();

        ConfigurationSection requiresSection = getChildSection(projectileSection, "requires", file, warnings);
        if (!matchesRequires(requiresSection, file, warnings)) {
            return null;
        }

        FloatRange angleRange = null;
        Float minLaunchAngle = parseOptionalFloat(
                projectileSection.get("min_launch_angle"), 0f, Float.MAX_VALUE, "min_launch_angle", file, warnings
        );
        Float maxLaunchAngle = parseOptionalFloat(
                projectileSection.get("max_launch_angle"), 0f, Float.MAX_VALUE, "max_launch_angle", file, warnings
        );
        if (minLaunchAngle == null && maxLaunchAngle == null) {
            angleRange = parseOptionalRange(projectileSection.get("launch_angle"), "launch_angle", file, warnings);
        } else if (minLaunchAngle == null || maxLaunchAngle == null) {
            warnOptional(warnings, "projectile.launch_angle requires both min and max in " + file.getPath());
            minLaunchAngle = null;
            maxLaunchAngle = null;
        }

        FloatRange speedRange = null;
        Float minLaunchSpeed = parseOptionalFloat(
                projectileSection.get("min_launch_speed"), 0f, Float.MAX_VALUE, "min_launch_speed", file, warnings
        );
        Float maxLaunchSpeed = parseOptionalFloat(
                projectileSection.get("max_launch_speed"), 0f, Float.MAX_VALUE, "max_launch_speed", file, warnings
        );
        if (minLaunchSpeed == null && maxLaunchSpeed == null) {
            speedRange = parseOptionalRange(projectileSection.get("launch_speed"), "launch_speed", file, warnings);
        } else if (minLaunchSpeed == null || maxLaunchSpeed == null) {
            warnOptional(warnings, "projectile.launch_speed requires both min and max in " + file.getPath());
            minLaunchSpeed = null;
            maxLaunchSpeed = null;
        }

        Float damage = parseOptionalFloat(projectileSection.get("damage"), 0f, Float.MAX_VALUE, "damage", file, warnings);
        Float gravity = parseOptionalFloat(projectileSection.get("gravity"), -Float.MAX_VALUE, Float.MAX_VALUE, "gravity", file, warnings);
        Float launchKnockback = parseOptionalFloat(projectileSection.get("launch_knockback"), -Float.MAX_VALUE, Float.MAX_VALUE,
                "launch_knockback", file, warnings);
        Float impactKnockback = parseOptionalFloat(projectileSection.get("impact_knockback"), -Float.MAX_VALUE, Float.MAX_VALUE,
                "impact_knockback", file, warnings);
        Integer maxLifetime = parseOptionalInteger(
                projectileSection.get("max_lifetime"), 1, Integer.MAX_VALUE, "max_lifetime", file, warnings
        );
        Integer maxPiercedEntities = parseOptionalInteger(
                projectileSection.get("max_pierced_entities"), 0, Integer.MAX_VALUE, "max_pierced_entities", file, warnings
        );
        Boolean applyImpactEffectsAtExpiration = parseOptionalBoolean(
                projectileSection.get("apply_impact_effects_at_expiration"),
                "apply_impact_effects_at_expiration", file, warnings
        );
        Boolean applyImpactEffectsAtPierce = parseOptionalBoolean(
                projectileSection.get("apply_impact_effects_at_pierce"),
                "apply_impact_effects_at_pierce", file, warnings
        );

        Object rawCover = projectileSection.get("cover");
        if (rawCover == null) rawCover = projectileSection.get("cover_id");
        String coverId = parseOptionalString(rawCover, "cover", file, warnings);
        String coverInternalName = null;
        if (coverId != null) {
            ParsedId parsedCover = parseOptionalId(coverId, pack.namespace, file, warnings);
            if (parsedCover != null) {
                coverInternalName = parsedCover.internalName;
            }
        }

        String customDamageSource = parseOptionalString(
                projectileSection.get("custom_damage_source"), "custom_damage_source", file, warnings
        );
        if (customDamageSource != null) {
            warnOptional(warnings, "projectile.custom_damage_source is not supported yet in " + file.getPath());
            customDamageSource = null;
        }

        List<YamlPotionEffectDefinition> impactPotionEffects = parsePotionEffects(
                projectileSection.get("impact_potion_effects"),
                "impact_potion_effects", file, warnings
        );
        List<YamlProjectileEffectsDefinition> inFlightEffects = parseProjectileEffectsList(
                projectileSection.get("in_flight_effects"), pack, file, warnings
        );
        List<YamlProjectileEffectDefinition> impactEffects = parseProjectileEffectList(
                projectileSection.get("impact_effects"), pack, file, warnings
        );

        if (angleRange != null) {
            minLaunchAngle = angleRange.min;
            maxLaunchAngle = angleRange.max;
        }
        if (speedRange != null) {
            minLaunchSpeed = speedRange.min;
            maxLaunchSpeed = speedRange.max;
        }

        if (errors.size() != errorCountBefore) return null;

        return new YamlProjectileDefinition(
                parsedId.fullId,
                parsedId.internalName,
                parsedId.name,
                pack.directory,
                file,
                damage,
                minLaunchAngle,
                maxLaunchAngle,
                minLaunchSpeed,
                maxLaunchSpeed,
                gravity,
                launchKnockback,
                impactKnockback,
                impactPotionEffects,
                maxLifetime,
                maxPiercedEntities,
                inFlightEffects,
                impactEffects,
                applyImpactEffectsAtExpiration,
                applyImpactEffectsAtPierce,
                coverInternalName,
                customDamageSource
        );
    }

    private static List<YamlPotionEffectDefinition> parsePotionEffects(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            warnOptional(warnings, "projectile." + fieldName + " must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlPotionEffectDefinition> effects = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            YamlPotionEffectDefinition definition = parsePotionEffect(entry, fieldName + "[" + index + "]", sourceFile, warnings);
            if (definition != null) {
                effects.add(definition);
            }
            index += 1;
        }
        return effects;
    }

    private static YamlPotionEffectDefinition parsePotionEffect(
            Object rawEntry, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (rawEntry instanceof String) {
            return parsePotionEffectString((String) rawEntry, fieldName, sourceFile, warnings);
        }

        ConfigurationSection section = rawEntry instanceof ConfigurationSection ? (ConfigurationSection) rawEntry : null;
        Map<?, ?> map = rawEntry instanceof Map<?, ?> ? (Map<?, ?>) rawEntry : null;
        if (section == null && map == null) {
            warnOptional(warnings, "projectile." + fieldName + " must be a string or map in " + sourceFile.getPath());
            return null;
        }

        Object rawType = section != null ? section.get("type") : map.get("type");
        if (rawType == null) rawType = section != null ? section.get("effect") : map.get("effect");
        VEffectType effectType = parseEffectType(rawType, fieldName + ".type", sourceFile, warnings);
        Integer duration = parseOptionalInteger(
                section != null ? section.get("duration") : map.get("duration"),
                1, Integer.MAX_VALUE, fieldName + ".duration", sourceFile, warnings
        );
        Integer level = parseOptionalInteger(
                section != null ? section.get("level") : map.get("level"),
                1, 256, fieldName + ".level", sourceFile, warnings
        );

        if (effectType == null || duration == null) {
            return null;
        }

        return new YamlPotionEffectDefinition(effectType, duration, level == null ? 1 : level);
    }

    private static YamlPotionEffectDefinition parsePotionEffectString(
            String rawEntry, String fieldName, File sourceFile, List<String> warnings
    ) {
        String trimmed = rawEntry.trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + " is empty in " + sourceFile.getPath());
            return null;
        }

        String typePart = trimmed;
        Integer duration = null;
        Integer level = null;

        String[] parts = trimmed.split(":");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String secondLast = parts.length >= 3 ? parts[parts.length - 2] : null;
            if (isInteger(last)) {
                level = Integer.parseInt(last);
                if (secondLast != null && isInteger(secondLast)) {
                    duration = Integer.parseInt(secondLast);
                    typePart = joinParts(parts, 0, parts.length - 2);
                } else {
                    duration = level;
                    level = null;
                    typePart = joinParts(parts, 0, parts.length - 1);
                }
            }
        }

        VEffectType effectType = parseEffectType(typePart, fieldName + ".type", sourceFile, warnings);
        if (effectType == null || duration == null) {
            warnOptional(warnings, "projectile." + fieldName + " must define duration (use type:duration[:level]) in "
                    + sourceFile.getPath());
            return null;
        }

        if (level == null) level = 1;
        if (duration <= 0) {
            warnOptional(warnings, "projectile." + fieldName + " duration must be positive in " + sourceFile.getPath());
            return null;
        }
        if (level <= 0) {
            warnOptional(warnings, "projectile." + fieldName + " level must be positive in " + sourceFile.getPath());
            return null;
        }

        return new YamlPotionEffectDefinition(effectType, duration, level);
    }

    private static String joinParts(String[] parts, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (index > start) builder.append(':');
            builder.append(parts[index]);
        }
        return builder.toString();
    }

    private static List<YamlProjectileEffectsDefinition> parseProjectileEffectsList(
            Object rawValue, YamlPackDefinition pack, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            warnOptional(warnings, "projectile.in_flight_effects must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlProjectileEffectsDefinition> effects = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            YamlProjectileEffectsDefinition parsed = parseProjectileEffects(entry, pack,
                    "in_flight_effects[" + index + "]", sourceFile, warnings);
            if (parsed != null) {
                effects.add(parsed);
            }
            index += 1;
        }
        return effects;
    }

    private static YamlProjectileEffectsDefinition parseProjectileEffects(
            Object rawEntry, YamlPackDefinition pack, String fieldName, File sourceFile, List<String> warnings
    ) {
        ConfigurationSection section = rawEntry instanceof ConfigurationSection ? (ConfigurationSection) rawEntry : null;
        Map<?, ?> map = rawEntry instanceof Map<?, ?> ? (Map<?, ?>) rawEntry : null;
        if (section == null && map == null) {
            warnOptional(warnings, "projectile." + fieldName + " must be a map in " + sourceFile.getPath());
            return null;
        }

        Object rawDelay = section != null ? section.get("delay") : map.get("delay");
        Object rawPeriod = section != null ? section.get("period") : map.get("period");
        Object rawEffects = section != null ? section.get("effects") : map.get("effects");

        Integer delay = parseOptionalInteger(rawDelay, 0, Integer.MAX_VALUE, fieldName + ".delay", sourceFile, warnings);
        Integer period = parseOptionalInteger(rawPeriod, 1, Integer.MAX_VALUE, fieldName + ".period", sourceFile, warnings);
        List<YamlProjectileEffectDefinition> effects = parseProjectileEffectList(rawEffects, pack, sourceFile, warnings);
        if (effects.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + ".effects must have at least 1 entry in " + sourceFile.getPath());
            return null;
        }
        return new YamlProjectileEffectsDefinition(delay, period, effects);
    }

    private static List<YamlProjectileEffectDefinition> parseProjectileEffectList(
            Object rawValue, YamlPackDefinition pack, File sourceFile, List<String> warnings
    ) {
        if (rawValue == null) return Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            warnOptional(warnings, "projectile.effects must be a list in " + sourceFile.getPath());
            return Collections.emptyList();
        }

        List<YamlProjectileEffectDefinition> effects = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            YamlProjectileEffectDefinition effect = parseProjectileEffect(entry, pack, "effects[" + index + "]", sourceFile, warnings);
            if (effect != null) {
                effects.add(effect);
            }
            index += 1;
        }
        return effects;
    }

    private static YamlProjectileEffectDefinition parseProjectileEffect(
            Object rawEntry, YamlPackDefinition pack, String fieldName, File sourceFile, List<String> warnings
    ) {
        ConfigurationSection section = rawEntry instanceof ConfigurationSection ? (ConfigurationSection) rawEntry : null;
        Map<?, ?> map = rawEntry instanceof Map<?, ?> ? (Map<?, ?>) rawEntry : null;
        if (section == null && map == null) {
            warnOptional(warnings, "projectile." + fieldName + " must be a map in " + sourceFile.getPath());
            return null;
        }

        Object rawType = section != null ? section.get("type") : map.get("type");
        if (rawType == null) rawType = section != null ? section.get("effect") : map.get("effect");
        YamlProjectileEffectType type = parseProjectileEffectType(rawType, fieldName + ".type", sourceFile, warnings);
        if (type == null) return null;

        switch (type) {
            case EXPLOSION:
                return parseExplosionEffect(section, map, fieldName, sourceFile, warnings);
            case COLORED_REDSTONE:
                return parseColoredRedstoneEffect(section, map, fieldName, sourceFile, warnings);
            case SIMPLE_PARTICLE:
                return parseSimpleParticleEffect(section, map, fieldName, sourceFile, warnings);
            case STRAIGHT_ACCELERATION:
            case RANDOM_ACCELERATION:
                return parseAccelerationEffect(type, section, map, fieldName, sourceFile, warnings);
            case SUB_PROJECTILES:
                return parseSubProjectilesEffect(section, map, fieldName, pack, sourceFile, warnings);
            case COMMAND:
                return parseCommandEffect(section, map, fieldName, sourceFile, warnings);
            case PUSH_PULL:
                return parsePushPullEffect(section, map, fieldName, sourceFile, warnings);
            case PLAY_SOUND:
                return parsePlaySoundEffect(section, map, fieldName, sourceFile, warnings);
            case FIREWORKS:
                return parseFireworkEffect(section, map, fieldName, sourceFile, warnings);
            case POTION_AURA:
                return parsePotionAuraEffect(section, map, fieldName, sourceFile, warnings);
            default:
                warnOptional(warnings, "Unsupported projectile effect type in " + sourceFile.getPath());
                return null;
        }
    }

    private static YamlProjectileEffectDefinition parseExplosionEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Object rawPower = section != null ? section.get("power") : map.get("power");
        Float power = parseOptionalFloat(rawPower, 0f, Float.MAX_VALUE, fieldName + ".power", sourceFile, warnings);
        if (power == null) {
            warnOptional(warnings, "projectile." + fieldName + ".power is required in " + sourceFile.getPath());
            return null;
        }
        Boolean destroyBlocks = parseOptionalBoolean(section != null ? section.get("destroy_blocks") : map.get("destroy_blocks"),
                fieldName + ".destroy_blocks", sourceFile, warnings);
        Boolean setFire = parseOptionalBoolean(section != null ? section.get("set_fire") : map.get("set_fire"),
                fieldName + ".set_fire", sourceFile, warnings);
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.EXPLOSION,
                power,
                destroyBlocks,
                setFire,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseColoredRedstoneEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Color minColor = parseColor(getField(section, map, "min_color"), fieldName + ".min_color", sourceFile, warnings);
        Color maxColor = parseColor(getField(section, map, "max_color"), fieldName + ".max_color", sourceFile, warnings);
        if (minColor == null || maxColor == null) {
            Integer minRed = parseOptionalInteger(getField(section, map, "min_red"), 0, 255,
                    fieldName + ".min_red", sourceFile, warnings);
            Integer minGreen = parseOptionalInteger(getField(section, map, "min_green"), 0, 255,
                    fieldName + ".min_green", sourceFile, warnings);
            Integer minBlue = parseOptionalInteger(getField(section, map, "min_blue"), 0, 255,
                    fieldName + ".min_blue", sourceFile, warnings);
            Integer maxRed = parseOptionalInteger(getField(section, map, "max_red"), 0, 255,
                    fieldName + ".max_red", sourceFile, warnings);
            Integer maxGreen = parseOptionalInteger(getField(section, map, "max_green"), 0, 255,
                    fieldName + ".max_green", sourceFile, warnings);
            Integer maxBlue = parseOptionalInteger(getField(section, map, "max_blue"), 0, 255,
                    fieldName + ".max_blue", sourceFile, warnings);

            if (minRed == null || minGreen == null || minBlue == null || maxRed == null || maxGreen == null || maxBlue == null) {
                warnOptional(warnings, "projectile." + fieldName + " requires min/max color in " + sourceFile.getPath());
                return null;
            }

            Float minRadius = parseOptionalFloat(getField(section, map, "min_radius"), 0f, Float.MAX_VALUE,
                    fieldName + ".min_radius", sourceFile, warnings);
            Float maxRadius = parseOptionalFloat(getField(section, map, "max_radius"), 0f, Float.MAX_VALUE,
                    fieldName + ".max_radius", sourceFile, warnings);
            Integer amount = parseOptionalInteger(getField(section, map, "amount"), 1, Integer.MAX_VALUE,
                    fieldName + ".amount", sourceFile, warnings);
            return new YamlProjectileEffectDefinition(
                    YamlProjectileEffectType.COLORED_REDSTONE,
                    null,
                    null,
                    null,
                    minRed,
                    minGreen,
                    minBlue,
                    maxRed,
                    maxGreen,
                    maxBlue,
                    minRadius,
                    maxRadius,
                    amount,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        Float minRadius = parseOptionalFloat(getField(section, map, "min_radius"), 0f, Float.MAX_VALUE,
                fieldName + ".min_radius", sourceFile, warnings);
        Float maxRadius = parseOptionalFloat(getField(section, map, "max_radius"), 0f, Float.MAX_VALUE,
                fieldName + ".max_radius", sourceFile, warnings);
        Integer amount = parseOptionalInteger(getField(section, map, "amount"), 1, Integer.MAX_VALUE,
                fieldName + ".amount", sourceFile, warnings);

        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.COLORED_REDSTONE,
                null,
                null,
                null,
                minColor.getRed(),
                minColor.getGreen(),
                minColor.getBlue(),
                maxColor.getRed(),
                maxColor.getGreen(),
                maxColor.getBlue(),
                minRadius,
                maxRadius,
                amount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseSimpleParticleEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        VParticle particle = parseParticle(getField(section, map, "particle"), fieldName + ".particle", sourceFile, warnings);
        if (particle == null) return null;
        Float minRadius = parseOptionalFloat(getField(section, map, "min_radius"), 0f, Float.MAX_VALUE,
                fieldName + ".min_radius", sourceFile, warnings);
        Float maxRadius = parseOptionalFloat(getField(section, map, "max_radius"), 0f, Float.MAX_VALUE,
                fieldName + ".max_radius", sourceFile, warnings);
        Integer amount = parseOptionalInteger(getField(section, map, "amount"), 1, Integer.MAX_VALUE,
                fieldName + ".amount", sourceFile, warnings);
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.SIMPLE_PARTICLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                particle,
                minRadius,
                maxRadius,
                amount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseAccelerationEffect(
            YamlProjectileEffectType type,
            ConfigurationSection section,
            Map<?, ?> map,
            String fieldName,
            File sourceFile,
            List<String> warnings
    ) {
        Object rawMin = getField(section, map, "min_acceleration");
        if (rawMin == null) rawMin = getField(section, map, "min");
        Object rawMax = getField(section, map, "max_acceleration");
        if (rawMax == null) rawMax = getField(section, map, "max");
        Float min = parseOptionalFloat(rawMin, -Float.MAX_VALUE, Float.MAX_VALUE, fieldName + ".min", sourceFile, warnings);
        Float max = parseOptionalFloat(rawMax, -Float.MAX_VALUE, Float.MAX_VALUE, fieldName + ".max", sourceFile, warnings);
        if (min == null && max != null) min = max;
        if (max == null && min != null) max = min;

        return new YamlProjectileEffectDefinition(
                type,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                min,
                max,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseSubProjectilesEffect(
            ConfigurationSection section,
            Map<?, ?> map,
            String fieldName,
            YamlPackDefinition pack,
            File sourceFile,
            List<String> warnings
    ) {
        String rawChild = parseOptionalString(getField(section, map, "child"), fieldName + ".child", sourceFile, warnings);
        if (rawChild == null) rawChild = parseOptionalString(getField(section, map, "projectile"),
                fieldName + ".projectile", sourceFile, warnings);
        if (rawChild == null) {
            warnOptional(warnings, "projectile." + fieldName + " requires child projectile in " + sourceFile.getPath());
            return null;
        }
        ParsedId parsedChild = parseOptionalId(rawChild, pack.namespace, sourceFile, warnings);
        if (parsedChild == null) return null;

        Boolean useParentLifetime = parseOptionalBoolean(
                getField(section, map, "use_parent_lifetime"), fieldName + ".use_parent_lifetime", sourceFile, warnings
        );
        Integer minAmount = parseOptionalInteger(
                getField(section, map, "min_amount"), 0, Integer.MAX_VALUE, fieldName + ".min_amount", sourceFile, warnings
        );
        Integer maxAmount = parseOptionalInteger(
                getField(section, map, "max_amount"), 0, Integer.MAX_VALUE, fieldName + ".max_amount", sourceFile, warnings
        );
        Float angleToParent = parseOptionalFloat(
                getField(section, map, "angle_to_parent"), 0f, Float.MAX_VALUE, fieldName + ".angle_to_parent",
                sourceFile, warnings
        );

        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.SUB_PROJECTILES,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                parsedChild.internalName,
                useParentLifetime,
                minAmount,
                maxAmount,
                angleToParent,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseCommandEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        String command = parseOptionalString(getField(section, map, "command"), fieldName + ".command", sourceFile, warnings);
        if (command == null) {
            warnOptional(warnings, "projectile." + fieldName + ".command is required in " + sourceFile.getPath());
            return null;
        }
        String executorRaw = parseOptionalString(getField(section, map, "executor"), fieldName + ".executor", sourceFile, warnings);
        PEExecuteCommand.Executor executor = PEExecuteCommand.Executor.SHOOTER;
        if (executorRaw != null) {
            String normalized = executorRaw.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("console")) executor = PEExecuteCommand.Executor.CONSOLE;
            else if (normalized.equals("shooter")) executor = PEExecuteCommand.Executor.SHOOTER;
            else warnOptional(warnings, "Unknown executor '" + executorRaw + "' in " + sourceFile.getPath());
        }
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.COMMAND,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                command,
                executor,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parsePushPullEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Float strength = parseOptionalFloat(getField(section, map, "strength"), -Float.MAX_VALUE, Float.MAX_VALUE,
                fieldName + ".strength", sourceFile, warnings);
        Float radius = parseOptionalFloat(getField(section, map, "radius"), 0f, Float.MAX_VALUE,
                fieldName + ".radius", sourceFile, warnings);
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.PUSH_PULL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                strength,
                radius,
                null,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parsePlaySoundEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Object rawSound = getField(section, map, "sound");
        if (rawSound == null) rawSound = getField(section, map, "id");
        YamlSoundDefinition sound = parseSoundEntry(rawSound, fieldName + ".sound", sourceFile, warnings);
        if (sound == null) return null;
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.PLAY_SOUND,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sound,
                null,
                null,
                null
        );
    }

    private static YamlProjectileEffectDefinition parseFireworkEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Object rawEffects = getField(section, map, "effects");
        if (!(rawEffects instanceof List<?>)) {
            warnOptional(warnings, "projectile." + fieldName + ".effects must be a list in " + sourceFile.getPath());
            return null;
        }

        List<YamlFireworkEffectDefinition> effects = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawEffects) {
            YamlFireworkEffectDefinition parsed = parseFireworkEffectEntry(entry, fieldName + ".effects[" + index + "]", sourceFile, warnings);
            if (parsed != null) effects.add(parsed);
            index += 1;
        }
        if (effects.isEmpty()) return null;

        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.FIREWORKS,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                effects,
                null,
                null
        );
    }

    private static YamlFireworkEffectDefinition parseFireworkEffectEntry(
            Object rawEntry, String fieldName, File sourceFile, List<String> warnings
    ) {
        ConfigurationSection section = rawEntry instanceof ConfigurationSection ? (ConfigurationSection) rawEntry : null;
        Map<?, ?> map = rawEntry instanceof Map<?, ?> ? (Map<?, ?>) rawEntry : null;
        if (section == null && map == null) {
            warnOptional(warnings, "projectile." + fieldName + " must be a map in " + sourceFile.getPath());
            return null;
        }

        Object rawType = getField(section, map, "type");
        String typeName = rawType instanceof String ? ((String) rawType).trim() : null;
        if (typeName == null || typeName.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + ".type is required in " + sourceFile.getPath());
            return null;
        }
        PEShowFireworks.EffectType type;
        try {
            type = PEShowFireworks.EffectType.valueOf(typeName.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown firework effect type '" + typeName + "' in " + sourceFile.getPath());
            return null;
        }

        Boolean flicker = parseOptionalBoolean(getField(section, map, "flicker"), fieldName + ".flicker", sourceFile, warnings);
        Boolean trail = parseOptionalBoolean(getField(section, map, "trail"), fieldName + ".trail", sourceFile, warnings);

        List<Color> colors = parseColorList(getField(section, map, "colors"), fieldName + ".colors", sourceFile, warnings, true);
        List<Color> fadeColors = parseColorList(getField(section, map, "fade_colors"), fieldName + ".fade_colors", sourceFile, warnings, false);
        if (colors == null || colors.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + ".colors must contain at least 1 color in " + sourceFile.getPath());
            return null;
        }
        if (fadeColors == null) fadeColors = Collections.emptyList();

        return new YamlFireworkEffectDefinition(
                flicker != null && flicker,
                trail != null && trail,
                type,
                colors,
                fadeColors
        );
    }

    private static YamlProjectileEffectDefinition parsePotionAuraEffect(
            ConfigurationSection section, Map<?, ?> map, String fieldName, File sourceFile, List<String> warnings
    ) {
        Float radius = parseOptionalFloat(getField(section, map, "radius"), 0f, Float.MAX_VALUE,
                fieldName + ".radius", sourceFile, warnings);
        List<YamlPotionEffectDefinition> effects = parsePotionEffects(
                getField(section, map, "effects"), fieldName + ".effects", sourceFile, warnings
        );
        if (effects.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + ".effects must have at least 1 entry in " + sourceFile.getPath());
            return null;
        }
        return new YamlProjectileEffectDefinition(
                YamlProjectileEffectType.POTION_AURA,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                radius,
                effects
        );
    }

    private static Object getField(ConfigurationSection section, Map<?, ?> map, String key) {
        if (section != null) return section.get(key);
        return map.get(key);
    }

    private static YamlProjectileEffectType parseProjectileEffectType(
            Object rawType, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (!(rawType instanceof String)) {
            warnOptional(warnings, "projectile." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String trimmed = ((String) rawType).trim();
        if (trimmed.isEmpty()) {
            warnOptional(warnings, "projectile." + fieldName + " must not be empty in " + sourceFile.getPath());
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "explosion":
            case "create_explosion":
                return YamlProjectileEffectType.EXPLOSION;
            case "colored_redstone":
            case "redstone":
            case "colored_redstone_particle":
                return YamlProjectileEffectType.COLORED_REDSTONE;
            case "simple_particle":
            case "particle":
                return YamlProjectileEffectType.SIMPLE_PARTICLE;
            case "straight_acceleration":
            case "straight_accelerate":
            case "straight":
                return YamlProjectileEffectType.STRAIGHT_ACCELERATION;
            case "random_acceleration":
            case "random_accelerate":
            case "random":
                return YamlProjectileEffectType.RANDOM_ACCELERATION;
            case "sub_projectile":
            case "sub_projectiles":
                return YamlProjectileEffectType.SUB_PROJECTILES;
            case "command":
            case "execute_command":
                return YamlProjectileEffectType.COMMAND;
            case "push_pull":
            case "push_or_pull":
                return YamlProjectileEffectType.PUSH_PULL;
            case "play_sound":
            case "sound":
                return YamlProjectileEffectType.PLAY_SOUND;
            case "firework":
            case "fireworks":
                return YamlProjectileEffectType.FIREWORKS;
            case "potion_aura":
            case "aura":
                return YamlProjectileEffectType.POTION_AURA;
            default:
                warnOptional(warnings, "Unknown projectile effect type '" + trimmed + "' in " + sourceFile.getPath());
                return null;
        }
    }

    private static List<Color> parseColorList(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings, boolean required
    ) {
        if (rawValue == null) return required ? null : Collections.emptyList();
        if (!(rawValue instanceof List<?>)) {
            warnOptional(warnings, "projectile." + fieldName + " must be a list in " + sourceFile.getPath());
            return required ? null : Collections.emptyList();
        }
        List<Color> colors = new ArrayList<>();
        int index = 0;
        for (Object entry : (List<?>) rawValue) {
            Color color = parseColor(entry, fieldName + "[" + index + "]", sourceFile, warnings);
            if (color != null) colors.add(color);
            index += 1;
        }
        return colors;
    }

    private static Color parseColor(Object rawValue, String fieldName, File sourceFile, List<String> warnings) {
        if (rawValue == null) return null;

        if (rawValue instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) rawValue;
            Integer red = parseOptionalInteger(map.get("red"), 0, 255, fieldName + ".red", sourceFile, warnings);
            Integer green = parseOptionalInteger(map.get("green"), 0, 255, fieldName + ".green", sourceFile, warnings);
            Integer blue = parseOptionalInteger(map.get("blue"), 0, 255, fieldName + ".blue", sourceFile, warnings);
            if (red == null || green == null || blue == null) return null;
            return new Color(red, green, blue);
        }

        if (rawValue instanceof List<?>) {
            List<?> list = (List<?>) rawValue;
            if (list.size() < 3) {
                warnOptional(warnings, "projectile." + fieldName + " must have 3 entries in " + sourceFile.getPath());
                return null;
            }
            Integer red = parseOptionalInteger(list.get(0), 0, 255, fieldName + "[0]", sourceFile, warnings);
            Integer green = parseOptionalInteger(list.get(1), 0, 255, fieldName + "[1]", sourceFile, warnings);
            Integer blue = parseOptionalInteger(list.get(2), 0, 255, fieldName + "[2]", sourceFile, warnings);
            if (red == null || green == null || blue == null) return null;
            return new Color(red, green, blue);
        }

        if (rawValue instanceof Number) {
            int rgb = ((Number) rawValue).intValue();
            if (rgb < 0) {
                warnOptional(warnings, "projectile." + fieldName + " must be non-negative in " + sourceFile.getPath());
                return null;
            }
            if (rgb <= 0xFFFFFF) {
                return new Color(rgb);
            }
            return new Color(rgb, true);
        }

        if (rawValue instanceof String) {
            String raw = ((String) rawValue).trim();
            if (raw.isEmpty()) {
                warnOptional(warnings, "projectile." + fieldName + " must not be empty in " + sourceFile.getPath());
                return null;
            }
            String normalized = raw;
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            if (normalized.startsWith("0x") || normalized.startsWith("0X")) normalized = normalized.substring(2);
            if (normalized.contains(",")) {
                String[] parts = normalized.split(",");
                if (parts.length < 3) {
                    warnOptional(warnings, "projectile." + fieldName + " must have 3 components in " + sourceFile.getPath());
                    return null;
                }
                Integer red = parseOptionalInteger(parts[0].trim(), 0, 255, fieldName + ".red", sourceFile, warnings);
                Integer green = parseOptionalInteger(parts[1].trim(), 0, 255, fieldName + ".green", sourceFile, warnings);
                Integer blue = parseOptionalInteger(parts[2].trim(), 0, 255, fieldName + ".blue", sourceFile, warnings);
                if (red == null || green == null || blue == null) return null;
                return new Color(red, green, blue);
            }
            if (normalized.length() == 6 || normalized.length() == 8) {
                try {
                    int rgb = (int) Long.parseLong(normalized, 16);
                    if (normalized.length() == 6) {
                        return new Color(rgb);
                    }
                    return new Color(rgb, true);
                } catch (NumberFormatException ex) {
                    warnOptional(warnings, "projectile." + fieldName + " has invalid hex color in " + sourceFile.getPath());
                    return null;
                }
            }
        }

        warnOptional(warnings, "projectile." + fieldName + " has invalid color in " + sourceFile.getPath());
        return null;
    }

    private static VEffectType parseEffectType(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, "projectile." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid projectile." + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VEffectType effectType = VEffectType.valueOf(normalized);
            if (mcVersion < effectType.firstVersion || mcVersion > effectType.lastVersion) {
                warnOptional(warnings, "projectile." + fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return effectType;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown projectile." + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static VParticle parseParticle(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        if (!(rawValue instanceof String)) {
            warnOptional(warnings, "projectile." + fieldName + " must be a string in " + sourceFile.getPath());
            return null;
        }
        String normalized = normalizeEnumKey((String) rawValue);
        if (normalized == null) {
            warnOptional(warnings, "Invalid projectile." + fieldName + " in " + sourceFile.getPath());
            return null;
        }
        try {
            VParticle particle = VParticle.valueOf(normalized);
            if (mcVersion < particle.firstVersion || mcVersion > particle.lastVersion) {
                warnOptional(warnings, "projectile." + fieldName + " is not available in MC "
                        + MCVersions.createString(mcVersion) + " (" + sourceFile.getPath() + ")");
                return null;
            }
            return particle;
        } catch (IllegalArgumentException ex) {
            warnOptional(warnings, "Unknown projectile." + fieldName + " '" + rawValue + "' in " + sourceFile.getPath());
            return null;
        }
    }

    private static YamlSoundDefinition parseSoundEntry(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseSoundDefinition(rawValue, "projectile." + fieldName, sourceFile, warnings, true);
    }

    private static ParsedId parseId(
            String rawId, String defaultNamespace, File sourceFile, List<String> errors
    ) {
        return YamlParseUtils.parseId(rawId, defaultNamespace, sourceFile, errors, "projectile.id");
    }

    private static ParsedId parseOptionalId(
            String rawId, String defaultNamespace, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalId(rawId, defaultNamespace, sourceFile, warnings, "id");
    }

    private static ConfigurationSection getChildSection(
            ConfigurationSection parent, String name, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.getChildSection(parent, name, "projectile.", sourceFile, warnings);
    }

    private static boolean matchesRequires(
            ConfigurationSection requiresSection, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.matchesRequires(requiresSection, "projectile.requires", sourceFile, warnings);
    }

    private static Float parseOptionalFloat(
            Object rawValue, float min, float max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseFloatInRange(rawValue, min, max, "projectile." + fieldName, sourceFile, warnings);
    }

    private static Integer parseOptionalInteger(
            Object rawValue, int min, int max, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseInteger(rawValue, min, max, "projectile." + fieldName, sourceFile, warnings);
    }

    private static Boolean parseOptionalBoolean(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseBoolean(rawValue, "projectile." + fieldName, sourceFile, warnings);
    }

    private static String parseOptionalString(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalString(rawValue, "projectile." + fieldName, sourceFile, warnings);
    }

    private static FloatRange parseOptionalRange(
            Object rawValue, String fieldName, File sourceFile, List<String> warnings
    ) {
        return YamlParseUtils.parseOptionalRange(rawValue, "projectile." + fieldName, sourceFile, warnings);
    }
}
