package nl.knokko.customitems.plugin.yaml;

class YamlArmorDefinition {

    final Integer maxDurability;
    final Integer entityHitDurabilityLoss;
    final Integer blockBreakDurabilityLoss;
    final Double armorValue;
    final Double armorToughness;

    YamlArmorDefinition(
            Integer maxDurability,
            Integer entityHitDurabilityLoss,
            Integer blockBreakDurabilityLoss,
            Double armorValue,
            Double armorToughness
    ) {
        this.maxDurability = maxDurability;
        this.entityHitDurabilityLoss = entityHitDurabilityLoss;
        this.blockBreakDurabilityLoss = blockBreakDurabilityLoss;
        this.armorValue = armorValue;
        this.armorToughness = armorToughness;
    }
}
