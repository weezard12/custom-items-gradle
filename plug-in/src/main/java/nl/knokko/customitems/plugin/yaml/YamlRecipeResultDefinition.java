package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;

class YamlRecipeResultDefinition {

    final YamlRecipeResultType type;
    final VMaterial material;
    final Integer dataValue;
    final String customItemInternalName;
    final String foreignItemId;
    final String encoded;
    final int amount;
    final YamlUpgradeResultDefinition upgrade;

    YamlRecipeResultDefinition(
            YamlRecipeResultType type,
            VMaterial material,
            Integer dataValue,
            String customItemInternalName,
            String foreignItemId,
            String encoded,
            int amount,
            YamlUpgradeResultDefinition upgrade
    ) {
        this.type = type;
        this.material = material;
        this.dataValue = dataValue;
        this.customItemInternalName = customItemInternalName;
        this.foreignItemId = foreignItemId;
        this.encoded = encoded;
        this.amount = amount;
        this.upgrade = upgrade;
    }
}
