package nl.knokko.customitems.nms21plus;

import nl.knokko.customitems.item.KciFood;
import nl.knokko.customitems.nms18plus.KciNmsItems18Plus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;

import java.lang.reflect.Method;
import java.util.Collections;

public abstract class KciNmsItems21Plus extends KciNmsItems18Plus {

	private static final boolean HAS_PAPER;

	static {
		boolean hasPaper;
		try {
			Class.forName("net.kyori.adventure.text.Component");
			hasPaper = true;
		} catch (ClassNotFoundException noPaper) {
			hasPaper = false;
			Bukkit.getLogger().warning("CustomItems translations in MC 1.20+ requires papermc");
		}
		HAS_PAPER = hasPaper;
	}

	@Override
	@SuppressWarnings("UnstableApiUsage")
	public void setEquippableAssetID(ItemMeta meta, EquipmentSlot slot, String id) {
		EquippableComponent component = meta.getEquippable();
		component.setModel(new NamespacedKey("minecraft", id));
		component.setSlot(slot);
		meta.setEquippable(component);
	}

	@Override
	public ItemStack translate(ItemStack item, String itemName, boolean translateDisplayName, int loreSize) {
		if (!HAS_PAPER) return item;
		return Translations21Plus.translate(item, itemName, translateDisplayName, loreSize);
	}

	@Override
	public boolean applyNativeFoodProperties(ItemMeta meta, KciFood food) {
		if (meta == null || food == null) return false;

		try {
			FoodComponent component = meta.getFood();
			component.setNutrition(Math.max(0, food.getFoodValue()));
			component.setSaturation(0f);
			component.setCanAlwaysEat(!food.getEatEffects().isEmpty() || food.getFoodValue() < 0);
			meta.setFood(component);
		} catch (Throwable failed) {
			return false;
		}

		// Spigot 1.21 uses a separate consumable component for timing/animation.
		// Paper API doesn't always expose those types directly, so use reflection best-effort.
		applyConsumableProperties(meta, food);
		return true;
	}

	private static void applyConsumableProperties(ItemMeta meta, KciFood food) {
		try {
			Method getConsumable = meta.getClass().getMethod("getConsumable");
			Object consumable = getConsumable.invoke(meta);
			if (consumable == null) return;

			Method setConsumeSeconds = consumable.getClass().getMethod("setConsumeSeconds", float.class);
			setConsumeSeconds.invoke(consumable, food.getEatTime() / 20f);

			try {
				Class<?> animationClass = Class.forName(
						"org.bukkit.inventory.meta.components.consumable.ConsumableComponent$Animation"
				);
				@SuppressWarnings("unchecked")
				Object eatAnimation = Enum.valueOf((Class<? extends Enum>) animationClass.asSubclass(Enum.class), "EAT");
				Method setAnimation = consumable.getClass().getMethod("setAnimation", animationClass);
				setAnimation.invoke(consumable, eatAnimation);
			} catch (Throwable ignored) {
			}

			try {
				Method setEffects = consumable.getClass().getMethod("setEffects", java.util.List.class);
				setEffects.invoke(consumable, Collections.emptyList());
			} catch (Throwable ignored) {
			}

			Method setConsumable = meta.getClass().getMethod("setConsumable", getConsumable.getReturnType());
			setConsumable.invoke(meta, consumable);
		} catch (Throwable ignored) {
		}
	}

	@Override
	public boolean hasNativeFoodProperties(ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR) return false;

		try {
			ItemMeta meta = stack.getItemMeta();
			if (meta == null || !meta.hasFood()) return false;

			Boolean hasConsumable = queryHasConsumable(meta);
			return hasConsumable == null || hasConsumable;
		} catch (Throwable failed) {
			return false;
		}
	}

	private static Boolean queryHasConsumable(ItemMeta meta) {
		try {
			Method hasConsumableMethod = meta.getClass().getMethod("hasConsumable");
			Object rawResult = hasConsumableMethod.invoke(meta);
			if (rawResult instanceof Boolean) return (Boolean) rawResult;
		} catch (Throwable ignored) {
		}
		return null;
	}
}
