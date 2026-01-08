package nl.knokko.customitems.plugin.yaml;

import java.io.File;

class YamlBlockDefinition {

    final String fullId;
    final String internalName;
    final String idName;
    final File packDirectory;
    final File sourceFile;
    final YamlBlockModelDefinition modelDefinition;
    final YamlBlockMiningSpeedDefinition miningSpeed;
    final YamlBlockSoundsDefinition sounds;
    final java.util.List<YamlBlockDropDefinition> drops;

    YamlBlockDefinition(
            String fullId,
            String internalName,
            String idName,
            File packDirectory,
            File sourceFile,
            YamlBlockModelDefinition modelDefinition,
            YamlBlockMiningSpeedDefinition miningSpeed,
            YamlBlockSoundsDefinition sounds,
            java.util.List<YamlBlockDropDefinition> drops
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.idName = idName;
        this.packDirectory = packDirectory;
        this.sourceFile = sourceFile;
        this.modelDefinition = modelDefinition;
        this.miningSpeed = miningSpeed;
        this.sounds = sounds;
        this.drops = drops;
    }
}
