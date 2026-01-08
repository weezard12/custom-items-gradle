package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlRequiredItemsDefinition {

    final boolean enabled;
    final boolean invert;
    final List<YamlRequiredVanillaItemDefinition> vanillaItems;
    final List<String> customItems;

    YamlRequiredItemsDefinition(
            boolean enabled,
            boolean invert,
            List<YamlRequiredVanillaItemDefinition> vanillaItems,
            List<String> customItems
    ) {
        this.enabled = enabled;
        this.invert = invert;
        this.vanillaItems = vanillaItems;
        this.customItems = customItems;
    }
}
