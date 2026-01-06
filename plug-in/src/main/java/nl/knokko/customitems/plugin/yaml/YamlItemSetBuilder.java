package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.item.KciSimpleItem;
import nl.knokko.customitems.item.KciAttributeModifier;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.item.enchantment.LeveledEnchantment;
import nl.knokko.customitems.settings.ExportSettings;
import nl.knokko.customitems.texture.KciTexture;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

class YamlItemSetBuilder {

    private static final String PLACEHOLDER_TEXTURE_NAME = "yaml_placeholder";

    static ItemSet build(Collection<YamlItemDefinition> items)
            throws ValidationException, ProgrammingValidationException {
        ItemSet itemSet = new ItemSet(ItemSet.Side.EDITOR);

        ExportSettings settings = new ExportSettings(true);
        settings.setMcVersion(mcVersion);
        settings.setMode(ExportSettings.Mode.MANUAL);
        settings.setSkipResourcepack(true);
        itemSet.setExportSettings(settings);

        KciTexture placeholderTexture = new KciTexture(true);
        placeholderTexture.setName(PLACEHOLDER_TEXTURE_NAME);
        placeholderTexture.setImage(createPlaceholderImage());
        itemSet.textures.add(placeholderTexture);

        for (YamlItemDefinition itemDefinition : items) {
            KciSimpleItem item = new KciSimpleItem(true);
            item.setName(itemDefinition.internalName);
            item.setAlias(itemDefinition.fullId);
            item.setDisplayName(itemDefinition.displayName);
            if (!itemDefinition.lore.isEmpty()) {
                item.setLore(itemDefinition.lore);
            }
            item.setTexture(itemSet.textures.getReference(PLACEHOLDER_TEXTURE_NAME));
            if (itemDefinition.material != null) {
                item.setItemType(itemDefinition.material.itemType);
                if (itemDefinition.material.otherMaterial != null) {
                    item.setOtherMaterial(itemDefinition.material.otherMaterial);
                }
            }
            if (itemDefinition.stackSize != null) {
                item.setMaxStacksize(itemDefinition.stackSize.byteValue());
            }
            if (itemDefinition.damageValue != null) {
                item.setItemDamage(itemDefinition.damageValue.shortValue());
            }
            if (itemDefinition.unbreakable != null) {
                item.setIndestructible(itemDefinition.unbreakable);
            }
            if (!itemDefinition.enchantments.isEmpty()) {
                Collection<LeveledEnchantment> leveledEnchantments = new ArrayList<>(itemDefinition.enchantments.size());
                for (YamlEnchantmentDefinition enchantment : itemDefinition.enchantments) {
                    leveledEnchantments.add(
                            LeveledEnchantment.createQuick(enchantment.type, enchantment.level)
                    );
                }
                item.setDefaultEnchantments(leveledEnchantments);
            }
            if (itemDefinition.attackDamage != null || itemDefinition.attackSpeed != null) {
                Collection<KciAttributeModifier> attributes = new ArrayList<>(2);
                if (itemDefinition.attackDamage != null) {
                    attributes.add(KciAttributeModifier.createQuick(
                            KciAttributeModifier.Attribute.ATTACK_DAMAGE,
                            KciAttributeModifier.Slot.MAINHAND,
                            KciAttributeModifier.Operation.ADD,
                            itemDefinition.attackDamage
                    ));
                }
                if (itemDefinition.attackSpeed != null) {
                    attributes.add(KciAttributeModifier.createQuick(
                            KciAttributeModifier.Attribute.ATTACK_SPEED,
                            KciAttributeModifier.Slot.MAINHAND,
                            KciAttributeModifier.Operation.ADD,
                            itemDefinition.attackSpeed
                    ));
                }
                item.setAttributeModifiers(attributes);
            }
            itemSet.items.add(item);
        }

        itemSet.validateExportVersion(mcVersion);
        return itemSet;
    }

    static ByteArrayBitOutput buildBinary(ItemSet itemSet) {
        ByteArrayBitOutput output = new ByteArrayBitOutput();
        itemSet.save(output, ItemSet.Side.PLUGIN);
        output.terminate();
        return output;
    }

    private static BufferedImage createPlaceholderImage() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
}
