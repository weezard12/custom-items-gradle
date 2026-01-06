package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

class YamlItemDefinition {

    final String fullId;
    final String internalName;
    final String displayName;
    final File sourceFile;
    final List<String> lore;
    final YamlMaterialDefinition material;
    final List<YamlEnchantmentDefinition> enchantments;
    final Integer stackSize;
    final Integer damageValue;
    final Boolean unbreakable;
    final Double attackDamage;
    final Double attackSpeed;

    YamlItemDefinition(
            String fullId,
            String internalName,
            String displayName,
            File sourceFile,
            List<String> lore,
            YamlMaterialDefinition material,
            List<YamlEnchantmentDefinition> enchantments,
            Integer stackSize,
            Integer damageValue,
            Boolean unbreakable,
            Double attackDamage,
            Double attackSpeed
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.displayName = displayName;
        this.sourceFile = sourceFile;
        this.lore = lore;
        this.material = material;
        this.enchantments = enchantments;
        this.stackSize = stackSize;
        this.damageValue = damageValue;
        this.unbreakable = unbreakable;
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
    }
}
