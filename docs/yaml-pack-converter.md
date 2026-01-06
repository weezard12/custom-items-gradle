# YAML Pack Converter (Plugin Side)

This plugin can build `items.cis.txt` directly from YAML packs placed in
`plugins/CustomItems/<pack folder>`. This is a lightweight, production-ready
path for iterating without the editor. The fields below are currently
supported.

## How it works

- On startup and on `/kci reload`, the plugin scans `plugins/CustomItems/*`.
- Each subfolder is treated as a pack.
- Any `.yml` or `.yaml` file with a top-level `item:` section is parsed.
- The plugin creates a minimal ItemSet and writes `plugins/CustomItems/items.cis.txt`.
- If any YAML errors are found, conversion is skipped and the existing
  `items.cis.txt` (if any) is used.

## Pack namespace

- Default namespace = pack folder name.
- If `pack.yml` exists, `namespace` (or `pack.namespace`) overrides the default.
- Namespace must match `[a-z0-9_]` (same rules as internal item names).

Example `pack.yml`:
```yml
namespace: "my"
```

## Supported item fields (current)

```yml
item:
  id: "my:steel_sword"    # required
  name: "Steel Sword"     # required
  type: "tool"            # simple|tool|armor|food (optional)
  lore:                   # optional
    - "&7A blazing sword that protects its wielder from flames."
    - "&#FF5500Grants &6Fire Resistance &7while held"
  material: "IRON_SWORD"  # optional
  stack_size: 1           # optional
  damage_value: 0         # optional
  unbreakable: true       # optional
  attack_damage: 7        # optional
  attack_speed: 1.6       # optional
  enchantments:           # optional
    - id: "sharpness"
      level: 3
    - "unbreaking:2"
  tool:                   # optional (type: tool)
    max_durability: 500
    entity_hit_durability_loss: 1
    block_break_durability_loss: 1
  armor:                  # optional (type: armor)
    max_durability: 407
    entity_hit_durability_loss: 0
    block_break_durability_loss: 0
    armor_value: 2
    armor_toughness: 0
  food:                   # optional (type: food)
    food_value: 6
    eat_time: 32
```

Rules:
- `id` can be namespaced (`my:steel_sword`) or use the pack namespace (`steel_sword`).
- `name` is used as the display name.

Field notes:
- `lore` is a list of strings; each entry is a lore line.
- Colors:
- Use `&` codes (`&a`, `&6`, `&l`, etc.) for legacy colors and formatting (case-insensitive).
- Use hex with `&#RRGGBB`; on MC 1.16+ it becomes `&x&...`, on older versions it is downgraded to the nearest legacy color.
- `material` accepts vanilla names like `IRON_SWORD` or namespaced values like `minecraft:iron_sword`.
- If `material` is not a supported `KciItemType`, it uses `OTHER` and requires MC 1.14+.
- `type: tool` requires a tool material (`*_SWORD`, `*_AXE`, `*_PICKAXE`, `*_SHOVEL`, `*_HOE`, `SHEARS`, `FISHING_ROD`, `FLINT_AND_STEEL`, `CARROT_STICK`).
- `type: armor` requires an armor material (`*_HELMET`, `*_CHESTPLATE`, `*_LEGGINGS`, `*_BOOTS`).
- `type: food` allows any food-compatible item type or a `VMaterial` (MC 1.14+).
- `stack_size` is supported for `simple` and `food` items only.
- `enchantments` supports list entries as strings (`"sharpness:3"`) or maps (`id` + optional `level`).
- `attack_damage` and `attack_speed` are added as attribute modifiers for the main hand.
- `type` controls which item class is used. If `type` is omitted, it defaults to `simple` unless a single
  type block (`tool`, `armor`, or `food`) is present.
- `tool` and `armor` blocks control durability defaults. If omitted, vanilla defaults are used based on `material`.
- `food` block controls custom food value and eat time.
- `armor.armor_value` and `armor.armor_toughness` are applied as armor attribute modifiers on the slot implied
  by `material` (helmet/head, chestplate/chest, leggings/legs, boots/feet).
- If `armor.armor_value` or `armor.armor_toughness` are omitted, vanilla defaults are applied for the chosen armor.

Internal behavior:
- Internal item name = `namespace_name` (for safe compatibility).
- Item alias = `namespace:name` (so `/kci give namespace:name` works).

## Example pack layout

```
plugins/CustomItems/
  my_pack/
    pack.yml
    items/
      steel_sword.yml
```

```yml
# items/steel_sword.yml
item:
  id: "steel_sword"
  name: "Steel Sword"
```

## Notes and limitations

- This path does not generate a resource pack.
- A placeholder texture is used internally so the ItemSet validates.
- `unbreakable` applies to tool/armor durability; it has no effect on simple or food items.
