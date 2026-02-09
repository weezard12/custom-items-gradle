# Custom Items API

This plugin exposes a small Java API in `nl.knokko.customitems.plugin.CustomItemsApi` that other plugins can use.

## Identifying custom items by id

For YAML packs, `item.id` is stored as the item alias. Use `getItemId()` to resolve that id from an ItemStack.
If an item has no alias, the API falls back to the internal item name.

```java
@EventHandler(ignoreCancelled = true)
public void onRightClick(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    String itemId = CustomItemsApi.getItemId(event.getItem());
    if (itemId == null) return;

    if (itemId.equals("my:steel_sword")) {
        event.getPlayer().sendMessage(ChatColor.GOLD + "Steel Sword used!");
        // Your custom action here
    }
}
```

## Creating items by id

```java
ItemStack stack = CustomItemsApi.createItemStackById("my:steel_sword", 1);
```

## Helper methods

- `getItemId(ItemStack)` returns the custom id (alias) or internal name.
- `createItemStackById(String, int)` creates by id or internal name.
- `hasItemId(String)` checks whether an item exists.
- `getAllItemIds()` returns all ids (alias when present).

## Custom blocks by id

Custom blocks use namespaced ids in the API (`namespace:block_id`) for YAML-based packs.
Internally, blocks are still stored as internal names (`namespace_block_id`), but API lookup
methods accept both forms for backward compatibility.

```java
@EventHandler(ignoreCancelled = true)
public void onBreak(BlockBreakEvent event) {
    if (CustomItemsApi.hasBlock("my:steel_block")) {
        String id = CustomItemsApi.getBlockId(event.getBlock());
        if ("my:steel_block".equals(id)) {
            event.getPlayer().sendMessage(ChatColor.GREEN + "You broke a steel block");
        }
    }
}
```

```java
CustomItemsApi.placeBlock(targetBlock, "my:steel_block");
```

Block helper methods:
- `hasBlock(String)` accepts namespaced ids and internal names.
- `hasBlockId(String)` alias for id-based block checks.
- `placeBlock(Block, String)` accepts namespaced ids and internal names.
- `getBlockId(Block)` returns namespaced id for YAML blocks, otherwise internal name.
- `getBlockName(Block)` same behavior as `getBlockId(Block)` (kept for compatibility).
- `getBlockInternalName(Block)` always returns the internal storage name.
- `getAllBlockIds()` returns API ids when known, otherwise internal names.

## Custom projectiles

Custom projectiles are identified by internal name. For YAML packs, the internal name is
`<namespace>_<name>` (e.g. `my:arcane_bolt` -> `my_arcane_bolt`). The API methods accept either
the internal name or a namespaced id (they will convert `namespace:name` to `namespace_name`).

```java
if (CustomItemsApi.hasProjectile("my:arcane_bolt")) {
    CustomItemsApi.launchProjectile(player, "my:arcane_bolt");
}
```

Helper methods:
- `hasProjectile(String)` accepts internal names or namespaced ids.
- `launchProjectile(LivingEntity, String)` fires a custom projectile by name or id.
- `getAllProjectileNames()` returns all internal projectile names.
