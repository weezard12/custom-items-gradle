# YAML Pack Converter (Plugin Side)

This plugin can build `items.cis.txt` directly from YAML packs placed in
`plugins/CustomItems/<pack folder>`. This is a lightweight, production-ready
path for iterating without the editor. The fields below are currently
supported.

## How it works

- On startup and on `/kci reload`, the plugin scans `plugins/CustomItems/*`.
- Before scanning, it imports embedded packs from other plugins (see below).
- Each subfolder is treated as a pack.
- Any `.yml` or `.yaml` file can be parsed either by explicit top-level definition keys
  (`item:`, `block:`, `recipe:`, `projectile:`, `projectile_cover:`, `projectile-cover:`,
  `ability:`, `abilities:`, `power:`, `powers:`) or by filename keywords (see below).
- You can define multiple entries in a single file by separating documents with `---`.
- The plugin builds an ItemSet and writes `plugins/CustomItems/items.cis.txt`.
- Runtime resource-pack generation can be toggled in `config.yml`.
  When enabled, the plugin also generates `plugins/CustomItems/resource-pack.zip`.
  When disabled, existing `plugins/CustomItems/resource-pack.zip` is left unchanged.
- Packs with YAML errors are skipped, while valid packs are still converted.
- Duplicate internal ids are warnings, not fatal errors: the first declaration is kept and later duplicates are skipped.
- Packs that fail later conversion/validation are isolated and skipped, so other valid packs still convert.
- If no valid packs remain, conversion is skipped and the existing
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

You can place multiple `item:`, `block:`, `recipe:`, `projectile:`, `projectile_cover:`,
`ability:`, and/or `power:` documents in one YAML file by
separating them with a line that contains only `---`.

```yml
item:
  id: "my:steel_sword"
  name: "Steel Sword"
---
block:
  id: "my:steel_block"
---
projectile_cover:
  id: "my:arcane_cover"
---
projectile:
  id: "my:arcane_bolt"
---
ability:
  id: "my:fire_resist"
  type: "passive_potion_effect"
  effect: "fire_resistance"
---
power:
  id: "my:flame_guard"
  abilities: ["my:fire_resist"]
```

## Implicit type by filename

If a YAML document has no explicit top-level definition key, CustomItems can infer the definition type from the
file name (case-insensitive, extension removed):

- `item`, `items` -> item
- `block`, `blocks` -> block
- `recipe`, `recipes` -> recipe
- `projectile`, `projectiles` -> projectile
- `projectile_cover`, `projectile_covers`, `projectile-cover`, `projectile-covers` -> projectile cover
- `ability`, `abilities` -> ability
- `power`, `powers` -> power

Precedence:
- If a document contains any explicit top-level definition key, explicit parsing is used.
- Filename-based inference is only used when no explicit definition key is present in that document.

Example:

```yml
# items.yml
id: "my:steel_sword"
name: "Steel Sword"
```

```yml
# blocks.yml
id: "my:steel_block"
```

## Embedded packs from other plugins

Other plugins can ship packs inside their jar resources. On startup (and `/kci reload`), CustomItems
copies those packs into `plugins/CustomItems/` before conversion.

Supported resource roots inside plugin jars:
- `customitems/<pack>/...`
- `custom-items/<pack>/...`

Global assets (shared across all packs):
- `assets/...` is copied to `plugins/CustomItems/assets/...`
- `customitems/assets/...` and `custom-items/assets/...` are also supported

Importer config (in `config.yml`):
```yml
Embedded pack importer:
  Enabled: true
  Restrict to pack roots: false
  Pack roots:
    - customitems
    - custom-items
```

Runtime resource-pack config (in `config.yml`):
```yml
Runtime resource pack:
  Generate: true
  Send to players: true
```

Rules:
- A pack is only imported if it contains at least one `.yml/.yaml` file whose first non-empty line
  either starts with one of the explicit definition keys (`item:`, `block:`, `recipe:`, `projectile:`,
  `projectile_cover:`, `projectile-cover:`, `ability:`, `abilities:`, `power:`, `powers:`) or belongs
  to a filename keyword file (`item/items`, `block/blocks`, `recipe/recipes`, `projectile/projectiles`,
  `projectile_cover/projectile_covers/projectile-cover/projectile-covers`, `ability/abilities`, `power/powers`).
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
  type: "tool"            # simple|tool|armor|wand|food|block (optional)
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
  wand:                   # optional (type: wand)
    projectile: "my:arcane_bolt"
    amount_per_shot: 1
    cooldown: 40
    mana_cost: 0
    requires_permission: false
    magic_spells: ["fireball"]
    charges:
      max_charges: 5
      recharge_time: 60
  model:                  # optional custom item model
    json: "models/steel_sword.json"
    textures:
      layer0: "textures/steel_sword.png"
  block: "my:steel_block" # optional (type: block; defaults to same id)
  powers: ["my:flame_guard"]      # optional: grant powers while equipped
  abilities: ["my:fire_resist"]   # optional: auto-wrap abilities into an item-local power
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
- `type: wand` requires a wand-compatible item type (hoes or shears).
- `type: food` allows any food-compatible item type or a `VMaterial` (MC 1.14+).
- `type: block` creates a placeable block item. Use `item.block` to reference the custom block id.
- `stack_size` is supported for `simple`, `food`, and `block` items only.
- `damage_value` locks the internal model data value when set to a positive number.
- `enchantments` supports list entries as strings (`"sharpness:3"`) or maps (`id` + optional `level`).
- `attack_damage` and `attack_speed` are added as attribute modifiers for the main hand.
- `type` controls which item class is used. If `type` is omitted, it defaults to `simple` unless a single
  type block (`tool`, `armor`, `wand`, `food`, or `block`) is present.
- `tool` and `armor` blocks control durability defaults. If omitted, vanilla defaults are used based on `material`.
- `food` block controls custom food value and eat time.
- Runtime behavior for `food` on MC 1.20.5+ uses native item food components.
- This change is runtime-only: no extra YAML fields are required.
- Runtime defaults for native food components currently are:
- `saturation = 0`
- `canAlwaysEat = true` when `food_value < 0` or when the item has `eatEffects`; otherwise `false`
- Existing item stacks created before this runtime change may keep legacy eating behavior until they are recreated or upgraded.
- `wand` block controls projectile, cooldown/amount per shot, charges, mana cost, permissions, and Magic spells.
- Wand items must define `wand.projectile` or `wand.magic_spells`.
- `wand.projectile_id` is an alias for `wand.projectile`.
- `wand.magic_spells` can also be written as `wand.spells`.
- `wand.charges` requires `max_charges` (>1) and `recharge_time` (>0). Omit it to disable charges.
- `wand.cooldown` is in ticks; `wand.amount_per_shot` is the number of projectiles per use.
- `wand.projectile` must reference a custom projectile id defined in the pack.
- `block` creates a placeable block item linked to a custom block. The item model automatically references
  the block model, and the item texture uses the block texture. If `block` is omitted, it defaults to the item id.
- `model` can define a custom item model:
- `item.model.json` is required (`item.model.model` is an alias).
- `item.model.textures` is required and its keys must exist in the model JSON `textures` map.
- If `item.model` is present, it takes precedence over auto-detection.
- If `item.model` is omitted (and the item type is not `block`), the plugin auto-detects
  `plugins/CustomItems/assets/item/<namespace>_<name>.json` (internal name: replace `:` with `_`).
- Auto-detected models read their JSON `textures` map and try to resolve non-`#` texture references to
  global item textures. If a non-vanilla reference can't be resolved, the model is skipped with a warning.
- For namespaced auto-model texture references like `my:steel_sword`, resolution checks both
  `steel_sword.png` and `my_steel_sword.png` in global `assets/item`.
- If auto-detection fails (missing/invalid JSON or unresolved custom textures), conversion continues and the item
  falls back to the normal texture-only model.
- Custom item models still require a regular item texture file (`assets/item/<id>.png` or global fallback).
- `item.model` is ignored for `type: block` items (the linked block controls the model).
- `powers` attaches existing YAML `power` ids to this item. While a player has this custom item in inventory,
  those powers are granted through the Powers plugin.
- `abilities` attaches YAML `ability` ids directly to the item. CustomItems auto-generates a synthetic power
  per item and grants it while the item is in inventory.
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
    default: -1
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
- `mining_speed` values are between `-5` and `25`. Positive values apply Haste, negative values apply Mining Fatigue.
- Use `mining_speed.default` to override the base mushroom-block speed (e.g., set a negative default to slow down all tools).
- `mining_speed.vanilla` entries accept `tool` or `material`; `allow_custom_items` defaults to true.
- `mining_speed.vanilla` and `mining_speed.custom` also accept `speed` as an alias for `value`.
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

## Supported projectile cover fields (current)

Sphere cover example:
```yml
projectile_cover:
  id: "my:arcane_cover"        # required
  type: "sphere"               # sphere|custom (optional, defaults to sphere)
  item_type: "ENDER_PEARL"     # optional (must support projectile covers)
  texture: "arcane_cover"      # required for sphere
  slots_per_axis: 10           # optional (1..50)
  scale: 0.35                  # optional (>0)
  geyser_texture: "arcane_g"   # optional
```

Custom cover example:
```yml
projectile_cover:
  id: "my:crystal_cover"     # required
  type: "custom"
  item_type: "ENDER_PEARL"   # optional
  model:
    json: "models/cover.json"    # required
    textures:                   # required (keys must exist in model JSON)
      layer0: "textures/cover.png"
  geyser_texture: "crystal_g"    # optional
```

Rules:
- `id` can be namespaced (`my:arcane_cover`) or use the pack namespace (`arcane_cover`).
- `projectile_cover.item_type` is optional; if set, it must be a `KciItemType` that supports projectile covers.
- `projectile_cover.type` defaults to `sphere` unless a `model` section is present.
- For sphere covers, `texture` is optional; if omitted it defaults to `<namespace>_<name>` (e.g. `my_arcane_cover.png`).
- `projectile_cover.model.textures` keys must match the texture keys in the model JSON.

## Supported projectile fields (current)

```yml
projectile:
  id: "my:arcane_bolt"                 # required
  requires:
    mc: ">=1.16"
  damage: 6                            # optional
  gravity: 0.02                        # optional
  launch_angle: { min: 0, max: 3 }     # optional (or min_launch_angle/max_launch_angle)
  launch_speed: 1.2                    # optional (or min_launch_speed/max_launch_speed)
  launch_knockback: 0.0                # optional
  impact_knockback: 0.4                # optional
  max_lifetime: 200                    # optional (ticks)
  max_pierced_entities: 1              # optional
  apply_impact_effects_at_expiration: true  # optional
  apply_impact_effects_at_pierce: true      # optional
  cover: "my:arcane_cover"             # optional (or cover_id)
  impact_potion_effects:               # optional
    - "poison:100:2"                   # type:duration[:level]
    - type: "weakness"
      duration: 60
      level: 1
  in_flight_effects:                   # optional
    - delay: 0
      period: 5
      effects:
        - type: "simple_particle"
          particle: "CRIT"
          amount: 4
          min_radius: 0.1
          max_radius: 0.2
  impact_effects:                      # optional
    - type: "explosion"
      power: 1.5
      destroy_blocks: false
    - type: "play_sound"
      sound: "ENTITY_GENERIC_EXPLODE"
      volume: 1.0
      pitch: 1.2
```

Rules:
- `id` can be namespaced (`my:arcane_bolt`) or use the pack namespace (`arcane_bolt`).
- `launch_angle` and `launch_speed` accept a number (fixed value) or `{ min, max }`.
- `custom_damage_source` exists but is currently ignored (warning emitted).

### Projectile effect types

All effects use `type` (or `effect`) and can be used in `impact_effects` or inside `in_flight_effects[*].effects`.

Explosion:
```yml
- type: "explosion"
  power: 2.0              # required
  destroy_blocks: false
  set_fire: false
```

Colored redstone:
```yml
- type: "colored_redstone"
  min_color: "#ff0000"
  max_color: "#ffff00"
  min_radius: 0.1
  max_radius: 0.2
  amount: 8
```

Simple particle:
```yml
- type: "simple_particle"
  particle: "FLAME"   # VParticle name
  min_radius: 0.0
  max_radius: 0.2
  amount: 6
```

Straight/random acceleration:
```yml
- type: "straight_acceleration"   # or random_acceleration
  min: 0.02
  max: 0.05
```

Sub projectiles:
```yml
- type: "sub_projectiles"
  child: "my:arcane_bolt"
  use_parent_lifetime: true
  min_amount: 1
  max_amount: 3
  angle_to_parent: 20
```

Command:
```yml
- type: "command"
  command: "say hit!"
  executor: "console"    # console|shooter (optional, default shooter)
```

Push/pull:
```yml
- type: "push_pull"
  strength: 0.4
  radius: 3.0
```

Play sound:
```yml
- type: "play_sound"
  sound: "ENTITY_ARROW_HIT"
  volume: 1.0
  pitch: 1.0
```

Fireworks:
```yml
- type: "fireworks"
  effects:
    - type: "BALL"
      flicker: true
      trail: true
      colors: ["#ff0000", "#00ff00"]
      fade_colors: ["#0000ff"]
```

Potion aura:
```yml
- type: "potion_aura"
  radius: 3.0
  effects:
    - "slowness:60:1"
```

## Supported ability fields (Powers bridge)

```yml
ability:
  id: "my:fire_resist"                # required
  type: "passive_potion_effect"       # optional, defaults to passive_potion_effect
  name: "Fire Resistance Passive"     # optional
  effect: "fire_resistance"           # required (alias: potion_effect)
  amplifier: 0                        # optional (alias: level)
  duration: 200                       # optional (ticks)
  tick_interval: 20                   # optional (alias: interval)
  ambient: false                      # optional
  particles: true                     # optional
  icon: true                          # optional
  conditions:                         # optional
    - on: "on start hold any hand"
    - on: "on finish hold any hand"
  conditions_default_enabled: false   # optional
```

Rules:
- `id` can be namespaced (`my:fire_resist`) or use the pack namespace (`fire_resist`).
- `abilities:` can be used as a list form of multiple ability maps.
- Ability objects are only used when the Powers plugin is present.
- Current supported type is `passive_potion_effect`.
- `conditions` is an ordered list; multiple rules stack.
- `conditions[*].on`/`trigger` supports:
  `on right click`, `on shift right click`, `on any right click`,
  `on left click`, `on shift left click`, `on any left click`,
  `on start hold hand`, `on start hold off hand`, `on start hold any hand`,
  `on finish hold hand`, `on finish hold off hand`, `on finish hold any hand`,
  `on hold inventory`, `on drop`, `on pick up`.
- `conditions[*].action` supports `enable`, `disable`, `trigger`, `sync`.
  If omitted, default action depends on trigger:
  right/left click -> `trigger`, start hold/pick up -> `enable`,
  finish hold/drop -> `disable`, hold inventory -> `sync`.
- `conditions[*].item` can reference a custom item id to scope the rule to that item.
- For abilities assigned with `item.abilities`, `conditions[*].item` is optional.
  If omitted, item conditions automatically use the item that granted the ability.

## Supported power fields (Powers bridge)

```yml
power:
  id: "my:flame_guard"                # required
  name: "Flame Guard"                 # optional
  abilities: ["my:fire_resist"]       # required (alias: passive_abilities)
  rarity: "Rare"                      # optional (must exist in Powers rarity registry)
  alignment: "LIGHT"                  # optional (NEUTRAL|LIGHT|DARK)
  icon: "flame_guard"                 # optional (alias: icon_key)
  description:                        # optional
    - "Grants constant fire resistance"
```

Rules:
- `id` can be namespaced (`my:flame_guard`) or use the pack namespace (`flame_guard`).
- `powers:` can be used as a list form of multiple power maps.
- Powers are granted to players from custom items through `item.powers`/`item.abilities` while the item is in
  inventory, and automatically removed when the player no longer has the item.
- If the Powers plugin is not installed, these fields are ignored at runtime.

## Textures (runtime resource pack)

If a texture file exists, the plugin automatically maps it to the item or block id and includes it in the
generated resource pack.

Texture paths:
- Items: `plugins/CustomItems/<pack>/assets/item/<id>.png`
- Blocks: `plugins/CustomItems/<pack>/assets/block/<id>.png`
- Projectile covers: `plugins/CustomItems/<pack>/assets/projectile/<id>.png`
- Block items use the linked block texture (item textures are ignored).
- Items also fall back to `plugins/CustomItems/assets/item/<id>.png` if the pack texture is missing.
- Block textures referenced by name also fall back to `plugins/CustomItems/assets/block/<id>.png`.
- Projectile cover textures referenced by name also fall back to `plugins/CustomItems/assets/projectile/<id>.png`.
- For namespaced ids, `<id>` is the part after the colon (e.g. `my:steel_sword` -> `steel_sword.png`).
- You can also use the internal name `<namespace>_<name>.png` (e.g. `my_ruby_axe.png`).
- `model.texture`, `model.textures.*`, and `item.model.textures.*` can point to explicit png paths relative to the pack folder.
- For `item.model.textures.*`, path-like values fall back to `plugins/CustomItems/assets/item/...` when the
  pack-local file is missing.
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
