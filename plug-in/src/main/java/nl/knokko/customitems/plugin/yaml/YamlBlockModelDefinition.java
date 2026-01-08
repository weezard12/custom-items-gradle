package nl.knokko.customitems.plugin.yaml;

import java.util.Map;

class YamlBlockModelDefinition {

    final YamlBlockModelType type;
    final String simpleTexture;
    final Map<String, String> sidedTextures;
    final YamlBlockCustomModelDefinition customModel;

    YamlBlockModelDefinition(
            YamlBlockModelType type,
            String simpleTexture,
            Map<String, String> sidedTextures,
            YamlBlockCustomModelDefinition customModel
    ) {
        this.type = type;
        this.simpleTexture = simpleTexture;
        this.sidedTextures = sidedTextures;
        this.customModel = customModel;
    }
}
