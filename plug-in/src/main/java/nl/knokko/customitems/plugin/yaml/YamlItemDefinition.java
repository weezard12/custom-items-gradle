package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

class YamlItemDefinition {

    final String fullId;
    final String internalName;
    final String displayName;
    final File sourceFile;
    final List<String> lore;
    final YamlItemType type;
    final YamlToolDefinition toolDefinition;
    final YamlArmorDefinition armorDefinition;
    final YamlFoodDefinition foodDefinition;
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
            YamlItemType type,
            YamlToolDefinition toolDefinition,
            YamlArmorDefinition armorDefinition,
            YamlFoodDefinition foodDefinition,
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
        this.type = type;
        this.toolDefinition = toolDefinition;
        this.armorDefinition = armorDefinition;
        this.foodDefinition = foodDefinition;
        this.material = material;
        this.enchantments = enchantments;
        this.stackSize = stackSize;
        this.damageValue = damageValue;
        this.unbreakable = unbreakable;
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
    }
}
