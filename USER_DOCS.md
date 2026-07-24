## Minecraft Standalone User docs
Usage:
```
java -jar MinecraftStandalone.jar \[options\]
```
Options:
  --username <name>     Set player name (default: Player#####)
  --version <id>        Choose Minecraft version (default: embedded 26.3)
  --server <address>    Direct connect to server
  --port <port>         Server port (default: 25565)
  --help, -h            Show this help

Examples:
```
  java -jar MinecraftStandalone.jar
  java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org
```
For snapshots the version form is \[version\]-snapshot-\[snapshot\]

For other snapshots, the version form is directly the version snapshot.

Examples for other versions:

```
    java -jar MinecraftStandalone.jar --version 26w14a
```
This launches the 2026 April Fools Day Snapshot.

```
    java -jar MinecraftStandalone.jar --version 26.3-snapshot-5
```
This launches the 5th snapshot of an upcoming release.


Newer versions may include support for custom client.jar's, but for now these are the only ways to launch a version.

Find an available version at [MOJANG VERSION MANIFEST](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json).

All versions are fetched from Mojang Version manifest.

COMING SOON: OneMC, a companion, a way to launch a client with all assets built in.
