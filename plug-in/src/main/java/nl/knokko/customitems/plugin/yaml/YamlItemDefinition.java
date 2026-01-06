package nl.knokko.customitems.plugin.yaml;

import java.io.File;

class YamlItemDefinition {

    final String fullId;
    final String internalName;
    final String displayName;
    final File sourceFile;

    YamlItemDefinition(String fullId, String internalName, String displayName, File sourceFile) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.displayName = displayName;
        this.sourceFile = sourceFile;
    }
}
