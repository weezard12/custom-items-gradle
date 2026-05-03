package nl.knokko.customitems.plugin.yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class YamlPowersDefinition {

    private final Map<String, YamlPowerAbilityDefinition> abilitiesById;
    private final Map<String, YamlPowerDefinition> powersById;
    private final Map<String, Set<String>> itemPowersByInternalName;

    YamlPowersDefinition(
            Map<String, YamlPowerAbilityDefinition> abilitiesById,
            Map<String, YamlPowerDefinition> powersById,
            Map<String, Set<String>> itemPowersByInternalName
    ) {
        this.abilitiesById = Collections.unmodifiableMap(new LinkedHashMap<>(abilitiesById));
        this.powersById = Collections.unmodifiableMap(new LinkedHashMap<>(powersById));

        Map<String, Set<String>> itemPowers = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : itemPowersByInternalName.entrySet()) {
            itemPowers.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        this.itemPowersByInternalName = Collections.unmodifiableMap(itemPowers);
    }

    public static YamlPowersDefinition empty() {
        return new YamlPowersDefinition(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    public Map<String, YamlPowerAbilityDefinition> getAbilitiesById() {
        return abilitiesById;
    }

    public Map<String, YamlPowerDefinition> getPowersById() {
        return powersById;
    }

    public Map<String, Set<String>> getItemPowersByInternalName() {
        return itemPowersByInternalName;
    }

    public boolean isEmpty() {
        return abilitiesById.isEmpty() && powersById.isEmpty() && itemPowersByInternalName.isEmpty();
    }
}
