# BuildTools helper script

This repo includes `scripts/install-spigot.ps1` to run BuildTools for all
Minecraft versions referenced in `build.gradle`.

## Quick start
1. Put `BuildTools.jar` in the repo root or pass `-BuildToolsPath`.
2. Install JDKs and set `JAVA*_HOME`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-jdks.ps1 -Persist
```

3. Run the script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-spigot.ps1
```

## One-step build check + jar
If you want a single entry point that checks prerequisites and then builds the
shaded jar, use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-plugin.ps1
```

## Dev profile for selected versions
If you only want to build a subset of NMS modules, pass `-PonlyVersions` to
Gradle or `-OnlyVersions` to the build script:

```powershell
./gradlew "-PonlyVersions=1.21.11" :plug-in:shadowJar
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-plugin.ps1 -OnlyVersions 1.21.11
```

For Minecraft 26.1.2, use Java 25:

```powershell
./gradlew "-PonlyVersions=26.1.2" :plug-in:shadowJar
```

By default this excludes `ce-event-handler` and `test-custom-recipes`. If you
need them, add `-PincludeEventHandler=true` and/or `-PincludeTests=true` when
running Gradle, or pass `-IncludeEventHandler` and/or `-IncludeTests` to
`scripts/build-plugin.ps1`.

## Options
- `-BuildToolsPath`: Path to `BuildTools.jar` or `BuildTools.exe`.
- `-Versions`: Explicit list of versions, for example:
  `-Versions 1.12.2,1.13.2,1.14.4`.
- `-JavaExe`: Force a single Java executable for all versions.
- `-SkipIfPresent`: Skip versions that already exist in `~/.m2`.
- `-WorkDir`: Directory where BuildTools should run (default: repo root).

## JDK setup options
`scripts/install-jdks.ps1` installs Temurin JDKs and sets `JAVA*_HOME`.
- `-JdkVersions`: List of JDK majors (default: `8,16,17,21,25`).
- `-Architecture`: `x64` or `aarch64` (auto-detected if omitted).
- `-InstallRoot`: Directory to install JDKs (default: `jdks` in repo root).
- `-Persist`: Persist `JAVA*_HOME` in your user profile.
- `-SkipIfPresent`: Skip JDKs that already exist.
- `-Overwrite`: Replace existing JDK folders.

## Notes
- If you do not set the `JAVA*_HOME` environment variables and do not pass
  `-JavaExe`, the script uses `java` from `PATH` for all versions. Some
  versions may fail to build with a single Java version.
- BuildTools will create `Bukkit`, `CraftBukkit`, `Spigot`, and `work`
  directories in the working directory.
