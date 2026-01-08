package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlBlockMiningSpeedDefinition {

    final Integer defaultValue;
    final List<YamlBlockVanillaMiningSpeedEntry> vanillaEntries;
    final List<YamlBlockCustomMiningSpeedEntry> customEntries;

    YamlBlockMiningSpeedDefinition(
            Integer defaultValue,
            List<YamlBlockVanillaMiningSpeedEntry> vanillaEntries,
            List<YamlBlockCustomMiningSpeedEntry> customEntries
    ) {
        this.defaultValue = defaultValue;
        this.vanillaEntries = vanillaEntries;
        this.customEntries = customEntries;
    }
}
