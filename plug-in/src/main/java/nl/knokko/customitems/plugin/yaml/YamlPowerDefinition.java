package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

public class YamlPowerDefinition {

    public final String fullId;
    public final String name;
    public final List<String> abilityIds;
    public final String rarity;
    public final String alignment;
    public final String iconKey;
    public final List<String> description;
    public final File sourceFile;

    YamlPowerDefinition(
            String fullId,
            String name,
            List<String> abilityIds,
            String rarity,
            String alignment,
            String iconKey,
            List<String> description,
            File sourceFile
    ) {
        this.fullId = fullId;
        this.name = name;
        this.abilityIds = abilityIds;
        this.rarity = rarity;
        this.alignment = alignment;
        this.iconKey = iconKey;
        this.description = description;
        this.sourceFile = sourceFile;
    }
}
