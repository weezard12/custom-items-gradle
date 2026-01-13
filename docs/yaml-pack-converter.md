# YAML Pack Converter (Plugin Side)

This plugin can build `items.cis.txt` directly from YAML packs placed in
`plugins/CustomItems/<pack folder>`. This is a lightweight, production-ready
path for iterating without the editor. The fields below are currently
supported.

## How it works

- On startup and on `/kci reload`, the plugin scans `plugins/CustomItems/*`.
- Before scanning, it imports embedded packs from other plugins (see below).
- Each subfolder is treated as a pack.
- Any `.yml` or `.yaml` file with a top-level `item:`, `block:`, or `recipe:` section is parsed.
- You can define multiple items/blocks in a single file by separating documents with `---`.
- The plugin builds an ItemSet, generates `plugins/CustomItems/resource-pack.zip`, and writes
  `plugins/CustomItems/items.cis.txt`.
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

## Multiple entries in one file

You can place multiple `item:`, `block:`, and/or `recipe:` documents in one YAML file by
separating them with a line that contains only `---`.

```yml
item:
  id: "my:steel_sword"
  name: "Steel Sword"
---
block:
  id: "my:steel_block"
```

## Embedded packs from other plugins

Other plugins can ship packs inside their jar resources. On startup (and `/kci reload`), CustomItems
copies those packs into `plugins/CustomItems/` before conversion.

Supported resource roots inside plugin jars:
- `customitems/<pack>/...`
- `custom-items/<pack>/...`

Importer config (in `config.yml`):
```yml
Embedded pack importer:
  Enabled: true
  Restrict to pack roots: false
  Pack roots:
    - customitems
    - custom-items
```

Rules:
- A pack is only imported if it contains at least one `.yml/.yaml` file whose first non-empty line is `item:`, `block:`, or `recipe:`.
- Packs are copied to `plugins/CustomItems/<pack>`.
- If that folder already exists, it is only overwritten when it contains a `.kci-imported.txt`
  marker created by the same plugin. Otherwise it is skipped.
- When `Restrict to pack roots` is `false`, the importer scans all root-level folders in plugin jars
  and ignores the folder names listed under `Pack roots`.

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
- `type: tool` requires a tool material (`*_SWORD`, `*_AXE`, `*_PICKAXE`, `*_SHOVEL`, `*_HOE`, `SHEARS`, `FISHING_ROD`, `FLINT_AND_STEEL`, `CARROT_STICK`, `MACE`).
- `type: armor` requires an armor material (`*_HELMET`, `*_CHESTPLATE`, `*_LEGGINGS`, `*_BOOTS`).
- `type: food` allows any food-compatible item type or a `VMaterial` (MC 1.14+).
- `stack_size` is supported for `simple` and `food` items only.
- `damage_value` locks the internal model data value when set to a positive number.
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

## Supported block fields (current)

```yml
block:
  id: "my:steel_block"    # required
  requires:
    mc: ">=1.16"
  model:
    type: "simple"         # simple|sided|custom
    texture: "steel_block" # optional (defaults to assets/block/<id>.png, or global assets for simple blocks)
  mining_speed:
    default: 0
    vanilla:
      - tool: "DIAMOND_PICKAXE"
        value: 5
        allow_custom_items: true
    custom:
      - item: "my:steel_pickaxe"
        value: 8
  sounds:
    left_click: "BLOCK_STONE_HIT"
    break:
      sound: "BLOCK_STONE_BREAK"
      volume: 1.0
      pitch: 1.0
  drops:
    - outputs:
        - material: "IRON_INGOT"
          amount: 1
          chance: 50
        - item: "my:steel_ingot"
          amount: 1
          chance: 25
      silk_touch: optional   # optional|required|forbidden
      fortune:
        min: 0
        max: 3
      cancel_normal_drops: false
      required_held_items:
        vanilla:
          - material: "DIAMOND_PICKAXE"
            allow_custom_items: true
        custom:
          - "my:steel_pickaxe"
        invert: false
      biomes:
        whitelist: [PLAINS]
        blacklist: [OCEAN]
```

Rules:
- `id` can be namespaced (`my:steel_block`) or use the pack namespace (`steel_block`).
- Internal block name = `namespace_name`.
- `requires.mc` supports operators (`>=`, `<=`, `>`, `<`, `=`) and versions like `1.16` or `1.16.5`.
- `model.type: simple` uses a single texture; `sided` uses `model.textures.north/east/south/west/up/down`.
- `model.type: custom` expects `model.json`, `model.editor_texture`, and `model.textures` (map of model texture keys to png paths).
- `mining_speed` values are between `-5` and `25`.
- `mining_speed.vanilla` entries accept `tool` or `material`; `allow_custom_items` defaults to true.
- `mining_speed.custom.item` uses a custom item id and applies only when that item is held.
- `sounds` supports string shorthand (`"BLOCK_STONE_HIT"`) or map with `sound`, `volume`, `pitch`.
- `drops.outputs` chances are percentages (0..100), decimals allowed.
- `drops.required_held_items` supports `enabled` (default true), `invert` (default false), `vanilla` entries
  (with `material` + `allow_custom_items`) and `custom` entries (list of custom item ids).
- `drops.biomes` supports `whitelist` and `blacklist` lists of biome names; empty lists mean no restriction.

## Supported recipe fields (current)

```yml
recipe:
  id: "my:steel_sword"   # required
  type: "shaped"         # shaped|shapeless
  ignore_displacement: true
  permission: "customitems.recipe.any"
  result:
    item: "my:steel_sword"
    amount: 1
  shape:                # shaped only
    - "ab"
    - " c"
  ingredients:          # shaped map or shapeless list
    a:
      material: "IRON_INGOT"
    b:
      item: "my:steel_ingot"
      amount: 2
    c:
      material: "STICK"
```

Shapeless example:
```yml
recipe:
  id: "my:steel_sword"
  type: "shapeless"
  result: "my:steel_sword"
  ingredients:
    - "IRON_INGOT"
    - "my:steel_ingot"
    - { material: "STICK", amount: 1 }
```

Recipe fields:
- `id` can be namespaced (`my:steel_sword`) or use the pack namespace (`steel_sword`).
- `type` can be omitted if `shape` (shaped) or `ingredients` list (shapeless) is present.
- `ignore_displacement` defaults to `true` (shaped only).
- `permission` (or `required_permission`) controls who can craft this recipe.
- `result` (or `output`) defines the crafted item. You can also use `recipe.item` as shorthand for a custom item.

Ingredient / result definitions:
- Shorthand string:
  - `"IRON_INGOT"` -> vanilla
  - `"my:steel_ingot"` -> custom
- Use uppercase or the `minecraft:` namespace to force vanilla (`"minecraft:iron_ingot"`).
- Map form:
  - `item`: custom item id
  - `material`: vanilla material
  - `data_value`: legacy data value (forces vanilla_data)
  - `mimic`: foreign item id
  - `item_bridge`: ItemBridge id
  - `encoded`: copied item data
  - `amount`: stack size (default 1)
  - `remaining_item`: result definition returned after crafting
  - `constraints`: ingredient constraints (see below)

Ingredient constraints:
```yml
constraints:
  durability:
    - operator: ">="
      percentage: 50
  enchantments:
    - enchantment: "sharpness"
      operator: ">="
      level: 3
  variables:
    - variable: "gem_level"
      operator: ">="
      value: 2
```

Upgrade results:
```yml
result:
  type: "upgrade"
  ingredient_index: 4
  upgrades: ["Reinforced"]
  repair_percentage: 25
  new_type: "my:steel_sword"
  keep_old_upgrades: true
  keep_old_enchantments: true
```

## Textures (runtime resource pack)

If a texture file exists, the plugin automatically maps it to the item or block id and includes it in the
generated resource pack.

Texture paths:
- Items: `plugins/CustomItems/<pack>/assets/item/<id>.png`
- Blocks: `plugins/CustomItems/<pack>/assets/block/<id>.png`
- Simple blocks also fall back to `plugins/CustomItems/assets/block/<id>.png` if the pack texture is missing.
- For namespaced ids, `<id>` is the part after the colon (e.g. `my:steel_sword` -> `steel_sword.png`).
- `model.texture` and `model.textures.*` can also point to explicit png paths relative to the pack folder.
- Textures must be square, power-of-two, and at most 512x512.

## Example pack layout

```
plugins/CustomItems/
  my_pack/
    pack.yml
    items/
      steel_sword.yml
    blocks/
      steel_block.yml
```

```yml
# items/steel_sword.yml
item:
  id: "steel_sword"
  name: "Steel Sword"
```

```yml
# blocks/steel_block.yml
block:
  id: "steel_block"
```

## Notes and limitations

- A placeholder texture is used internally so the ItemSet validates.
- Items without a real texture are skipped in the generated resource pack (they fall back to their vanilla model).
- `unbreakable` applies to tool/armor durability; it has no effect on simple or food items.
- Block textures and runtime block models are generated for `simple` and `sided` models.
- `custom` block models embed textures listed under `model.textures` into the resource pack.
