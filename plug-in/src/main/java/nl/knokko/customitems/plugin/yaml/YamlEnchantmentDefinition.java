package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.enchantment.VEnchantmentType;

class YamlEnchantmentDefinition {

    final VEnchantmentType type;
    final int level;

    YamlEnchantmentDefinition(VEnchantmentType type, int level) {
        this.type = type;
        this.level = level;
    }
}
