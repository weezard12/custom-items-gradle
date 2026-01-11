package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.recipe.ingredient.constraint.ConstraintOperator;

class YamlVariableConstraintDefinition {

    final String variable;
    final ConstraintOperator operator;
    final int value;

    YamlVariableConstraintDefinition(String variable, ConstraintOperator operator, int value) {
        this.variable = variable;
        this.operator = operator;
        this.value = value;
    }
}
