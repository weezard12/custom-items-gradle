package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.item.enchantment.VEnchantmentType;
import nl.knokko.customitems.recipe.ingredient.constraint.ConstraintOperator;

class YamlEnchantmentConstraintDefinition {

    final VEnchantmentType enchantment;
    final ConstraintOperator operator;
    final int level;

    YamlEnchantmentConstraintDefinition(
            VEnchantmentType enchantment,
            ConstraintOperator operator,
            int level
    ) {
        this.enchantment = enchantment;
        this.operator = operator;
        this.level = level;
    }
}
