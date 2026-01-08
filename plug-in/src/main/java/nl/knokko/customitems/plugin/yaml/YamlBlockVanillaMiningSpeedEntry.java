package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.VMaterial;

class YamlBlockVanillaMiningSpeedEntry {

    final VMaterial material;
    final int value;
    final boolean allowCustomItems;

    YamlBlockVanillaMiningSpeedEntry(VMaterial material, int value, boolean allowCustomItems) {
        this.material = material;
        this.value = value;
        this.allowCustomItems = allowCustomItems;
    }
}
