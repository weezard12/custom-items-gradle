package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlUpgradeResultDefinition {

    final Integer ingredientIndex;
    final String inputSlotName;
    final List<String> upgrades;
    final Float repairPercentage;
    final YamlRecipeResultDefinition newType;
    final Boolean keepOldUpgrades;
    final Boolean keepOldEnchantments;

    YamlUpgradeResultDefinition(
            Integer ingredientIndex,
            String inputSlotName,
            List<String> upgrades,
            Float repairPercentage,
            YamlRecipeResultDefinition newType,
            Boolean keepOldUpgrades,
            Boolean keepOldEnchantments
    ) {
        this.ingredientIndex = ingredientIndex;
        this.inputSlotName = inputSlotName;
        this.upgrades = upgrades;
        this.repairPercentage = repairPercentage;
        this.newType = newType;
        this.keepOldUpgrades = keepOldUpgrades;
        this.keepOldEnchantments = keepOldEnchantments;
    }
}
