package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlWandDefinition {

    final String projectileInternalName;
    final Integer cooldown;
    final Integer amountPerShot;
    final YamlWandChargesDefinition charges;
    final Float manaCost;
    final Boolean requiresPermission;
    final List<String> magicSpells;

    YamlWandDefinition(
            String projectileInternalName,
            Integer cooldown,
            Integer amountPerShot,
            YamlWandChargesDefinition charges,
            Float manaCost,
            Boolean requiresPermission,
            List<String> magicSpells
    ) {
        this.projectileInternalName = projectileInternalName;
        this.cooldown = cooldown;
        this.amountPerShot = amountPerShot;
        this.charges = charges;
        this.manaCost = manaCost;
        this.requiresPermission = requiresPermission;
        this.magicSpells = magicSpells;
    }
}
