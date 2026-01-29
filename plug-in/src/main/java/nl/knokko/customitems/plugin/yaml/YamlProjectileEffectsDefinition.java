package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlProjectileEffectsDefinition {

    final Integer delay;
    final Integer period;
    final List<YamlProjectileEffectDefinition> effects;

    YamlProjectileEffectsDefinition(Integer delay, Integer period, List<YamlProjectileEffectDefinition> effects) {
        this.delay = delay;
        this.period = period;
        this.effects = effects;
    }
}
