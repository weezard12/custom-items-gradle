package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.projectile.effect.PEShowFireworks;

import java.awt.Color;
import java.util.List;

class YamlFireworkEffectDefinition {

    final boolean flicker;
    final boolean trail;
    final PEShowFireworks.EffectType type;
    final List<Color> colors;
    final List<Color> fadeColors;

    YamlFireworkEffectDefinition(
            boolean flicker,
            boolean trail,
            PEShowFireworks.EffectType type,
            List<Color> colors,
            List<Color> fadeColors
    ) {
        this.flicker = flicker;
        this.trail = trail;
        this.type = type;
        this.colors = colors;
        this.fadeColors = fadeColors;
    }
}
