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
  id: "my:steel_sword" # required
  name: "Steel Sword"  # required
```

Rules:
- `id` can be namespaced (`my:steel_sword`) or use the pack namespace (`steel_sword`).
- `name` is used as the display name.

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
