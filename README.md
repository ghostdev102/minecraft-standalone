# MinecraftStandalone Launcher

A zero-dependency CLI launcher for Minecraft. Drop the jar anywhere, run it,
Minecraft starts. No installers, no GUIs, no accounts required for cracked
servers.

```bash
java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org
```

## Quick start

```bash
# Build
./build.sh

# Launch latest release
java -jar build/libs/MinecraftStandalone.jar

# Launch with a name and direct-connect
java -jar build/libs/MinecraftStandalone.jar --username Steve --server mc.example.com
```

## CLI options

| Flag | Description |
|------|-------------|
| `--username <name>` | Player name (default: `Player#####`) |
| `--version <id>` | Minecraft version (default: latest release) |
| `--server <address>` | Direct connect to server |
| `--port <port>` | Server port (default: 25565) |
| `--mods <file.jar>` | Copy mod jar to `mods/` folder before launch |
| `--setup` | Download version manifest and prepare |
| `--help` | Show full usage |

## Mod loader support (forge-support branch)

The `forge-support` branch adds Forge/NeoForge integration:

```bash
# Install Forge for 1.21.1
java -jar MinecraftStandalone.jar --add-mods forge --version 1.21.1

# Launch with Forge
java -jar MinecraftStandalone.jar --mod-forge

# Install a mod
java -jar MinecraftStandalone.jar --mods my-cool-mod.jar
```

See `DEV_DOCS.md` for architecture details.

---

> ⚠️ Not affiliated with Mojang AB. "Minecraft" is a trademark of Mojang AB.
> Users should own a legitimate Minecraft account. Use at own risk.
