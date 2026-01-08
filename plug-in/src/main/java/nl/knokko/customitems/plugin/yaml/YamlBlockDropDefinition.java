package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.block.drop.SilkTouchRequirement;

import java.util.List;

class YamlBlockDropDefinition {

    final List<YamlBlockDropOutputDefinition> outputs;
    final SilkTouchRequirement silkTouchRequirement;
    final Integer minFortuneLevel;
    final Integer maxFortuneLevel;
    final Boolean cancelNormalDrops;
    final YamlRequiredItemsDefinition requiredItems;
    final YamlAllowedBiomesDefinition allowedBiomes;

    YamlBlockDropDefinition(
            List<YamlBlockDropOutputDefinition> outputs,
            SilkTouchRequirement silkTouchRequirement,
            Integer minFortuneLevel,
            Integer maxFortuneLevel,
            Boolean cancelNormalDrops,
            YamlRequiredItemsDefinition requiredItems,
            YamlAllowedBiomesDefinition allowedBiomes
    ) {
        this.outputs = outputs;
        this.silkTouchRequirement = silkTouchRequirement;
        this.minFortuneLevel = minFortuneLevel;
        this.maxFortuneLevel = maxFortuneLevel;
        this.cancelNormalDrops = cancelNormalDrops;
        this.requiredItems = requiredItems;
        this.allowedBiomes = allowedBiomes;
    }
}
