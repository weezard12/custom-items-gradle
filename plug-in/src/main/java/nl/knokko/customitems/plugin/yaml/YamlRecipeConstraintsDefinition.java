package nl.knokko.customitems.plugin.yaml;

import java.util.List;

class YamlRecipeConstraintsDefinition {

    final List<YamlDurabilityConstraintDefinition> durabilityConstraints;
    final List<YamlEnchantmentConstraintDefinition> enchantmentConstraints;
    final List<YamlVariableConstraintDefinition> variableConstraints;

    YamlRecipeConstraintsDefinition(
            List<YamlDurabilityConstraintDefinition> durabilityConstraints,
            List<YamlEnchantmentConstraintDefinition> enchantmentConstraints,
            List<YamlVariableConstraintDefinition> variableConstraints
    ) {
        this.durabilityConstraints = durabilityConstraints;
        this.enchantmentConstraints = enchantmentConstraints;
        this.variableConstraints = variableConstraints;
    }
}
