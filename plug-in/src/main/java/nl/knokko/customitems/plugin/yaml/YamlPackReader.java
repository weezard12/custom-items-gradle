package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.Validation;
import nl.knokko.customitems.util.ValidationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

class YamlPackReader {

    static YamlPackDefinition readPack(File packDir, List<String> errors) {
        String namespace = packDir.getName();
        File packConfigFile = new File(packDir, "pack.yml");
        if (packConfigFile.isFile()) {
            YamlConfiguration packConfig = YamlConfiguration.loadConfiguration(packConfigFile);
            String configuredNamespace = packConfig.getString("namespace");
            if (configuredNamespace == null || configuredNamespace.trim().isEmpty()) {
                configuredNamespace = packConfig.getString("pack.namespace");
            }
            if (configuredNamespace != null && !configuredNamespace.trim().isEmpty()) {
                namespace = configuredNamespace.trim();
            }
        }

        try {
            Validation.safeName(namespace);
        } catch (ValidationException | ProgrammingValidationException ex) {
            errors.add("Invalid pack namespace '" + namespace + "' in " + packDir.getPath() + ": " + ex.getMessage());
            return null;
        }

        return new YamlPackDefinition(packDir, namespace);
    }
}
