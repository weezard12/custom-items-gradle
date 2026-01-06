package nl.knokko.customitems.plugin.yaml;

import java.io.File;

class YamlPackDefinition {

    final File directory;
    final String namespace;

    YamlPackDefinition(File directory, String namespace) {
        this.directory = directory;
        this.namespace = namespace;
    }
}
