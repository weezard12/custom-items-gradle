package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;

class YamlBlockDropOutputDefinition {

    final VMaterial material;
    final String customItemInternalName;
    final int amount;
    final double chance;

    YamlBlockDropOutputDefinition(VMaterial material, String customItemInternalName, int amount, double chance) {
        this.material = material;
        this.customItemInternalName = customItemInternalName;
        this.amount = amount;
        this.chance = chance;
    }
}
