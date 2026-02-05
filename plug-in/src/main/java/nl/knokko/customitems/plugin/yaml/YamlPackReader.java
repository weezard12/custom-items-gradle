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
            String configuredNamespace = null;
            List<YamlConfiguration> configs = YamlDocumentReader.loadDocuments(packConfigFile, errors);
            for (YamlConfiguration config : configs) {
                if (config == null) continue;
                String candidate = config.getString("namespace");
                if (candidate == null || candidate.trim().isEmpty()) {
                    candidate = config.getString("pack.namespace");
                }
                if (candidate == null || candidate.trim().isEmpty()) continue;
                candidate = candidate.trim();
                if (configuredNamespace == null) {
                    configuredNamespace = candidate;
                } else if (!configuredNamespace.equals(candidate)) {
                    errors.add("Conflicting pack namespace values in " + packConfigFile.getPath()
                            + ": '" + configuredNamespace + "' and '" + candidate + "'");
                }
            }
            if (configuredNamespace != null && !configuredNamespace.isEmpty()) {
                namespace = configuredNamespace;
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
