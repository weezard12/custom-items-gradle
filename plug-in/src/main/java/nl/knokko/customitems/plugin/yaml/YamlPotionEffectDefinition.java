package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.effect.VEffectType;

class YamlPotionEffectDefinition {

    final VEffectType type;
    final int duration;
    final int level;

    YamlPotionEffectDefinition(VEffectType type, int duration, int level) {
        this.type = type;
        this.duration = duration;
        this.level = level;
    }
}
