package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;

class YamlRequiredVanillaItemDefinition {

    final VMaterial material;
    final boolean allowCustomItems;

    YamlRequiredVanillaItemDefinition(VMaterial material, boolean allowCustomItems) {
        this.material = material;
        this.allowCustomItems = allowCustomItems;
    }
}
