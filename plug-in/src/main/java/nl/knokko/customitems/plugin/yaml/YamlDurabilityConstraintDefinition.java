package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.recipe.ingredient.constraint.ConstraintOperator;

class YamlDurabilityConstraintDefinition {

    final ConstraintOperator operator;
    final float percentage;

    YamlDurabilityConstraintDefinition(ConstraintOperator operator, float percentage) {
        this.operator = operator;
        this.percentage = percentage;
    }
}
