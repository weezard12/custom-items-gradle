package nl.knokko.customitems.plugin.util;

import de.tr7zw.changeme.nbtapi.NBT;
import nl.knokko.customitems.item.KciItem;
import nl.knokko.customitems.nms.KciNms;
import nl.knokko.customitems.plugin.set.ItemSetWrapper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import nl.knokko.customitems.item.VMaterial;
import nl.knokko.customitems.plugin.CustomItemsPlugin;
import nl.knokko.customitems.plugin.container.ContainerInstance;

import java.util.ArrayList;
import java.util.Collection;

import static nl.knokko.customitems.plugin.set.item.CustomItemWrapper.wrap;

public class ItemUtils {

	public enum GiveResult {
		ADDED_TO_INVENTORY,
		DROPPED_ON_GROUND,
		FAILED
	}

	public static boolean isEmpty(ItemStack stack) {
		if(stack == null ||
				KciNms.instance.items.getMaterialName(stack).equals(VMaterial.AIR.name()) ||
				stack.getAmount() == 0) {
			return true;
		}

		return NBT.get(stack, nbt -> NbtHelper.getNested(nbt, ContainerInstance.PLACEHOLDER_KEY, 0) == 1);
	}
	
	public static boolean isCustom(ItemStack stack) {
		return CustomItemsPlugin.getInstance().getSet().getItem(stack) != null;
	}
	
	public static int getMaxStacksize(ItemStack stack) {
		KciItem customItem = CustomItemsPlugin.getInstance().getSet().getItem(stack);
		if (customItem != null) {
			return customItem.getMaxStacksize();
		}
		
		return stack.getMaxStackSize();
	}

	/**
	 * @return A list of ItemStacks that didn't fit in the inventory
	 */
	public static Collection<ItemStack> giveItems(ItemSetWrapper itemSet, Inventory destination, Collection<ItemStack> items) {
		Collection<ItemStack> didNotFit = new ArrayList<>();
		for (ItemStack item : items) {
			KciItem customItem = itemSet.getItem(item);
			if (customItem != null && wrap(customItem).needsStackingHelp()) {
				if (!giveCustomItemToInventory(itemSet, destination, customItem, item.getAmount())) {
					didNotFit.add(item);
				}
			} else {
				didNotFit.addAll(destination.addItem(item).values());
			}
		}

		return didNotFit;
	}

	public static boolean giveCustomItemToInventory(ItemSetWrapper itemSet, Inventory inventory, KciItem item, int amount) {
		boolean wasGiven = false;

		if (wrap(item).needsStackingHelp()) {
			ItemStack[] contents = inventory.getStorageContents();
			int freeSlotIndex = -1;
			for (int index = 0; index < contents.length; index++) {
				if (ItemUtils.isEmpty(contents[index])) {
					if (freeSlotIndex == -1) freeSlotIndex = index;
				} else {
					ItemStack existingStack = contents[index];
					KciItem existingItem = itemSet.getItem(existingStack);
					if (existingItem == item && item.getMaxStacksize() >= existingStack.getAmount() + amount) {
						existingStack.setAmount(existingStack.getAmount() + amount);
						inventory.setStorageContents(contents);
						wasGiven = true;
						break;
					}
				}
			}

			if (freeSlotIndex != -1 && !wasGiven) {
				contents[freeSlotIndex] = wrap(item).create(amount);
				inventory.setStorageContents(contents);
				return true;
			}

			return wasGiven;
		} else {
			return inventory.addItem(wrap(item).create(amount)).isEmpty();
		}
	}

	public static GiveResult giveCustomItem(ItemSetWrapper itemSet, Player player, KciItem item, int amount, boolean dropWhenInventoryIsFull) {
		if (player == null || item == null || amount < 1 || amount > item.getMaxStacksize()) {
			return GiveResult.FAILED;
		}

		if (giveCustomItemToInventory(itemSet, player.getInventory(), item, amount)) {
			return GiveResult.ADDED_TO_INVENTORY;
		}
		if (dropWhenInventoryIsFull) {
			player.getWorld().dropItem(player.getLocation(), wrap(item).create(amount));
			return GiveResult.DROPPED_ON_GROUND;
		}
		return GiveResult.FAILED;
	}

	public static GiveResult giveCustomItem(ItemSetWrapper itemSet, Player player, KciItem item, int amount) {
		CustomItemsPlugin plugin = CustomItemsPlugin.getInstance();
		boolean dropWhenInventoryIsFull = plugin == null || plugin.shouldDropGivenItemsWhenInventoryIsFull();
		return giveCustomItem(itemSet, player, item, amount, dropWhenInventoryIsFull);
	}

	public static void giveCustomItem(ItemSetWrapper itemSet, Player player, KciItem item) {
		giveCustomItem(itemSet, player, item, 1);
	}
}
