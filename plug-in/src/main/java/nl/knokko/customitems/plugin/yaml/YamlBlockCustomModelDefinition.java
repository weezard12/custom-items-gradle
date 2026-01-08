package nl.knokko.customitems.plugin.yaml;

import java.util.Map;

class YamlBlockCustomModelDefinition {

    final String modelPath;
    final String editorTexturePath;
    final Map<String, String> texturePaths;

    YamlBlockCustomModelDefinition(
            String modelPath,
            String editorTexturePath,
            Map<String, String> texturePaths
    ) {
        this.modelPath = modelPath;
        this.editorTexturePath = editorTexturePath;
        this.texturePaths = texturePaths;
    }
}
