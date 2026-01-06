package nl.knokko.customitems.plugin.yaml;

import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.item.KciAttributeModifier;
import nl.knokko.customitems.item.KciArmor;
import nl.knokko.customitems.item.KciFood;
import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.KciSimpleItem;
import nl.knokko.customitems.item.KciTool;
import nl.knokko.customitems.item.ToolDurabilityLoss;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.item.enchantment.LeveledEnchantment;
import nl.knokko.customitems.settings.ExportSettings;
import nl.knokko.customitems.texture.KciTexture;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
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
        settings.setMode(ExportSettings.Mode.AUTOMATIC);
        settings.setSkipResourcepack(false);
        itemSet.setExportSettings(settings);

        KciTexture placeholderTexture = new KciTexture(true);
        placeholderTexture.setName(PLACEHOLDER_TEXTURE_NAME);
        placeholderTexture.setImage(createPlaceholderImage());
        itemSet.textures.add(placeholderTexture);

        for (YamlItemDefinition itemDefinition : items) {
            KciItem item = createItem(itemDefinition);
            applyBaseProperties(itemDefinition, item);
            applyTexture(itemDefinition, item, itemSet);
            applyMaterial(itemDefinition, item);
            applyStackSize(itemDefinition, item);
            applyEnchantments(itemDefinition, item);
            applyAttributes(itemDefinition, item);
            if (item instanceof KciTool) {
                applyToolDefinition(itemDefinition, (KciTool) item);
            }
            if (item instanceof KciFood) {
                applyFoodDefinition(itemDefinition, (KciFood) item);
            }
            itemSet.items.add(item);
        }

        itemSet.validateExportVersion(mcVersion);
        return itemSet;
    }

    private static KciItem createItem(YamlItemDefinition itemDefinition) {
        if (itemDefinition.type == YamlItemType.TOOL) {
            return new KciTool(true, KciItemType.IRON_SWORD);
        }
        if (itemDefinition.type == YamlItemType.ARMOR) {
            return new KciArmor(true, KciItemType.IRON_HELMET);
        }
        if (itemDefinition.type == YamlItemType.FOOD) {
            return new KciFood(true);
        }
        return new KciSimpleItem(true);
    }

    private static void applyBaseProperties(YamlItemDefinition itemDefinition, KciItem item) {
        item.setName(itemDefinition.internalName);
        item.setAlias(itemDefinition.fullId);
        item.setDisplayName(itemDefinition.displayName);
        if (!itemDefinition.lore.isEmpty()) {
            item.setLore(itemDefinition.lore);
        }
        if (itemDefinition.damageValue != null) {
            item.setItemDamage(itemDefinition.damageValue.shortValue());
            if (itemDefinition.damageValue > 0) {
                item.setUpdateAutomatically(false);
            }
        }
    }

    private static void applyTexture(
            YamlItemDefinition itemDefinition, KciItem item, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        File textureFile = resolveTextureFile(itemDefinition);
        if (textureFile == null) {
            item.setTexture(itemSet.textures.getReference(PLACEHOLDER_TEXTURE_NAME));
            return;
        }

        String textureName = itemDefinition.internalName;
        BufferedImage image;
        try {
            image = ImageIO.read(textureFile);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read texture " + textureFile.getPath() + ": " + ex.getMessage());
        }
        if (image == null) {
            throw new ValidationException("Texture " + textureFile.getPath() + " is not a valid PNG image");
        }

        KciTexture.validateImage(image);
        KciTexture texture = KciTexture.createQuick(textureName, image);
        itemSet.textures.add(texture);
        item.setTexture(itemSet.textures.getReference(textureName));
    }

    private static File resolveTextureFile(YamlItemDefinition itemDefinition) {
        File assetsDir = new File(itemDefinition.packDirectory, "assets/item");
        File byName = new File(assetsDir, itemDefinition.idName + ".png");
        if (byName.isFile()) return byName;

        File byInternalName = new File(assetsDir, itemDefinition.internalName + ".png");
        if (byInternalName.isFile()) return byInternalName;

        return null;
    }

    private static void applyMaterial(YamlItemDefinition itemDefinition, KciItem item) {
        if (itemDefinition.material == null) return;
        item.setItemType(itemDefinition.material.itemType);
        if (itemDefinition.material.otherMaterial != null) {
            item.setOtherMaterial(itemDefinition.material.otherMaterial);
        }
    }

    private static void applyStackSize(YamlItemDefinition itemDefinition, KciItem item) {
        if (itemDefinition.stackSize == null) return;
        if (item instanceof KciSimpleItem) {
            ((KciSimpleItem) item).setMaxStacksize(itemDefinition.stackSize.byteValue());
        } else if (item instanceof KciFood) {
            ((KciFood) item).setMaxStacksize(itemDefinition.stackSize.byteValue());
        }
    }

    private static void applyEnchantments(YamlItemDefinition itemDefinition, KciItem item) {
        if (itemDefinition.enchantments.isEmpty()) return;

        Collection<LeveledEnchantment> leveledEnchantments = new ArrayList<>(itemDefinition.enchantments.size());
        for (YamlEnchantmentDefinition enchantment : itemDefinition.enchantments) {
            leveledEnchantments.add(
                    LeveledEnchantment.createQuick(enchantment.type, enchantment.level)
            );
        }
        item.setDefaultEnchantments(leveledEnchantments);
        if (item instanceof KciTool) {
            ((KciTool) item).setAllowEnchanting(false);
        }
    }

    private static void applyAttributes(YamlItemDefinition itemDefinition, KciItem item) {
        Double armorValue = null;
        Double armorToughness = null;
        if (itemDefinition.type == YamlItemType.ARMOR) {
            if (itemDefinition.armorDefinition != null) {
                armorValue = itemDefinition.armorDefinition.armorValue;
                armorToughness = itemDefinition.armorDefinition.armorToughness;
            }
            if (armorValue == null) {
                armorValue = getDefaultArmorValue(item.getItemType());
            }
            if (armorToughness == null) {
                armorToughness = getDefaultArmorToughness(item.getItemType());
            }
        }

        boolean hasArmorValues = armorValue != null && (armorValue != 0.0 || armorToughness != null && armorToughness != 0.0);
        if (itemDefinition.attackDamage == null && itemDefinition.attackSpeed == null && !hasArmorValues) return;

        Collection<KciAttributeModifier> attributes = new ArrayList<>(4);
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
        if (hasArmorValues) {
            KciAttributeModifier.Slot armorSlot = getArmorSlot(item.getItemType());
            if (armorValue != null && armorValue != 0.0) {
                attributes.add(KciAttributeModifier.createQuick(
                        KciAttributeModifier.Attribute.ARMOR,
                        armorSlot,
                        KciAttributeModifier.Operation.ADD,
                        armorValue
                ));
            }
            if (armorToughness != null && armorToughness != 0.0) {
                attributes.add(KciAttributeModifier.createQuick(
                        KciAttributeModifier.Attribute.ARMOR_TOUGHNESS,
                        armorSlot,
                        KciAttributeModifier.Operation.ADD,
                        armorToughness
                ));
            }
        }
        item.setAttributeModifiers(attributes);
    }

    private static KciAttributeModifier.Slot getArmorSlot(KciItemType itemType) {
        if (itemType.canServe(KciItemType.Category.HELMET)) return KciAttributeModifier.Slot.HEAD;
        if (itemType.canServe(KciItemType.Category.CHESTPLATE)) return KciAttributeModifier.Slot.CHEST;
        if (itemType.canServe(KciItemType.Category.LEGGINGS)) return KciAttributeModifier.Slot.LEGS;
        return KciAttributeModifier.Slot.FEET;
    }

    private static double getDefaultArmorValue(KciItemType itemType) {
        if (itemType == KciItemType.NETHERITE_HELMET || itemType == KciItemType.DIAMOND_HELMET) return 3.0;
        if (itemType == KciItemType.NETHERITE_CHESTPLATE || itemType == KciItemType.DIAMOND_CHESTPLATE) return 8.0;
        if (itemType == KciItemType.NETHERITE_LEGGINGS || itemType == KciItemType.DIAMOND_LEGGINGS) return 6.0;
        if (itemType == KciItemType.NETHERITE_BOOTS || itemType == KciItemType.DIAMOND_BOOTS) return 3.0;

        if (itemType == KciItemType.IRON_HELMET) return 2.0;
        if (itemType == KciItemType.IRON_CHESTPLATE) return 6.0;
        if (itemType == KciItemType.IRON_LEGGINGS) return 5.0;
        if (itemType == KciItemType.IRON_BOOTS) return 2.0;

        if (itemType == KciItemType.CHAINMAIL_HELMET) return 2.0;
        if (itemType == KciItemType.CHAINMAIL_CHESTPLATE) return 5.0;
        if (itemType == KciItemType.CHAINMAIL_LEGGINGS) return 4.0;
        if (itemType == KciItemType.CHAINMAIL_BOOTS) return 1.0;

        if (itemType == KciItemType.GOLD_HELMET) return 2.0;
        if (itemType == KciItemType.GOLD_CHESTPLATE) return 5.0;
        if (itemType == KciItemType.GOLD_LEGGINGS) return 3.0;
        if (itemType == KciItemType.GOLD_BOOTS) return 1.0;

        if (itemType == KciItemType.LEATHER_HELMET) return 1.0;
        if (itemType == KciItemType.LEATHER_CHESTPLATE) return 3.0;
        if (itemType == KciItemType.LEATHER_LEGGINGS) return 2.0;
        if (itemType == KciItemType.LEATHER_BOOTS) return 1.0;

        return 0.0;
    }

    private static double getDefaultArmorToughness(KciItemType itemType) {
        if (itemType == KciItemType.NETHERITE_HELMET || itemType == KciItemType.NETHERITE_CHESTPLATE
                || itemType == KciItemType.NETHERITE_LEGGINGS || itemType == KciItemType.NETHERITE_BOOTS) {
            return 3.0;
        }
        if (itemType == KciItemType.DIAMOND_HELMET || itemType == KciItemType.DIAMOND_CHESTPLATE
                || itemType == KciItemType.DIAMOND_LEGGINGS || itemType == KciItemType.DIAMOND_BOOTS) {
            return 2.0;
        }
        return 0.0;
    }
    private static void applyToolDefinition(YamlItemDefinition itemDefinition, KciTool tool) {
        Integer maxDurability = null;
        Integer entityHitLoss = null;
        Integer blockBreakLoss = null;
        if (itemDefinition.type == YamlItemType.TOOL && itemDefinition.toolDefinition != null) {
            maxDurability = itemDefinition.toolDefinition.maxDurability;
            entityHitLoss = itemDefinition.toolDefinition.entityHitDurabilityLoss;
            blockBreakLoss = itemDefinition.toolDefinition.blockBreakDurabilityLoss;
        } else if (itemDefinition.type == YamlItemType.ARMOR && itemDefinition.armorDefinition != null) {
            maxDurability = itemDefinition.armorDefinition.maxDurability;
            entityHitLoss = itemDefinition.armorDefinition.entityHitDurabilityLoss;
            blockBreakLoss = itemDefinition.armorDefinition.blockBreakDurabilityLoss;
        }

        boolean unbreakable = itemDefinition.unbreakable != null && itemDefinition.unbreakable;
        if (unbreakable) {
            tool.setMaxDurabilityNew(null);
        } else if (maxDurability != null) {
            tool.setMaxDurabilityNew(maxDurability.longValue());
        } else if (tool.getItemType() != KciItemType.OTHER) {
            tool.setMaxDurabilityNew((long) tool.getItemType().getMaxDurability(mcVersion));
        }

        if (entityHitLoss != null) {
            tool.setEntityHitDurabilityLoss(entityHitLoss);
        } else if (tool.getItemType() != KciItemType.OTHER) {
            tool.setEntityHitDurabilityLoss(ToolDurabilityLoss.defaultEntityHitDurabilityLoss(tool.getItemType()));
        }

        if (blockBreakLoss != null) {
            tool.setBlockBreakDurabilityLoss(blockBreakLoss);
        } else if (tool.getItemType() != KciItemType.OTHER) {
            tool.setBlockBreakDurabilityLoss(ToolDurabilityLoss.defaultBlockBreakDurabilityLoss(tool.getItemType()));
        }
    }

    private static void applyFoodDefinition(YamlItemDefinition itemDefinition, KciFood food) {
        if (itemDefinition.foodDefinition == null) return;
        if (itemDefinition.foodDefinition.foodValue != null) {
            food.setFoodValue(itemDefinition.foodDefinition.foodValue);
        }
        if (itemDefinition.foodDefinition.eatTime != null) {
            food.setEatTime(itemDefinition.foodDefinition.eatTime);
        }
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
