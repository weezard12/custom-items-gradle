package nl.knokko.customitems.plugin;

import nl.knokko.customitems.block.KciBlock;
import nl.knokko.customitems.item.KciBlockItem;
import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.itemset.ItemSet;
import nl.knokko.customitems.plugin.container.ContainerInfo;
import nl.knokko.customitems.plugin.container.ContainerInstance;
import nl.knokko.customitems.plugin.set.ItemSetWrapper;
import nl.knokko.customitems.plugin.set.block.MushroomBlockHelper;
import nl.knokko.customitems.plugin.util.ItemUtils;
import nl.knokko.customitems.plugin.yaml.YamlToCisConverter;
import nl.knokko.customitems.projectile.KciProjectile;
import nl.knokko.customitems.recipe.KciCraftingRecipe;
import nl.knokko.customitems.util.ProgrammingValidationException;
import nl.knokko.customitems.util.ValidationException;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import static nl.knokko.customitems.plugin.set.item.CustomItemWrapper.wrap;

public class CustomItemsApi {

    public static Collection<String> getAllItemNames() {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();

        Collection<String> itemNames = new ArrayList<>(itemSet.items.size());
        for (KciItem item : itemSet.items) {
            itemNames.add(item.getName());
        }

        return itemNames;
    }

    public static ItemStack createItemStack(String itemName, int amount) {
        ItemSetWrapper wrapper = CustomItemsPlugin.getInstance().getSet();

        KciItem item = wrapper.getItem(itemName);
        if (item != null) return wrap(item).create(amount);
        else return null;
    }

    /**
     * Creates an ItemStack by custom item id/internal name or block id/internal name.
     */
    public static ItemStack createItemStackById(String itemId, int amount) {
        ItemSetWrapper wrapper = CustomItemsPlugin.getInstance().getSet();
        KciItem item = getGiveItemById(wrapper, itemId);
        if (item != null) return wrap(item).create(amount);
        else return null;
    }

    /**
     * Gives a custom item or block item by item id/internal name or block id/internal name.
     * Full inventories follow the same behavior as `/kci give`.
     */
    public static void giveItem(Player player, String itemId, int amount) {
        ItemSetWrapper wrapper = CustomItemsPlugin.getInstance().getSet();
        giveResolvedItem(player, wrapper, getGiveItemById(wrapper, itemId), amount);
    }

    /**
     * Gives a custom item or block item by item id/internal name or block id/internal name
     * to each player in the collection.
     */
    public static void giveItem(Collection<? extends Player> players, String itemId, int amount) {
        if (players == null) return;

        ItemSetWrapper wrapper = CustomItemsPlugin.getInstance().getSet();
        KciItem item = getGiveItemById(wrapper, itemId);
        for (Player player : players) giveResolvedItem(player, wrapper, item, amount);
    }

    public static String getItemName(ItemStack itemStack) {
        KciItem item = CustomItemsPlugin.getInstance().getSet().getItem(itemStack);

        if (item != null) return item.getName();
        else return null;
    }

    /**
     * Returns the custom item id (alias) for the given ItemStack, or null when it isn't a custom item.
     * If the item has no alias, this falls back to the internal name.
     */
    public static String getItemId(ItemStack itemStack) {
        KciItem item = CustomItemsPlugin.getInstance().getSet().getItem(itemStack);
        if (item == null) return null;

        String alias = item.getAlias();
        if (alias != null && !alias.isEmpty()) return alias;
        return item.getName();
    }

    public static boolean hasItem(String itemName) {
        return CustomItemsPlugin.getInstance().getSet().getItem(itemName) != null;
    }

    /**
     * Checks whether a custom item or block item exists by item id/internal name or block id/internal name.
     */
    public static boolean hasItemId(String itemId) {
        ItemSetWrapper wrapper = CustomItemsPlugin.getInstance().getSet();
        return getGiveItemById(wrapper, itemId) != null;
    }

    /**
     * Returns all known custom item ids (alias). Items without alias will return their internal name.
     */
    public static Collection<String> getAllItemIds() {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();

        Collection<String> itemIds = new ArrayList<>(itemSet.items.size());
        for (KciItem item : itemSet.items) {
            String alias = item.getAlias();
            itemIds.add(alias == null || alias.isEmpty() ? item.getName() : alias);
        }

        return itemIds;
    }

    public static Collection<String> getAllBlockIds() {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();

        Collection<String> blockIds = new ArrayList<>(itemSet.blocks.size());
        for (KciBlock block : itemSet.blocks) {
            blockIds.add(toPublicBlockId(block.getName()));
        }

        return blockIds;
    }

    private static String normalizeBlockLookupName(String blockIdOrName) {
        if (blockIdOrName == null) return null;
        if (blockIdOrName.indexOf(':') >= 0) return blockIdOrName.replace(':', '_');
        return blockIdOrName;
    }

    private static String toPublicBlockId(String blockInternalName) {
        String mappedId = YamlToCisConverter.getYamlBlockId(blockInternalName);
        return mappedId != null ? mappedId : blockInternalName;
    }

    private static Optional<KciBlock> getBlockByIdOrInternalName(ItemSet itemSet, String blockIdOrName) {
        String lookupName = normalizeBlockLookupName(blockIdOrName);
        if (lookupName == null) return Optional.empty();
        return itemSet.blocks.get(lookupName);
    }

    private static Optional<KciBlockItem> getBlockItemByIdOrInternalName(ItemSet itemSet, String blockIdOrName) {
        Optional<KciBlock> maybeBlock = getBlockByIdOrInternalName(itemSet, blockIdOrName);
        if (!maybeBlock.isPresent()) return Optional.empty();

        String normalizedLookupName = normalizeBlockLookupName(blockIdOrName);
        KciBlock block = maybeBlock.get();
        KciBlockItem fallback = null;

        for (KciItem item : itemSet.items) {
            if (!(item instanceof KciBlockItem)) continue;

            KciBlockItem blockItem = (KciBlockItem) item;
            if (blockItem.getBlock().getInternalID() != block.getInternalID()) continue;

            if (blockIdOrName != null && blockIdOrName.equals(blockItem.getAlias())) {
                return Optional.of(blockItem);
            }
            if (normalizedLookupName != null && normalizedLookupName.equals(blockItem.getName())) {
                return Optional.of(blockItem);
            }
            if (fallback == null) fallback = blockItem;
        }

        return Optional.ofNullable(fallback);
    }

    private static KciItem getGiveItemById(ItemSetWrapper wrapper, String itemId) {
        KciItem item = wrapper.getItemById(itemId);
        if (item != null) return item;
        return getBlockItemByIdOrInternalName(wrapper.get(), itemId).orElse(null);
    }

    private static void giveResolvedItem(Player player, ItemSetWrapper wrapper, KciItem item, int amount) {
        if (player == null || item == null || amount < 1 || amount > item.getMaxStacksize()) return;
        ItemUtils.giveCustomItem(wrapper, player, item, amount);
    }

    public static void placeBlock(Block destination, String blockIdOrName) {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();
        Optional<KciBlock> customBlock = getBlockByIdOrInternalName(itemSet, blockIdOrName);
        if (customBlock.isPresent()) {
            MushroomBlockHelper.place(destination, customBlock.get());
        } else {
            destination.setType(Material.AIR);
        }
    }

    public static String getBlockName(Block block) {
        return getBlockId(block);
    }

    /**
     * Returns the public block id for the given block. For YAML blocks, this is typically namespace:name.
     * If no YAML id mapping exists, this falls back to the internal block name.
     */
    public static String getBlockId(Block block) {
        KciBlock customBlock = MushroomBlockHelper.getMushroomBlock(block);
        if (customBlock != null) return toPublicBlockId(customBlock.getName());
        return null;
    }

    /**
     * Returns the internal storage name of the custom block, or null when it is not a custom block.
     */
    public static String getBlockInternalName(Block block) {
        KciBlock customBlock = MushroomBlockHelper.getMushroomBlock(block);
        if (customBlock != null) return customBlock.getName();
        return null;
    }

    /**
     * Checks whether a custom block exists by namespaced id or internal name.
     */
    public static boolean hasBlock(String blockIdOrName) {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();
        return getBlockByIdOrInternalName(itemSet, blockIdOrName).isPresent();
    }

    /**
     * Alias for hasBlock(String), intended for id-focused API usage.
     */
    public static boolean hasBlockId(String blockId) {
        return hasBlock(blockId);
    }

    public static boolean hasProjectile(String projectileName) {
        return getProjectileByNameOrId(projectileName).isPresent();
    }

    public static void launchProjectile(LivingEntity shooter, String projectileName) {
        CustomItemsPlugin plugin = CustomItemsPlugin.getInstance();
        Optional<KciProjectile> maybeProjectile = getProjectileByNameOrId(projectileName);
        maybeProjectile.ifPresent(projectile -> plugin.getProjectileManager().fireProjectile(shooter, projectile));
    }

    /**
     * Returns all internal names of custom projectiles.
     */
    public static Collection<String> getAllProjectileNames() {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();

        Collection<String> projectileNames = new ArrayList<>(itemSet.projectiles.size());
        for (KciProjectile projectile : itemSet.projectiles) {
            projectileNames.add(projectile.getName());
        }
        return projectileNames;
    }

    private static Optional<KciProjectile> getProjectileByNameOrId(String projectileName) {
        if (projectileName == null) return Optional.empty();
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();
        Optional<KciProjectile> maybeProjectile = itemSet.projectiles.get(projectileName);
        if (!maybeProjectile.isPresent() && projectileName.indexOf(':') >= 0) {
            maybeProjectile = itemSet.projectiles.get(projectileName.replace(':', '_'));
        }
        return maybeProjectile;
    }


    /**
     * @param player The player that should open the container
     * @param containerName The name of the custom container to be opened
     * @param stringHost The host at which the container should be opened. This can be any string, but each distinct
     *                   host will count as a distinct location.
     * @return True if the container was opened successfully; False if there is no container with name <i>containerName</i>
     */
    public static boolean openContainerAtStringHost(Player player, String containerName, String stringHost) {
        ContainerInfo containerInfo = CustomItemsPlugin.getInstance().getSet().getContainerInfo(containerName);
        if (containerInfo != null) {
            ContainerInstance containerInstance = CustomItemsPlugin.getInstance().getData().containerManager.getCustomContainer(
                    null, stringHost, player, containerInfo.getContainer()
            );
            player.openInventory(containerInstance.getInventory());
            return true;
        } else {
            player.closeInventory();
            return false;
        }
    }

    /**
     * Destroys all instances of the given container at the given host.
     * @param containerName The name of the container whose instances should be destroyed
     * @param stringHost The host at which the container instances should be destroyed
     * @param dropLocation The location where all items that are stored in the destroyed containers will be dropped, or
     *                     null to discard all stored items
     * @return The number of destroyed container instances, or -1 if there is no container with name <i>containerName</i>
     */
    public static int destroyCustomContainersAtStringHost(String containerName, String stringHost, Location dropLocation) {
        ContainerInfo containerInfo = CustomItemsPlugin.getInstance().getSet().getContainerInfo(containerName);
        if (containerInfo != null) {
            return CustomItemsPlugin.getInstance().getData().containerManager.destroyCustomContainer(
                    containerInfo.getContainer(), stringHost, dropLocation
            );
        } else {
            return -1;
        }
    }

    /**
     * Registers a new custom crafting recipe at runtime. Returns false when validation fails.
     */
    public static boolean registerCraftingRecipe(KciCraftingRecipe recipe) {
        try {
            CustomItemsPlugin.getInstance().registerCraftingRecipe(recipe);
            return true;
        } catch (ValidationException | ProgrammingValidationException ex) {
            CustomItemsPlugin.getInstance().getLogger().warning("Failed to register crafting recipe: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Returns a snapshot of all custom crafting recipes.
     */
    public static Collection<KciCraftingRecipe> getCraftingRecipes() {
        ItemSet itemSet = CustomItemsPlugin.getInstance().getSet().get();
        Collection<KciCraftingRecipe> recipes = new ArrayList<>(itemSet.craftingRecipes.size());
        for (KciCraftingRecipe recipe : itemSet.craftingRecipes) {
            recipes.add(recipe);
        }
        return recipes;
    }
}
