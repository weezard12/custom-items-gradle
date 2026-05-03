package nl.knokko.customitems.plugin.yaml;

import java.util.Map;

class YamlItemCustomModelDefinition {

    final String modelPath;
    final Map<String, String> texturePaths;

    YamlItemCustomModelDefinition(String modelPath, Map<String, String> texturePaths) {
        this.modelPath = modelPath;
        this.texturePaths = texturePaths;
    }
}
