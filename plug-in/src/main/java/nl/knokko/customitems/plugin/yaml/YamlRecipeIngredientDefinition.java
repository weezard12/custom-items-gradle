package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;

class YamlRecipeIngredientDefinition {

    final YamlRecipeIngredientType type;
    final VMaterial material;
    final Integer dataValue;
    final String customItemInternalName;
    final String foreignItemId;
    final String encoded;
    final int amount;
    final YamlRecipeResultDefinition remainingItem;
    final YamlRecipeConstraintsDefinition constraints;

    YamlRecipeIngredientDefinition(
            YamlRecipeIngredientType type,
            VMaterial material,
            Integer dataValue,
            String customItemInternalName,
            String foreignItemId,
            String encoded,
            int amount,
            YamlRecipeResultDefinition remainingItem,
            YamlRecipeConstraintsDefinition constraints
    ) {
        this.type = type;
        this.material = material;
        this.dataValue = dataValue;
        this.customItemInternalName = customItemInternalName;
        this.foreignItemId = foreignItemId;
        this.encoded = encoded;
        this.amount = amount;
        this.remainingItem = remainingItem;
        this.constraints = constraints;
    }
}
