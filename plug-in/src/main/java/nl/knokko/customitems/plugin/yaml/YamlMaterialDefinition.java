package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.VMaterial;

class YamlMaterialDefinition {

    final KciItemType itemType;
    final VMaterial otherMaterial;

    YamlMaterialDefinition(KciItemType itemType, VMaterial otherMaterial) {
        this.itemType = itemType;
        this.otherMaterial = otherMaterial;
    }
}
