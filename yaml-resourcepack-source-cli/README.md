# YAML Resourcepack Source CLI

CLI tool that generates a Minecraft resource pack directly from a Paper plugin's **source** tree.
It uses the existing YAML parser logic from Knokko Custom Items and **does not** generate a CIS file.

## What It Does
- Reads YAML packs from your plugin `src/main/resources`
- Reads textures from `src/main/resources/assets`
- Builds an `ItemSet` using the YAML parser
- Writes a resource pack zip to the output path

## Requirements
- Java 21
- A Paper plugin project with `src/main/resources`

## Build
```powershell
.\gradlew :yaml-resourcepack-source-cli:shadowJar
```

## Run
```powershell
java -jar yaml-resourcepack-source-cli\build\libs\yaml-resourcepack-source-cli-all.jar <pluginRoot> <outputPath> --mc-version 26.2
```

If `<outputPath>` is a directory, `resource-pack.zip` is created inside it.

## Options
- `--mc-version <x.y.z>`
  - Overrides the MC version used by the YAML parser.
  - If omitted, `api-version` is read from `plugin.yml` or `paper-plugin.yml`.
  - Minecraft 26.1.x and 26.2 are supported; 26.2 generates resource-pack format 88.
- `--restrict-to-roots`
  - Only scans packs inside `customitems/` and `custom-items/`.
- `--roots <csv>`
  - Overrides the pack roots (comma-separated).
  - Example: `--roots customitems,custom-items`

## Resource Layout

### Pack roots
By default, packs can be placed in either:
- `src/main/resources/customitems/<pack>/...`
- `src/main/resources/custom-items/<pack>/...`

If `--restrict-to-roots` is not set, the tool also treats **any top-level folder**
in `src/main/resources/` as a pack (except `assets/`).

### Global assets
Assets shared across all packs are read from:
- `src/main/resources/assets/...`
- `src/main/resources/customitems/assets/...`
- `src/main/resources/custom-items/assets/...`

### Pack-local assets
Within a pack, textures are expected in:
- `assets/item/<id>.png`
- `assets/block/<id>.png`
- `assets/projectile/<id>.png`

The YAML parser matches the same rules as the in-plugin YAML pack converter.

## Example

```
my-plugin/
  src/main/resources/
    customitems/
      my_pack/
        pack.yml
        items/
          steel_sword.yml
        assets/
          item/
            steel_sword.png
    assets/
      item/
        shared_icon.png
```

## Notes
- This tool **only** generates the resource pack.
- It does **not** generate `items.cis.txt`.
