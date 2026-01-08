package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.drops.VBiome;

import java.util.List;

class YamlAllowedBiomesDefinition {

    final List<VBiome> whitelist;
    final List<VBiome> blacklist;

    YamlAllowedBiomesDefinition(List<VBiome> whitelist, List<VBiome> blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }
}
