# Minecraft 26.3 support

Build the Paper plugin using Java 25 (with a Java 21 toolchain available):

```powershell
.\gradlew "-PonlyVersions=26.3" :plug-in:shadowJar
```

The output is `plug-in/build/libs/plug-in-all.jar`. `26.3.0` is also accepted.
The existing 26.x bridge continues to serve 26.1.x and 26.2; it now compiles
against Paper API `26.3.build.200-stable`. The Docker test server and the Paper
download list use the same build.

YAML items, blocks, conditions, and both resource-pack CLIs accept 26.3.
The new materials include poplar wood, leaves, cushions, straw beds, colored
wool/concrete stairs and slabs, and explorer maps. New blocks, entities,
biomes, particles, and sounds are also available. Existing enum entries retain
their positions for compatibility with saved item sets, and 26.2 additions
remain usable in 26.3.
New vanilla fuels and poplar log/leaf ingredients are recognized by cooking
recipe validation.

Both the editor and the YAML exporter write resource-pack format 97 with
`min_format` and `max_format` set to `[97, 1]`. For custom item, block, and
projectile models exported to 26.3, `shade: false` becomes
`shade_direction_override: "up"`; older exports retain their original model
format. The input model files are not modified. NBT-API is updated to 2.16.1
for 26.3 item-stack compatibility.

Default mushroom models now live in `shared-code` resources so YAML custom
block packs work in the plugin and both executable CLIs as well as the editor.
The shared compatibility modules compile against public Spigot APIs; minimum
world height is read when available and defaults to zero on pre-1.17 servers.

## Upstream audit

On October 17, 2026, upstream `knokko/custom-items-gradle` master was checked
at `567aea350c7e4387b0147b6a8b7d42c0d0e2f0f1` (14.10). That commit is already
an ancestor of this fork through merge `a74e84e1`, so no additional released
upstream fixes were missing. This includes the 1.12 recipe fixes, world
generation and Purpur fixes, and recent Skript compatibility fixes.

## Validation

- Built the plugin, editor, and both resource-pack CLI JARs.
- Passed 73 tests across shared code, the plugin, the editor, and YAML CLIs.
- Exercised both executable CLIs with items and blocks targeting 26.1.2,
  26.2, and 26.3, including the default 26.3 target.
- Ran an isolated Paper 26.3 build 200 server on Java 25: plugin startup,
  embedded YAML item/block import, resource-pack generation, item creation
  and NBT lookup, custom-block placement and lookup, reload, world height,
  and biome/sound lookup succeeded. New material stack sizes, fuels, and
  cooking ingredients were checked against the running server.
- Confirmed that existing material, block, entity, biome, and particle enum
  entries retain their positions.

Client-side visual rendering and optional third-party plugin integrations
were not exercised in this check.

## References

- [Minecraft 26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)
- [Paper 26.3 API](https://jd.papermc.io/paper/26.3/)
- [NBT-API 2.16.1 release](https://github.com/tr7zw/Item-NBT-API/releases/tag/2.16.1)
- [Upstream master audited commit](https://github.com/knokko/custom-items-gradle/commit/567aea350c7e4387b0147b6a8b7d42c0d0e2f0f1)
