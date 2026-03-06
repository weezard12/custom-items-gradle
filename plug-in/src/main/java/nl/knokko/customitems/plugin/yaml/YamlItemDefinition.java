package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

class YamlItemDefinition {

    final String fullId;
    final String internalName;
    final String idName;
    final File packDirectory;
    final String displayName;
    final File sourceFile;
    final List<String> lore;
    final YamlItemType type;
    final YamlToolDefinition toolDefinition;
    final YamlArmorDefinition armorDefinition;
    final YamlWandDefinition wandDefinition;
    final YamlFoodDefinition foodDefinition;
    final YamlItemCustomModelDefinition customModelDefinition;
    final String blockInternalName;
    final YamlMaterialDefinition material;
    final List<YamlEnchantmentDefinition> enchantments;
    final Integer stackSize;
    final Integer damageValue;
    final Boolean unbreakable;
    final Double attackDamageFinal;
    final Double attackSpeedFinal;
    final Double attackDamageModifier;
    final Double attackSpeedModifier;

    YamlItemDefinition(
            String fullId,
            String internalName,
            String idName,
            File packDirectory,
            String displayName,
            File sourceFile,
            List<String> lore,
            YamlItemType type,
            YamlToolDefinition toolDefinition,
            YamlArmorDefinition armorDefinition,
            YamlWandDefinition wandDefinition,
            YamlFoodDefinition foodDefinition,
            YamlItemCustomModelDefinition customModelDefinition,
            String blockInternalName,
            YamlMaterialDefinition material,
            List<YamlEnchantmentDefinition> enchantments,
            Integer stackSize,
            Integer damageValue,
            Boolean unbreakable,
            Double attackDamageFinal,
            Double attackSpeedFinal,
            Double attackDamageModifier,
            Double attackSpeedModifier
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.idName = idName;
        this.packDirectory = packDirectory;
        this.displayName = displayName;
        this.sourceFile = sourceFile;
        this.lore = lore;
        this.type = type;
        this.toolDefinition = toolDefinition;
        this.armorDefinition = armorDefinition;
        this.wandDefinition = wandDefinition;
        this.foodDefinition = foodDefinition;
        this.customModelDefinition = customModelDefinition;
        this.blockInternalName = blockInternalName;
        this.material = material;
        this.enchantments = enchantments;
        this.stackSize = stackSize;
        this.damageValue = damageValue;
        this.unbreakable = unbreakable;
        this.attackDamageFinal = attackDamageFinal;
        this.attackSpeedFinal = attackSpeedFinal;
        this.attackDamageModifier = attackDamageModifier;
        this.attackSpeedModifier = attackSpeedModifier;
    }
}
