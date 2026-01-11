package nl.knokko.customitems.plugin.yaml;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

class YamlDocumentReader {

    static List<YamlConfiguration> loadDocuments(File file, List<String> errors) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            errors.add("Failed to read " + file.getPath() + ": " + ex.getMessage());
            return new ArrayList<>();
        }

        List<String> documents = splitDocuments(lines);
        List<YamlConfiguration> configs = new ArrayList<>(documents.size());
        for (String doc : documents) {
            String trimmed = doc.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
                trimmed = trimmed.substring(1).trim();
                if (trimmed.isEmpty()) continue;
            }
            configs.add(YamlConfiguration.loadConfiguration(new StringReader(trimmed)));
        }
        return configs;
    }

    private static List<String> splitDocuments(List<String> lines) {
        List<String> docs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals("---")) {
                docs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        docs.add(current.toString());
        return docs;
    }
}
