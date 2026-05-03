package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

public class YamlPowerAbilityDefinition {

    public final String fullId;
    public final String name;
    public final YamlPowerAbilityType type;
    public final String potionEffect;
    public final int amplifier;
    public final int durationTicks;
    public final boolean ambient;
    public final boolean particles;
    public final boolean icon;
    public final long tickIntervalTicks;
    public final List<YamlPowerConditionDefinition> conditions;
    public final boolean conditionStateEnabledByDefault;
    public final File sourceFile;

    YamlPowerAbilityDefinition(
            String fullId,
            String name,
            YamlPowerAbilityType type,
            String potionEffect,
            int amplifier,
            int durationTicks,
            boolean ambient,
            boolean particles,
            boolean icon,
            long tickIntervalTicks,
            List<YamlPowerConditionDefinition> conditions,
            boolean conditionStateEnabledByDefault,
            File sourceFile
    ) {
        this.fullId = fullId;
        this.name = name;
        this.type = type;
        this.potionEffect = potionEffect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
        this.tickIntervalTicks = tickIntervalTicks;
        this.conditions = conditions;
        this.conditionStateEnabledByDefault = conditionStateEnabledByDefault;
        this.sourceFile = sourceFile;
    }
}
