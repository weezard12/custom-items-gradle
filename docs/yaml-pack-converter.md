# YAML Pack Converter (Plugin Side)

This plugin can build `items.cis.txt` directly from YAML packs placed in
`plugins/CustomItems/<pack folder>`. This is a lightweight, production-ready
path for iterating without the editor. For now, only the fields below are
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
```

Rules:
- `id` can be namespaced (`my:steel_sword`) or use the pack namespace (`steel_sword`).
- `name` is used as the display name.

Field notes:
- `lore` is a list of strings; each entry is a lore line.
- Colors:
  - Use `&` codes (`&a`, `&6`, `&l`, etc.) for legacy colors and formatting.
  - Use hex with `&#RRGGBB` (MC 1.16+) and it will be converted to the `&x&...` format.
- `material` accepts vanilla names like `IRON_SWORD` or namespaced values like `minecraft:iron_sword`.
- If `material` is not a supported `KciItemType`, it uses `OTHER` and requires MC 1.14+.
- `enchantments` supports list entries as strings (`"sharpness:3"`) or maps (`id` + optional `level`).
- `attack_damage` and `attack_speed` are added as attribute modifiers for the main hand.

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
- Only `item.id` and `item.name` are supported right now.

When you are ready, we can add more fields (material, type, lore, etc.) and
extend the converter in small, testable steps.
