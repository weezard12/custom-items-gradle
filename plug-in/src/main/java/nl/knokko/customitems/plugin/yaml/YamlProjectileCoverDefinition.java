package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.KciItemType;

import java.io.File;
import java.util.Map;

class YamlProjectileCoverDefinition {

    final String fullId;
    final String internalName;
    final String idName;
    final File packDirectory;
    final File sourceFile;
    final YamlProjectileCoverType type;
    final KciItemType itemType;
    final String texturePath;
    final Integer slotsPerAxis;
    final Double scale;
    final String modelPath;
    final Map<String, String> modelTextures;
    final String geyserTexturePath;

    YamlProjectileCoverDefinition(
            String fullId,
            String internalName,
            String idName,
            File packDirectory,
            File sourceFile,
            YamlProjectileCoverType type,
            KciItemType itemType,
            String texturePath,
            Integer slotsPerAxis,
            Double scale,
            String modelPath,
            Map<String, String> modelTextures,
            String geyserTexturePath
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.idName = idName;
        this.packDirectory = packDirectory;
        this.sourceFile = sourceFile;
        this.type = type;
        this.itemType = itemType;
        this.texturePath = texturePath;
        this.slotsPerAxis = slotsPerAxis;
        this.scale = scale;
        this.modelPath = modelPath;
        this.modelTextures = modelTextures;
        this.geyserTexturePath = geyserTexturePath;
    }
}
