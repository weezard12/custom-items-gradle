package nl.knokko.customitems.plugin.yaml;

import java.io.File;
import java.util.List;

class YamlRecipeDefinition {

    final String fullId;
    final String internalName;
    final String idName;
    final File packDirectory;
    final File sourceFile;
    final YamlRecipeType type;
    final boolean ignoreDisplacement;
    final String requiredPermission;
    final YamlRecipeResultDefinition result;
    final YamlRecipeIngredientDefinition[] shapedIngredients;
    final List<YamlRecipeIngredientDefinition> shapelessIngredients;

    YamlRecipeDefinition(
            String fullId,
            String internalName,
            String idName,
            File packDirectory,
            File sourceFile,
            YamlRecipeType type,
            boolean ignoreDisplacement,
            String requiredPermission,
            YamlRecipeResultDefinition result,
            YamlRecipeIngredientDefinition[] shapedIngredients,
            List<YamlRecipeIngredientDefinition> shapelessIngredients
    ) {
        this.fullId = fullId;
        this.internalName = internalName;
        this.idName = idName;
        this.packDirectory = packDirectory;
        this.sourceFile = sourceFile;
        this.type = type;
        this.ignoreDisplacement = ignoreDisplacement;
        this.requiredPermission = requiredPermission;
        this.result = result;
        this.shapedIngredients = shapedIngredients;
        this.shapelessIngredients = shapelessIngredients;
    }
}
