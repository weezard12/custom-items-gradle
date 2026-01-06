package nl.knokko.customitems.plugin.yaml;

class YamlToolDefinition {

    final Integer maxDurability;
    final Integer entityHitDurabilityLoss;
    final Integer blockBreakDurabilityLoss;

    YamlToolDefinition(Integer maxDurability, Integer entityHitDurabilityLoss, Integer blockBreakDurabilityLoss) {
        this.maxDurability = maxDurability;
        this.entityHitDurabilityLoss = entityHitDurabilityLoss;
        this.blockBreakDurabilityLoss = blockBreakDurabilityLoss;
    }
}
