# MinecraftStandalone Launcher — Dev Docs

> ⚠️ **DMCA / Legal Notice**
> This project is **not affiliated with Mojang AB, Microsoft, or Mojang Studios**.
> "Minecraft" is a registered trademark of Mojang AB.
> This launcher downloads game assets from Mojang's public CDN — users **must**
> own a legitimate Minecraft account to use those assets. Distribution of the
> Minecraft client jar (`client.jar`) or any copyrighted game assets via this
> project's embedded data feature may violate the **Mojang EULA / DMCA**.
> **Use at your own risk. The authors assume no liability.**

## Overview

CLI launcher that downloads Minecraft + dependencies at runtime and launches
the game directly. No JavaFX GUI. Cracked (offline) auth only.

```
java -jar MinecraftStandalone.jar
java -jar MinecraftStandalone.jar --username Notch
java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org
java -jar MinecraftStandalone.jar --version 1.21.1
java -jar MinecraftStandalone.jar --help
```

![demo](demo.mp4)

## How it works

1. `VersionManager.extractAll()` — tries to extract embedded resources
   from classpath (`/embedded/version.json`, `/embedded/client.bin`, etc.).
   If empty (default), skips silently.
2. `discoverVersions()` — scans `~/.minecraft/versions/` for existing
   version metadata JSONs.
3. If none found, `fetchVersionManifest()` downloads the Mojang version
   manifest and all version JSONs.
4. Latest release version is auto-selected (or `--version` override).
5. Client jar, libraries, natives, and assets are downloaded on demand
   to `~/.minecraft/`.
6. JVM process is spawned with the correct classpath, natives directory,
   and LWJGL arguments.

## Project structure

```
src/main/java/launcher/
    Main.java              — entry point, CLI arg parsing, orchestration
    assets/
        AssetManager.java  — asset index + object downloads
    auth/
        AuthManager.java   — AuthProfile record + AuthException
        OfflineAuthManager — offline UUID generation
    launch/
        LaunchProfile.java — immutable profile holding all launch params
        MinecraftLauncher  — builds JVM command line, spawns process
    libraries/
        LibraryManager.java— platform filtering, download, classpath gathering
    natives/
        NativeManager.java — native jar extraction to temp dir
    util/
        DownloadUtil.java  — HTTP GET → file (Java HttpClient)
        OsUtil.java        — OS detection, .minecraft dir, arch
        VersionJson.java   — GSON model for version JSON (Library, Rule, etc.)
    versions/
        VersionManager.java— manifest fetch, version resolution, inheritance merge
src/main/resources/
    logback.xml
    embedded/              — optional: pre-packed client.jar + libs + assets
pack-embedded.py           — packs a Minecraft installation into embedded/
build.sh                   — invokes pack-embedded.py + gradlew shadowJar
build.gradle               — Shadow plugin → fat jar
```

## Building

```bash
./build.sh
# or just:
./gradlew shadowJar
```

Output: `build/libs/MinecraftStandalone.jar`

### Packing embedded data (optional)

Place a Minecraft client jar + version.json in `clientjar/`:

```
clientjar/
    version.json     # version metadata (from .minecraft/versions/<id>/<id>.json)
    client.jar       # the actual game jar
```

Then run `python3 pack-embedded.py`. This copies the client jar, all
libraries, and all assets into `src/main/resources/embedded/`. The launcher
extracts them at runtime instead of downloading.

## Dependencies

| Artifact | Purpose |
|----------|---------|
| slf4j-api 2.0.16 | Logging facade |
| logback-classic 1.5.13 | Logging backend |
| gson 2.11.0 | JSON parsing (version metadata, asset indexes) |

## "Cracked" auth

`OfflineAuthManager` creates a UUID from `OfflinePlayer:<username>` and
returns a dummy access token. No Microsoft/Mojang authentication is
performed. This is sufficient for offline play or cracked servers.

## Version inheritance

Minecraft version JSONs can inherit from a parent (e.g. `1.21.1` inherits
from `1.21`). `VersionManager.resolve()` follows the `inheritsFrom` chain,
merging fields from parent into child. Child fields take priority.

## Common issues

**`ClassNotFoundException: net.minecraft.client.main.Main`**
→ Client jar not downloaded. Check `~/.minecraft/versions/<id>/<id>.jar`.

**`FileAlreadyExistsException` on natives**
→ Fixed in current build (uses `REPLACE_EXISTING`).

**Old version auto-selected**
→ Fixed in current build (picks latest `"type":"release"` from manifest).
