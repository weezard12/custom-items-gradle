## Add Minor Version Support
This guide covers adding support for a new **minor/patch** Minecraft version (for example, 1.21.11)
without changing the YAML/CIS syntax.

### 1) Update Version Constants
Edit `shared-code/src/main/java/nl/knokko/customitems/MCVersions.java`:
- Add the new patch constant (e.g. `VERSION1_21_11`).
- Update `LAST_VERSION` to the new patch.
- Update `SUPPORTED_VERSIONS` so editor exports and validation use real releases
  instead of integer ranges.
- If the patch changes resource pack format or model layout, add logic in:
  - `normalizeLowerBound(...)`
  - `normalizeUpperBound(...)`
  - `latestPatchForMinor(...)`

### 2) Update Version Gates in Enums
For items/blocks/enchantments added in this patch, set the correct first version:
- `shared-code/src/main/java/nl/knokko/customitems/item/VMaterial.java`
- `shared-code/src/main/java/nl/knokko/customitems/item/KciItemType.java`
- `shared-code/src/main/java/nl/knokko/customitems/item/enchantment/VEnchantmentType.java`
- And any other `V*` enums (sounds, particles, biomes, blocks) that changed in this patch.

Tip: for new items, update both `VMaterial` and `KciItemType` if they are usable as item types.

### 3) Update Resource Pack Format / Models (if needed)
If the patch changes pack format or item model layout:
- Plugin runtime:
  - `plug-in/src/main/java/nl/knokko/customitems/plugin/resourcepack/ResourcepackVersionHelper.java`
    - Update pack format mapping for the new patch.
    - Update any model structure switches (modern vs legacy).
- Editor export:
  - `editor/src/main/java/nl/knokko/customitems/editor/resourcepack/ResourcepackGenerator.java`
    - Update pack format selection for the new patch.

### 4) Update NMS Module (if CraftBukkit revision changed)
If the server package changes (e.g. `v1_21_R6` -> `v1_21_R7`):
- Add a new NMS module (or update an existing one).
- Update `settings.gradle` and `build.gradle` to include the module.
- Ensure the plugin selects the correct implementation.

Minecraft 26.1+ uses the unversioned CraftBukkit package, so new NMS modules can
declare `CRAFT_ITEM_STACK_CLASS_NAMES` instead of `NMS_VERSION_STRING(S)`.

### 5) Update YAML Parser Aliases (if needed)
If new materials or renamed materials need aliases:
- `plug-in/src/main/java/nl/knokko/customitems/plugin/yaml/YamlItemReader.java`

### 6) Build & Smoke Test
Run:
```
./gradlew :plug-in:shadowJar
```
Then test on a server for the new patch and at least one previous patch (e.g. 1.21.10):
- `/kci reload`
- `/kci list items`
- Give and place a new item/block added in this patch.
- Confirm resource pack output is correct and applied.

### Checklist
- [ ] Added patch constant and updated `LAST_VERSION` in `MCVersions`
- [ ] Added the release to `SUPPORTED_VERSIONS`
- [ ] Updated new items/blocks/enchantments in `VMaterial`, `KciItemType`, `VEnchantmentType`
- [ ] Updated resource pack format logic (editor + plugin) if needed
- [ ] Updated/added NMS module if CraftBukkit revision changed
- [ ] Adjusted YAML aliases if names changed
- [ ] `./gradlew :plug-in:shadowJar` passes
