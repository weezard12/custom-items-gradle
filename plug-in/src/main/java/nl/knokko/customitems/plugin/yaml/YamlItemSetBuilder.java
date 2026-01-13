package nl.knokko.customitems.plugin.yaml;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import nl.knokko.customitems.bithelper.ByteArrayBitOutput;
import nl.knokko.customitems.block.BlockSounds;
import nl.knokko.customitems.block.KciBlock;
import nl.knokko.customitems.block.drop.CustomBlockDrop;
import nl.knokko.customitems.block.drop.RequiredItems;
import nl.knokko.customitems.block.miningspeed.CustomMiningSpeedEntry;
import nl.knokko.customitems.block.miningspeed.MiningSpeed;
import nl.knokko.customitems.block.miningspeed.VanillaMiningSpeedEntry;
import nl.knokko.customitems.block.model.BlockModel;
import nl.knokko.customitems.block.model.CustomBlockModel;
import nl.knokko.customitems.block.model.SidedBlockModel;
import nl.knokko.customitems.block.model.SimpleBlockModel;
import nl.knokko.customitems.drops.AllowedBiomes;
import nl.knokko.customitems.drops.KciDrop;
import nl.knokko.customitems.item.KciAttributeModifier;
import nl.knokko.customitems.item.KciArmor;
import nl.knokko.customitems.item.KciBlockItem;
import nl.knokko.customitems.item.KciFood;
import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.item.KciItemType;
import nl.knokko.customitems.item.KciSimpleItem;
import nl.knokko.customitems.item.KciTool;
import nl.knokko.customitems.item.ToolDurabilityLoss;
import nl.knokko.customitems.item.model.ModernCustomItemModel;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.itemset.TextureReference;
import nl.knokko.customitems.itemset.UpgradeReference;
import nl.knokko.customitems.item.enchantment.LeveledEnchantment;
import nl.knokko.customitems.recipe.KciCraftingRecipe;
import nl.knokko.customitems.recipe.KciShapedRecipe;
import nl.knokko.customitems.recipe.KciShapelessRecipe;
import nl.knokko.customitems.recipe.OutputTable;
import nl.knokko.customitems.recipe.ingredient.CopiedIngredient;
import nl.knokko.customitems.recipe.ingredient.CustomItemIngredient;
import nl.knokko.customitems.recipe.ingredient.DataVanillaIngredient;
import nl.knokko.customitems.recipe.ingredient.ItemBridgeIngredient;
import nl.knokko.customitems.recipe.ingredient.KciIngredient;
import nl.knokko.customitems.recipe.ingredient.MimicIngredient;
import nl.knokko.customitems.recipe.ingredient.NoIngredient;
import nl.knokko.customitems.recipe.ingredient.SimpleVanillaIngredient;
import nl.knokko.customitems.recipe.ingredient.constraint.DurabilityConstraint;
import nl.knokko.customitems.recipe.ingredient.constraint.EnchantmentConstraint;
import nl.knokko.customitems.recipe.ingredient.constraint.IngredientConstraints;
import nl.knokko.customitems.recipe.ingredient.constraint.VariableConstraint;
import nl.knokko.customitems.recipe.result.CustomItemResult;
import nl.knokko.customitems.recipe.result.CopiedResult;
import nl.knokko.customitems.recipe.result.DataVanillaResult;
import nl.knokko.customitems.recipe.result.ItemBridgeResult;
import nl.knokko.customitems.recipe.result.KciResult;
import nl.knokko.customitems.recipe.result.MimicResult;
import nl.knokko.customitems.recipe.result.SimpleVanillaResult;
import nl.knokko.customitems.recipe.result.UpgradeResult;
import nl.knokko.customitems.recipe.upgrade.Upgrade;
import nl.knokko.customitems.settings.ExportSettings;
import nl.knokko.customitems.sound.KciSound;
import nl.knokko.customitems.texture.KciTexture;
import nl.knokko.customitems.util.Chance;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static nl.knokko.customitems.nms.KciNms.mcVersion;

public class YamlItemSetBuilder {

    public static final String PLACEHOLDER_TEXTURE_NAME = "yaml_placeholder";

    static ItemSet build(Collection<YamlItemDefinition> items)
            throws ValidationException, ProgrammingValidationException {
        return build(items, Collections.emptyList(), Collections.emptyList());
    }

    static ItemSet build(Collection<YamlItemDefinition> items, Collection<YamlBlockDefinition> blocks)
            throws ValidationException, ProgrammingValidationException {
        return build(items, blocks, Collections.emptyList());
    }

    static ItemSet build(
            Collection<YamlItemDefinition> items,
            Collection<YamlBlockDefinition> blocks,
            Collection<YamlRecipeDefinition> recipes
    ) throws ValidationException, ProgrammingValidationException {
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

        List<YamlItemDefinition> blockItems = new ArrayList<>();
        for (YamlItemDefinition itemDefinition : items) {
            if (itemDefinition.type == YamlItemType.BLOCK) {
                blockItems.add(itemDefinition);
                continue;
            }
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

        Map<String, KciBlock> blockByName = new HashMap<>();
        for (YamlBlockDefinition blockDefinition : blocks) {
            KciBlock block = new KciBlock(true);
            block.setName(blockDefinition.internalName);
            block.setModel(createBlockModel(blockDefinition, itemSet));
            itemSet.blocks.add(block);
            blockByName.put(blockDefinition.internalName, block);
        }

        for (YamlItemDefinition itemDefinition : blockItems) {
            KciBlockItem item = (KciBlockItem) createItem(itemDefinition);
            applyBaseProperties(itemDefinition, item);
            applyBlockItemDefinition(itemDefinition, item, itemSet);
            applyStackSize(itemDefinition, item);
            applyEnchantments(itemDefinition, item);
            applyAttributes(itemDefinition, item);
            itemSet.items.add(item);
        }

        for (YamlBlockDefinition blockDefinition : blocks) {
            KciBlock block = blockByName.get(blockDefinition.internalName);
            if (block == null) continue;
            applyBlockMiningSpeed(blockDefinition, block, itemSet);
            applyBlockSounds(blockDefinition, block);
            applyBlockDrops(blockDefinition, block, itemSet);
        }

        applyRecipes(recipes, itemSet);

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
        if (itemDefinition.type == YamlItemType.BLOCK) {
            return new KciBlockItem(true);
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
        } else if (item instanceof KciBlockItem) {
            ((KciBlockItem) item).setMaxStacksize(itemDefinition.stackSize.byteValue());
        }
    }

    private static void applyBlockItemDefinition(
            YamlItemDefinition itemDefinition, KciBlockItem item, ItemSet itemSet
    ) throws ValidationException {
        if (itemDefinition.blockInternalName == null) {
            throw new ValidationException("Missing block reference for " + itemDefinition.fullId
                    + " (" + itemDefinition.sourceFile.getPath() + ")");
        }
        KciBlock block = itemSet.blocks.get(itemDefinition.blockInternalName).orElse(null);
        if (block == null) {
            throw new ValidationException("Unknown block '" + itemDefinition.blockInternalName + "' for item "
                    + itemDefinition.fullId + " (" + itemDefinition.sourceFile.getPath() + ")");
        }
        item.setBlock(itemSet.blocks.getReference(block.getInternalID()));
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

    private static BlockModel createBlockModel(
            YamlBlockDefinition blockDefinition, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        if (blockDefinition.modelDefinition == null) {
            return new SimpleBlockModel(itemSet.textures.getReference(PLACEHOLDER_TEXTURE_NAME));
        }

        YamlBlockModelDefinition modelDefinition = blockDefinition.modelDefinition;
        if (modelDefinition.type == YamlBlockModelType.SIDED) {
            Map<String, String> textures = modelDefinition.sidedTextures;
            TextureReference north = resolveBlockTexture(blockDefinition, textures.get("north"), itemSet,
                    blockDefinition.internalName + "_north", true);
            TextureReference east = resolveBlockTexture(blockDefinition, textures.get("east"), itemSet,
                    blockDefinition.internalName + "_east", true);
            TextureReference south = resolveBlockTexture(blockDefinition, textures.get("south"), itemSet,
                    blockDefinition.internalName + "_south", true);
            TextureReference west = resolveBlockTexture(blockDefinition, textures.get("west"), itemSet,
                    blockDefinition.internalName + "_west", true);
            TextureReference up = resolveBlockTexture(blockDefinition, textures.get("up"), itemSet,
                    blockDefinition.internalName + "_up", true);
            TextureReference down = resolveBlockTexture(blockDefinition, textures.get("down"), itemSet,
                    blockDefinition.internalName + "_down", true);
            return new SidedBlockModel(north, east, south, west, up, down);
        }

        if (modelDefinition.type == YamlBlockModelType.CUSTOM) {
            return createCustomBlockModel(blockDefinition, modelDefinition.customModel, itemSet);
        }

        TextureReference texture = resolveBlockTexture(
                blockDefinition,
                modelDefinition.simpleTexture,
                itemSet,
                blockDefinition.internalName,
                modelDefinition.simpleTexture != null
        );
        if (texture == null) {
            texture = itemSet.textures.getReference(PLACEHOLDER_TEXTURE_NAME);
        }
        return new SimpleBlockModel(texture);
    }

    private static void applyBlockMiningSpeed(
            YamlBlockDefinition blockDefinition, KciBlock block, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        if (blockDefinition.miningSpeed == null) return;

        MiningSpeed miningSpeed = new MiningSpeed(true);
        if (blockDefinition.miningSpeed.defaultValue != null) {
            miningSpeed.setDefaultValue(blockDefinition.miningSpeed.defaultValue);
        }

        List<VanillaMiningSpeedEntry> vanillaEntries = new ArrayList<>();
        for (YamlBlockVanillaMiningSpeedEntry entry : blockDefinition.miningSpeed.vanillaEntries) {
            VanillaMiningSpeedEntry vanillaEntry = new VanillaMiningSpeedEntry(true);
            vanillaEntry.setMaterial(entry.material);
            vanillaEntry.setValue(entry.value);
            vanillaEntry.setAcceptCustomItems(entry.allowCustomItems);
            vanillaEntries.add(vanillaEntry);
        }
        miningSpeed.setVanillaEntries(vanillaEntries);

        List<CustomMiningSpeedEntry> customEntries = new ArrayList<>();
        for (YamlBlockCustomMiningSpeedEntry entry : blockDefinition.miningSpeed.customEntries) {
            CustomMiningSpeedEntry customEntry = new CustomMiningSpeedEntry(true);
            customEntry.setValue(entry.value);
            customEntry.setItemReference(itemSet.items.getReference(entry.itemInternalName));
            customEntries.add(customEntry);
        }
        miningSpeed.setCustomEntries(customEntries);

        block.setMiningSpeed(miningSpeed);
    }

    private static void applyBlockSounds(YamlBlockDefinition blockDefinition, KciBlock block) {
        if (blockDefinition.sounds == null) return;

        BlockSounds sounds = new BlockSounds(true);
        if (blockDefinition.sounds.leftClick != null) {
            sounds.setLeftClickSound(createSound(blockDefinition.sounds.leftClick));
        }
        if (blockDefinition.sounds.rightClick != null) {
            sounds.setRightClickSound(createSound(blockDefinition.sounds.rightClick));
        }
        if (blockDefinition.sounds.breakSound != null) {
            sounds.setBreakSound(createSound(blockDefinition.sounds.breakSound));
        }
        if (blockDefinition.sounds.step != null) {
            sounds.setStepSound(createSound(blockDefinition.sounds.step));
        }
        block.setSounds(sounds);
    }

    private static KciSound createSound(YamlSoundDefinition definition) {
        return KciSound.createQuick(definition.soundType, definition.volume, definition.pitch);
    }

    private static void applyBlockDrops(
            YamlBlockDefinition blockDefinition, KciBlock block, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        if (blockDefinition.drops == null || blockDefinition.drops.isEmpty()) return;

        List<CustomBlockDrop> drops = new ArrayList<>();
        for (YamlBlockDropDefinition dropDefinition : blockDefinition.drops) {
            CustomBlockDrop drop = new CustomBlockDrop(true);
            if (dropDefinition.silkTouchRequirement != null) {
                drop.setSilkTouchRequirement(dropDefinition.silkTouchRequirement);
            }
            if (dropDefinition.minFortuneLevel != null) {
                drop.setMinFortuneLevel(dropDefinition.minFortuneLevel);
            }
            if (dropDefinition.maxFortuneLevel != null) {
                drop.setMaxFortuneLevel(dropDefinition.maxFortuneLevel);
            }

            KciDrop dropConfig = new KciDrop(true);
            if (dropDefinition.cancelNormalDrops != null) {
                dropConfig.setCancelNormalDrops(dropDefinition.cancelNormalDrops);
            }

            OutputTable outputTable = new OutputTable(true);
            List<OutputTable.Entry> entries = new ArrayList<>();
            for (YamlBlockDropOutputDefinition output : dropDefinition.outputs) {
                OutputTable.Entry entry = new OutputTable.Entry(true);
                entry.setChance(createChance(output.chance));
                if (output.customItemInternalName != null) {
                    entry.setResult(createCustomItemResult(itemSet, output));
                } else {
                    entry.setResult(SimpleVanillaResult.createQuick(output.material, output.amount));
                }
                entries.add(entry);
            }
            outputTable.setEntries(entries);
            dropConfig.setOutputTable(outputTable);

            if (dropDefinition.requiredItems != null) {
                dropConfig.setRequiredHeldItems(createRequiredItems(itemSet, dropDefinition.requiredItems));
            }
            if (dropDefinition.allowedBiomes != null) {
                dropConfig.setAllowedBiomes(createAllowedBiomes(dropDefinition.allowedBiomes));
            }
            drop.setDrop(dropConfig);
            drops.add(drop);
        }

        block.setDrops(drops);
    }

    private static CustomItemResult createCustomItemResult(
            ItemSet itemSet, YamlBlockDropOutputDefinition output
    ) throws ValidationException {
        CustomItemResult result = CustomItemResult.createQuick(
                itemSet.items.getReference(output.customItemInternalName),
                output.amount
        );
        if (output.amount > result.getItem().getMaxStacksize()) {
            throw new ValidationException("Drop amount " + output.amount + " exceeds max stack size for "
                    + result.getItem().getName());
        }
        return result;
    }

    private static Chance createChance(double chancePercentage) {
        double rounded = Math.rint(chancePercentage);
        if (Math.abs(rounded - chancePercentage) < 0.0001) {
            return Chance.percentage((int) rounded);
        }
        return Chance.nonIntegerPercentage(chancePercentage);
    }

    private static void applyRecipes(
            Collection<YamlRecipeDefinition> recipes, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        if (recipes == null || recipes.isEmpty()) return;
        for (YamlRecipeDefinition definition : recipes) {
            KciCraftingRecipe recipe = createRecipe(definition, itemSet);
            itemSet.craftingRecipes.add(recipe);
        }
    }

    private static KciCraftingRecipe createRecipe(
            YamlRecipeDefinition definition, ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        KciResult result = createResult(definition.result, itemSet, definition.sourceFile);

        if (definition.type == YamlRecipeType.SHAPED) {
            KciShapedRecipe recipe = new KciShapedRecipe(true);
            recipe.setIgnoreDisplacement(definition.ignoreDisplacement);
            recipe.setResult(result);
            recipe.setRequiredPermission(definition.requiredPermission);

            YamlRecipeIngredientDefinition[] ingredients = definition.shapedIngredients;
            for (int index = 0; index < ingredients.length; index++) {
                KciIngredient ingredient = createIngredient(ingredients[index], itemSet, definition.sourceFile);
                recipe.setIngredientAt(index % 3, index / 3, ingredient);
            }
            return recipe;
        }

        KciShapelessRecipe recipe = new KciShapelessRecipe(true);
        List<KciIngredient> ingredients = new ArrayList<>(definition.shapelessIngredients.size());
        for (YamlRecipeIngredientDefinition ingredientDefinition : definition.shapelessIngredients) {
            ingredients.add(createIngredient(ingredientDefinition, itemSet, definition.sourceFile));
        }
        recipe.setIngredients(ingredients);
        recipe.setResult(result);
        recipe.setRequiredPermission(definition.requiredPermission);
        return recipe;
    }

    private static KciIngredient createIngredient(
            YamlRecipeIngredientDefinition definition, ItemSet itemSet, File sourceFile
    ) throws ValidationException {
        if (definition == null || definition.type == YamlRecipeIngredientType.NONE) {
            return new NoIngredient();
        }

        IngredientConstraints constraints = createConstraints(definition.constraints);
        KciResult remainingItem = definition.remainingItem != null
                ? createResult(definition.remainingItem, itemSet, sourceFile)
                : null;

        switch (definition.type) {
            case VANILLA:
                if (definition.material == null) {
                    throw new ValidationException("Missing vanilla material in " + sourceFile.getPath());
                }
                return SimpleVanillaIngredient.createQuick(
                        definition.material, definition.amount, remainingItem, constraints
                );
            case VANILLA_DATA:
                if (definition.dataValue == null) {
                    throw new ValidationException("Missing data value for vanilla ingredient in " + sourceFile.getPath());
                }
                if (definition.material == null) {
                    throw new ValidationException("Missing vanilla material in " + sourceFile.getPath());
                }
                return DataVanillaIngredient.createQuick(
                        definition.material, definition.dataValue, definition.amount, remainingItem, constraints
                );
            case CUSTOM:
                if (definition.customItemInternalName == null) {
                    throw new ValidationException("Missing custom item id in " + sourceFile.getPath());
                }
                if (!itemSet.items.get(definition.customItemInternalName).isPresent()) {
                    throw new ValidationException("Unknown custom item '" + definition.customItemInternalName
                            + "' in " + sourceFile.getPath());
                }
                return CustomItemIngredient.createQuick(
                        itemSet.items.getReference(definition.customItemInternalName),
                        definition.amount,
                        remainingItem,
                        constraints
                );
            case MIMIC:
                if (definition.foreignItemId == null || definition.foreignItemId.isEmpty()) {
                    throw new ValidationException("Missing mimic item id in " + sourceFile.getPath());
                }
                return MimicIngredient.createQuick(
                        definition.foreignItemId, definition.amount, remainingItem, constraints
                );
            case ITEM_BRIDGE:
                if (definition.foreignItemId == null || definition.foreignItemId.isEmpty()) {
                    throw new ValidationException("Missing item bridge id in " + sourceFile.getPath());
                }
                return ItemBridgeIngredient.createQuick(
                        definition.foreignItemId, definition.amount, remainingItem, constraints
                );
            case COPIED:
                if (definition.encoded == null || definition.encoded.isEmpty()) {
                    throw new ValidationException("Missing copied ingredient data in " + sourceFile.getPath());
                }
                return CopiedIngredient.createQuick(
                        definition.amount, definition.encoded, remainingItem, constraints
                );
            case NONE:
            default:
                return new NoIngredient();
        }
    }

    private static KciResult createResult(
            YamlRecipeResultDefinition definition, ItemSet itemSet, File sourceFile
    ) throws ValidationException {
        if (definition == null) return null;

        switch (definition.type) {
            case CUSTOM:
                if (definition.customItemInternalName == null) {
                    throw new ValidationException("Missing recipe result item in " + sourceFile.getPath());
                }
                if (!itemSet.items.get(definition.customItemInternalName).isPresent()) {
                    throw new ValidationException("Unknown recipe result item '" + definition.customItemInternalName
                            + "' in " + sourceFile.getPath());
                }
                return CustomItemResult.createQuick(
                        itemSet.items.getReference(definition.customItemInternalName),
                        definition.amount
                );
            case VANILLA:
                if (definition.material == null) {
                    throw new ValidationException("Missing vanilla material in " + sourceFile.getPath());
                }
                return SimpleVanillaResult.createQuick(definition.material, definition.amount);
            case VANILLA_DATA:
                if (definition.dataValue == null) {
                    throw new ValidationException("Missing data value for vanilla result in " + sourceFile.getPath());
                }
                if (definition.material == null) {
                    throw new ValidationException("Missing vanilla material in " + sourceFile.getPath());
                }
                return DataVanillaResult.createQuick(definition.material, definition.dataValue, definition.amount);
            case MIMIC:
                if (definition.foreignItemId == null || definition.foreignItemId.isEmpty()) {
                    throw new ValidationException("Missing mimic item id in " + sourceFile.getPath());
                }
                return MimicResult.createQuick(definition.foreignItemId, definition.amount);
            case ITEM_BRIDGE:
                if (definition.foreignItemId == null || definition.foreignItemId.isEmpty()) {
                    throw new ValidationException("Missing item bridge id in " + sourceFile.getPath());
                }
                return ItemBridgeResult.createQuick(definition.foreignItemId, definition.amount);
            case COPIED:
                if (definition.encoded == null || definition.encoded.isEmpty()) {
                    throw new ValidationException("Missing copied item data in " + sourceFile.getPath());
                }
                return CopiedResult.createQuick(definition.encoded);
            case UPGRADE:
                return createUpgradeResult(definition.upgrade, itemSet, sourceFile);
            default:
                throw new ValidationException("Unknown recipe result type in " + sourceFile.getPath());
        }
    }

    private static UpgradeResult createUpgradeResult(
            YamlUpgradeResultDefinition definition, ItemSet itemSet, File sourceFile
    ) throws ValidationException {
        if (definition == null) {
            throw new ValidationException("Missing upgrade result details in " + sourceFile.getPath());
        }

        UpgradeResult result = new UpgradeResult(true);
        if (definition.ingredientIndex != null && definition.inputSlotName != null) {
            throw new ValidationException("Upgrade result can't define both ingredient index and input slot ("
                    + sourceFile.getPath() + ")");
        }
        if (definition.ingredientIndex != null) {
            result.setIngredientIndex(definition.ingredientIndex);
        }
        if (definition.inputSlotName != null) {
            result.setInputSlotName(definition.inputSlotName);
        }

        List<UpgradeReference> upgradeReferences = new ArrayList<>(definition.upgrades.size());
        for (String upgradeName : definition.upgrades) {
            upgradeReferences.add(resolveUpgradeReference(itemSet, upgradeName, sourceFile));
        }
        result.setUpgrades(upgradeReferences);

        if (definition.repairPercentage != null) {
            result.setRepairPercentage(definition.repairPercentage);
        }
        if (definition.newType != null) {
            result.setNewType(createResult(definition.newType, itemSet, sourceFile));
        }
        if (definition.keepOldUpgrades != null) {
            result.setKeepOldUpgrades(definition.keepOldUpgrades);
        }
        if (definition.keepOldEnchantments != null) {
            result.setKeepOldEnchantments(definition.keepOldEnchantments);
        }

        return result;
    }

    private static UpgradeReference resolveUpgradeReference(
            ItemSet itemSet, String name, File sourceFile
    ) throws ValidationException {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("Upgrade name must not be empty in " + sourceFile.getPath());
        }
        for (Upgrade upgrade : itemSet.upgrades) {
            if (name.equals(upgrade.getName())) {
                return itemSet.upgrades.getReference(upgrade.getId());
            }
        }
        throw new ValidationException("Unknown upgrade '" + name + "' in " + sourceFile.getPath());
    }

    private static IngredientConstraints createConstraints(YamlRecipeConstraintsDefinition definition) {
        IngredientConstraints constraints = new IngredientConstraints(true);
        if (definition == null) return constraints;

        List<DurabilityConstraint> durabilityConstraints = new ArrayList<>(
                definition.durabilityConstraints.size()
        );
        for (YamlDurabilityConstraintDefinition durability : definition.durabilityConstraints) {
            durabilityConstraints.add(DurabilityConstraint.createQuick(durability.operator, durability.percentage));
        }
        constraints.setDurabilityConstraints(durabilityConstraints);

        List<EnchantmentConstraint> enchantmentConstraints = new ArrayList<>(
                definition.enchantmentConstraints.size()
        );
        for (YamlEnchantmentConstraintDefinition enchantment : definition.enchantmentConstraints) {
            enchantmentConstraints.add(EnchantmentConstraint.createQuick(
                    enchantment.enchantment, enchantment.operator, enchantment.level
            ));
        }
        constraints.setEnchantmentConstraints(enchantmentConstraints);

        List<VariableConstraint> variableConstraints = new ArrayList<>(
                definition.variableConstraints.size()
        );
        for (YamlVariableConstraintDefinition variable : definition.variableConstraints) {
            VariableConstraint constraint = new VariableConstraint(true);
            constraint.setVariable(variable.variable);
            constraint.setOperator(variable.operator);
            constraint.setValue(variable.value);
            variableConstraints.add(constraint.copy(false));
        }
        constraints.setVariableConstraints(variableConstraints);

        return constraints;
    }

    private static RequiredItems createRequiredItems(
            ItemSet itemSet, YamlRequiredItemsDefinition definition
    ) {
        RequiredItems requiredItems = new RequiredItems(true);
        requiredItems.setEnabled(definition.enabled);
        requiredItems.setInverted(definition.invert);

        List<RequiredItems.VanillaEntry> vanillaEntries = new ArrayList<>();
        for (YamlRequiredVanillaItemDefinition entry : definition.vanillaItems) {
            vanillaEntries.add(RequiredItems.VanillaEntry.createQuick(entry.material, entry.allowCustomItems));
        }
        requiredItems.setVanillaItems(vanillaEntries);

        List<nl.knokko.customitems.itemset.ItemReference> customEntries = new ArrayList<>();
        for (String itemName : definition.customItems) {
            customEntries.add(itemSet.items.getReference(itemName));
        }
        requiredItems.setCustomItems(customEntries);
        return requiredItems;
    }

    private static AllowedBiomes createAllowedBiomes(YamlAllowedBiomesDefinition definition) {
        AllowedBiomes biomes = new AllowedBiomes(true);
        biomes.setWhitelist(definition.whitelist);
        biomes.setBlacklist(definition.blacklist);
        return biomes;
    }

    private static BlockModel createCustomBlockModel(
            YamlBlockDefinition blockDefinition,
            YamlBlockCustomModelDefinition customModelDefinition,
            ItemSet itemSet
    ) throws ValidationException, ProgrammingValidationException {
        if (customModelDefinition == null) {
            throw new ValidationException("Custom block model is missing for " + blockDefinition.fullId);
        }

        File modelFile = resolveModelFile(blockDefinition.packDirectory, customModelDefinition.modelPath);
        if (modelFile == null || !modelFile.isFile()) {
            throw new ValidationException("Missing block model file " + customModelDefinition.modelPath
                    + " (" + blockDefinition.sourceFile.getPath() + ")");
        }

        byte[] rawModel;
        try {
            rawModel = Files.readAllBytes(modelFile.toPath());
        } catch (IOException ex) {
            throw new ValidationException("Failed to read block model " + modelFile.getPath() + ": " + ex.getMessage());
        }

        JsonObject modelJson;
        try {
            modelJson = (JsonObject) Jsoner.deserialize(new String(rawModel, StandardCharsets.UTF_8));
        } catch (JsonException ex) {
            throw new ValidationException("Invalid JSON in model " + modelFile.getPath());
        }

        if (modelJson == null) {
            throw new ValidationException("Model " + modelFile.getPath() + " is empty or invalid JSON");
        }

        Map<String, String> textureMap = modelJson.getMap(ModernCustomItemModel.TEXTURES_KEY);
        if (textureMap == null) {
            throw new ValidationException("Model " + modelFile.getPath() + " is missing a textures map");
        }

        Map<String, IncludedImageBuilder> includedImages = new HashMap<>();
        Map<String, Integer> usedNames = new HashMap<>();
        for (Map.Entry<String, String> entry : customModelDefinition.texturePaths.entrySet()) {
            String textureKey = entry.getKey();
            if (!textureMap.containsKey(textureKey)) {
                throw new ValidationException("Model " + modelFile.getPath() + " has no texture key '" + textureKey + "'");
            }
            File textureFile = resolveTextureFile(blockDefinition.packDirectory, entry.getValue());
            if (textureFile == null || !textureFile.isFile()) {
                throw new ValidationException("Missing model texture " + entry.getValue()
                        + " (" + blockDefinition.sourceFile.getPath() + ")");
            }

            String fileKey = textureFile.getPath();
            IncludedImageBuilder builder = includedImages.get(fileKey);
            if (builder == null) {
                BufferedImage image = loadTextureImage(textureFile, "model texture");
                String safeName = createSafeName(textureFile.getName(), usedNames);
                builder = new IncludedImageBuilder(safeName, image);
                includedImages.put(fileKey, builder);
            }
            builder.textureReferences.add(textureKey);
        }

        List<ModernCustomItemModel.IncludedImage> includedImageList = new ArrayList<>(includedImages.size());
        for (IncludedImageBuilder builder : includedImages.values()) {
            includedImageList.add(new ModernCustomItemModel.IncludedImage(
                    builder.textureReferences, builder.name, builder.image
            ));
        }

        ModernCustomItemModel model = new ModernCustomItemModel(rawModel, includedImageList);

        File editorTextureFile = resolveTextureFile(blockDefinition.packDirectory, customModelDefinition.editorTexturePath);
        if (editorTextureFile == null || !editorTextureFile.isFile()) {
            throw new ValidationException("Missing editor texture " + customModelDefinition.editorTexturePath
                    + " (" + blockDefinition.sourceFile.getPath() + ")");
        }
        BufferedImage editorImage = loadTextureImage(editorTextureFile, "editor texture");
        TextureReference editorTexture = addTexture(itemSet, blockDefinition.internalName + "_editor", editorImage);

        return new CustomBlockModel(model, editorTexture, null);
    }

    private static TextureReference resolveBlockTexture(
            YamlBlockDefinition blockDefinition,
            String rawTexture,
            ItemSet itemSet,
            String textureName,
            boolean required
    ) throws ValidationException, ProgrammingValidationException {
        String rawValue = rawTexture;
        boolean useIdFallback = false;
        if (rawValue == null || rawValue.trim().isEmpty()) {
            rawValue = blockDefinition.idName;
            useIdFallback = true;
        }
        File textureFile = resolveTextureFile(blockDefinition.packDirectory, rawValue);
        if ((textureFile == null || !textureFile.isFile()) && useIdFallback) {
            File globalTexture = resolveGlobalBlockTextureFile(blockDefinition.packDirectory, rawValue);
            if (globalTexture != null && globalTexture.isFile()) {
                textureFile = globalTexture;
            }
        }
        if (textureFile == null || !textureFile.isFile()) {
            if (required) {
                throw new ValidationException("Missing block texture " + rawValue + " for "
                        + blockDefinition.fullId + " (" + blockDefinition.sourceFile.getPath() + ")");
            }
            return null;
        }
        BufferedImage image = loadTextureImage(textureFile, "block texture");
        return addTexture(itemSet, textureName, image);
    }

    private static File resolveGlobalBlockTextureFile(File packDirectory, String rawValue) {
        if (rawValue == null) return null;
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) return null;
        String name = trimmed;
        int colonIndex = name.indexOf(':');
        if (colonIndex >= 0) {
            name = name.substring(colonIndex + 1);
        }
        if (name.isEmpty()) return null;
        File parent = packDirectory.getParentFile();
        if (parent == null) return null;
        File assetsDir = new File(parent, "assets/block");
        return new File(assetsDir, name + ".png");
    }

    private static File resolveModelFile(File packDirectory, String rawPath) {
        if (rawPath == null) return null;
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty()) return null;
        File candidate = new File(trimmed);
        if (!candidate.isAbsolute()) {
            candidate = new File(packDirectory, trimmed);
        }
        if (candidate.isFile()) return candidate;
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".json")) {
            File jsonCandidate = new File(candidate.getPath() + ".json");
            if (jsonCandidate.isFile()) return jsonCandidate;
        }
        return candidate;
    }

    private static File resolveTextureFile(File packDirectory, String rawValue) {
        if (rawValue == null) return null;
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) return null;
        boolean isPath = trimmed.contains("/") || trimmed.contains("\\")
                || trimmed.toLowerCase(Locale.ROOT).endsWith(".png");
        if (isPath) {
            File file = new File(trimmed);
            if (!file.isAbsolute()) {
                file = new File(packDirectory, trimmed);
            }
            return file;
        }
        int colonIndex = trimmed.indexOf(':');
        String name = colonIndex >= 0 ? trimmed.substring(colonIndex + 1) : trimmed;
        File assetsDir = new File(packDirectory, "assets/block");
        return new File(assetsDir, name + ".png");
    }

    private static BufferedImage loadTextureImage(
            File file, String description
    ) throws ValidationException, ProgrammingValidationException {
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read " + description + " " + file.getPath() + ": " + ex.getMessage());
        }
        if (image == null) {
            throw new ValidationException("Texture " + file.getPath() + " is not a valid PNG image");
        }
        KciTexture.validateImage(image);
        return image;
    }

    private static TextureReference addTexture(
            ItemSet itemSet, String textureName, BufferedImage image
    ) throws ValidationException, ProgrammingValidationException {
        if (itemSet.textures.get(textureName).isPresent()) {
            return itemSet.textures.getReference(textureName);
        }
        KciTexture texture = KciTexture.createQuick(textureName, image);
        itemSet.textures.add(texture);
        return itemSet.textures.getReference(textureName);
    }

    private static String createSafeName(String rawName, Map<String, Integer> used) throws ValidationException {
        String base = rawName;
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            base = base.substring(0, base.length() - 4);
        }
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                cleaned.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                cleaned.append(Character.toLowerCase(c));
            } else {
                cleaned.append('_');
            }
        }
        String name = cleaned.toString();
        if (name.isEmpty()) name = "texture";

        int count = used.getOrDefault(name, 0);
        if (count == 0) {
            used.put(name, 1);
            return name;
        }
        String candidate;
        do {
            count++;
            candidate = name + "_" + count;
        } while (used.containsKey(candidate));
        used.put(name, count);
        used.put(candidate, 1);
        return candidate;
    }

    private static class IncludedImageBuilder {

        final String name;
        final BufferedImage image;
        final List<String> textureReferences = new ArrayList<>();

        IncludedImageBuilder(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
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
