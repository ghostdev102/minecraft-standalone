#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "=============================================="
echo "  MinecraftStandalone Launcher - Build Script"
echo "=============================================="
echo ""
# Creeper face — Mojang creeps you off lol
BLACK="\033[30m"
GREEN="\033[32m"
RESET="\033[0m"
PIXEL="██"
printf "${BLACK}${PIXEL}${PIXEL}${PIXEL}${PIXEL}${PIXEL}${PIXEL}${PIXEL}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${PIXEL}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
printf "${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${GREEN}${PIXEL}${BLACK}${PIXEL}${BLACK}${PIXEL}${RESET}\n"
echo ""
printf '\033[41m\033[97m  ⚠️  DMCA WARNING — Mojang creeps you off lol  \033[0m\n'
printf '\033[41m\033[97m  Not affiliated with Mojang AB. "Minecraft" is  \033[0m\n'
printf '\033[41m\033[97m  a trademark of Mojang AB. Users MUST own a    \033[0m\n'
printf '\033[41m\033[97m  legitimate Minecraft account. Distribution of  \033[0m\n'
printf '\033[41m\033[97m  copyrighted assets may violate Mojang EULA /   \033[0m\n'
printf '\033[41m\033[97m  DMCA. Use at own risk.                        \033[0m\n'
echo ""


if ! command -v java &>/dev/null; then
    echo "ERROR: Java 21+ not found."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
if [ "$JAVA_VER" -lt 21 ]; then
    echo "ERROR: Java 21+ required, found $JAVA_VER"
    exit 1
fi
echo "[OK] Java $JAVA_VER"

EMBEDDED_DIR="src/main/resources/embedded"
if [ -d "$EMBEDDED_DIR" ]; then
    echo "[SKIP] Embedded data exists — delete it to re-pack: rm -rf $EMBEDDED_DIR"
elif [ -f pack-embedded.py ] && [ -f clientjar/version.json ]; then
    echo ""
    echo "==> Packing embedded data..."
    python3 pack-embedded.py || echo "[WARN] pack-embedded.py failed"
else
    echo "[INFO] No embedded data; launcher will download at runtime"
fi

echo ""
echo "==> Building with Gradle..."
chmod +x gradlew
./gradlew shadowJar

JAR=$(find build/libs -name "MinecraftStandalone.jar" -type f 2>/dev/null | head -1)
if [ -n "$JAR" ]; then
    echo "  Output: $JAR ($(du -h "$JAR" | cut -f1))"
    echo "  Run: java -jar $JAR"
    echo "  CMD: java -jar $JAR --server mc.example.com --port 25565"
else
    echo "  WARN: MinecraftStandalone.jar not found"
fi
