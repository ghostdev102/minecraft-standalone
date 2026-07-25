package launcher;

import launcher.assets.AssetManager;
import launcher.auth.OfflineAuthManager;
import launcher.launch.LaunchProfile;
import launcher.launch.MinecraftLauncher;
import launcher.libraries.LibraryManager;
import launcher.mods.ModManager;
import launcher.natives.NativeManager;
import launcher.util.LauncherConfig;
import launcher.util.OsUtil;
import launcher.util.VersionJson;
import launcher.versions.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String[] SPLASHES = {
        "Also try Terraria!",
        "Watch out for creepers!",
        "Digging deeper...",
        "Now with extra diamonds!",
        "Powered by Java 25",
        "1.3MB of pure launcher",
        "No Electron here!",
        "The launcher was a success!",
        "Minecraft Standalone Edition",
        "Cracked but classy",
        "Don't dig straight down!",
        "Remember to sleep",
        "Also try Vintagestory!",
        "Not affiliated with Mojang AB",
        "Super Beta",
    };

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0])))) {
            printHelp();
            System.exit(0);
        }

        if (args.length == 1 && "--list-versions".equals(args[0])) {
            Path mcDir = Path.of(OsUtil.mcDir());
            VersionManager vm = new VersionManager(mcDir);
            try { vm.extractAll(mcDir); } catch (Exception ignored) {}
            List<String> versions = vm.discoverVersions();
            if (versions.isEmpty()) {
                try {
                    vm.fetchVersionManifest();
                    versions = vm.discoverVersions();
                } catch (Exception e) {
                    System.err.println("No versions found");
                    System.exit(1);
                }
            }
            versions.forEach(System.out::println);
            System.exit(0);
        }

        if (args.length >= 1 && "--unpin".equals(args[0])) {
            try {
                Path home = Path.of(System.getProperty("user.home"));
                Path jarDir = home.resolve(".local/share/MinecraftStandalone");
                Path jarFile = jarDir.resolve("MinecraftStandalone.jar");
                Path binDir = home.resolve(".local/bin");
                Path wrapper = binDir.resolve("mc");

                Path source = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                Files.createDirectories(jarDir);
                Files.copy(source, jarFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                Files.createDirectories(binDir);
                String script = "#!/usr/bin/env bash\nexec java -jar \"" + jarFile.toAbsolutePath() + "\" \"$@\"\n";
                Files.writeString(wrapper, script);
                wrapper.toFile().setExecutable(true);

                System.out.println("Pinned to " + wrapper);
                System.out.println("Add to PATH if not already:");
                System.out.println("  echo 'export PATH=\"$HOME/.local/bin:$PATH\"' >> ~/.zshrc");
                System.out.println("  source ~/.zshrc");
                System.out.println("Then: mc --username Notch");
            } catch (Exception e) {
                System.err.println("Failed to unpin: " + e.getMessage());
                System.exit(1);
            }
            System.exit(0);
        }

        Path mcDir = Path.of(OsUtil.mcDir());

        LauncherConfig config = new LauncherConfig(mcDir);

        String versionId = config.get("version", null);
        String username = config.get("username", "Player" + ThreadLocalRandom.current().nextInt(10000, 99999));
        String serverAddress = config.get("server", null);
        int serverPort = 25565;
        boolean setupMode = false;
        boolean noSounds = Boolean.parseBoolean(config.get("no-sounds", "false"));
        boolean modForge = false;
        boolean modNeoForge = false;
        String addModsLoader = null;
        List<Path> modJars = new ArrayList<>();
        String ramOverride = config.get("ram", null);
        String resOverride = config.get("resolution", null);
        boolean dryRun = false;
        boolean verbose = false;
        String skinPath = null;
        String capePath = null;
        String resourcePackPath = null;
        String resourcePackRm = null;
        String shaderPath = null;
        String optifineJarPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username" -> { if (i + 1 < args.length) username = args[++i]; }
                case "--version"  -> { if (i + 1 < args.length) versionId = args[++i]; }
                case "--server"   -> { if (i + 1 < args.length) serverAddress = args[++i]; }
                case "--port"     -> { if (i + 1 < args.length) { try { serverPort = Integer.parseInt(args[++i]); } catch (NumberFormatException ignored) {} } }
                case "--ram"      -> { if (i + 1 < args.length) ramOverride = args[++i]; }
                case "--res"      -> { if (i + 1 < args.length) resOverride = args[++i]; }
                case "--skin" -> { if (i + 1 < args.length) skinPath = args[++i]; }
                case "--cape" -> { if (i + 1 < args.length) capePath = args[++i]; }
                case "--resource-pack" -> { if (i + 1 < args.length) resourcePackPath = args[++i]; }
                case "--resource-pack-rm" -> { if (i + 1 < args.length) resourcePackRm = args[++i]; }
                case "--shader" -> { if (i + 1 < args.length) shaderPath = args[++i]; }
                case "--optifine-jar" -> { if (i + 1 < args.length) optifineJarPath = args[++i]; }
                case "--no-sounds" -> noSounds = true;
                case "--setup"    -> setupMode = true;
                case "--dry-run"  -> dryRun = true;
                case "--verbose"  -> verbose = true;
                case "--mod-forge" -> modForge = true;
                case "--mod-neoforge" -> modNeoForge = true;
                case "--add-mods" -> { if (i + 1 < args.length) addModsLoader = args[++i]; }
                case "--mods"     -> {
                    if (i + 1 < args.length) {
                        String modPath = args[++i];
                        Path p = Path.of(modPath);
                        if (Files.exists(p)) modJars.add(p);
                        else log.warn("Mod not found: {}", modPath);
                    }
                }
            }
        }

        if (verbose) {
            System.setProperty("logback.level", "DEBUG");
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(ch.qos.logback.classic.Level.DEBUG);
        }

        try {
            VersionManager versionManager = new VersionManager(mcDir);
            LibraryManager libraryManager = new LibraryManager(mcDir);
            NativeManager nativeManager = new NativeManager(mcDir);
            AssetManager assetManager = new AssetManager(mcDir);
            assetManager.setNoSounds(noSounds);
            ModManager modManager = new ModManager(mcDir);

            if (setupMode) {
                log.info("Setup mode — downloading version manifest...");
                versionManager.fetchVersionManifest();
                if (versionId == null) {
                    versionId = versionManager.latestRelease();
                    if (versionId == null) {
                        var all = versionManager.discoverVersions();
                        if (!all.isEmpty()) versionId = all.get(all.size() - 1);
                    }
                }
                log.info("Setup complete for version: {}", versionId);
                config.set("version", versionId);
                config.save();
                System.exit(0);
            }

            if (addModsLoader != null) {
                if (versionId == null) {
                    versionManager.discoverVersions();
                    versionId = versionManager.latestRelease();
                }
                if (versionId == null) {
                    System.err.println("No version specified. Use --version <id>");
                    System.exit(1);
                }
                switch (addModsLoader.toLowerCase()) {
                    case "forge" -> {
                        String fv = modManager.findForge(versionId);
                        if (fv == null) { System.err.println("Forge not available for " + versionId); System.exit(1); }
                        modManager.installForge(fv);
                    }
                    case "neoforge" -> {
                        String nv = modManager.findNeoForge(versionId);
                        if (nv == null) { System.err.println("NeoForge not available for " + versionId); System.exit(1); }
                        modManager.installNeoForge(nv);
                    }
                    case "optifine" -> {
                        if (optifineJarPath != null) {
                            Path ofJar = Path.of(optifineJarPath);
                            if (!Files.exists(ofJar)) {
                                System.err.println("OptiFine jar not found: " + optifineJarPath);
                                System.exit(1);
                            }
                            Path dest = mcDir.resolve("libraries/optifine/" + ofJar.getFileName());
                            Files.createDirectories(dest.getParent());
                            Files.copy(ofJar, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            log.info("Copied OptiFine to {}", dest);
                        } else {
                            Path ofJar = modManager.downloadOptiFine(versionId);
                            log.info("OptiFine downloaded: {}", ofJar);
                        }
                    }
                    default -> { System.err.println("Unknown mod loader: " + addModsLoader + " (use forge, neoforge, or optifine)"); System.exit(1); }
                }
                log.info("Mod loader installed. You can now launch with --mod-forge or --mod-neoforge");
                System.exit(0);
            }

            if (resourcePackRm != null) {
                Path rpFile = mcDir.resolve("resourcepacks").resolve(resourcePackRm);
                if (rpFile.getFileName().toString().endsWith(".zip") || Files.exists(rpFile)) {
                    Files.deleteIfExists(rpFile);
                    log.info("Removed resource pack: {}", resourcePackRm);
                } else {
                    // Try with .zip extension
                    Path withZip = rpFile.resolveSibling(rpFile.getFileName() + ".zip");
                    if (Files.exists(withZip)) {
                        Files.delete(withZip);
                        log.info("Removed resource pack: {}.zip", resourcePackRm);
                    } else {
                        log.warn("Resource pack not found: {}", resourcePackRm);
                    }
                }
            }

            if (resourcePackPath != null) {
                resolveFile(resourcePackPath, "resource pack", mcDir, "resourcepacks");
            }

            if (shaderPath != null) {
                resolveFile(shaderPath, "shader", mcDir, "shaderpacks");
            }

            versionManager.extractAll(mcDir);
            versionManager.discoverVersions();

            if (versionId == null) {
                List<String> versions = versionManager.discoverVersions();
                if (versions.contains("26.3")) {
                    versionId = "26.3";
                    log.info("Using embedded version: {}", versionId);
                } else if (!versions.isEmpty()) {
                    versionId = versions.get(versions.size() - 1);
                    log.info("Auto-detected version: {}", versionId);
                } else {
                    try {
                        versionManager.fetchVersionManifest();
                        versionId = versionManager.latestRelease();
                        if (versionId == null) {
                            var all = versionManager.discoverVersions();
                            if (!all.isEmpty()) versionId = all.get(all.size() - 1);
                        }
                    } catch (Exception e) {
                        System.err.println("No versions found and could not fetch manifest");
                        System.exit(1);
                    }
                }
            }

            if (modForge || modNeoForge) {
                String loader = modForge ? "forge" : "neoforge";
                String found = findModdedVersion(versionManager.discoverVersions(), versionId, loader);
                if (found != null) {
                    versionId = found;
                    log.info("Using {} version: {}", loader, versionId);
                } else {
                    log.warn("No {} version found for {}, falling back to vanilla", loader, versionId);
                }
            }

            versionManager.discoverVersions();
            VersionJson resolved = versionManager.resolve(versionId);
            if (resolved == null) {
                System.err.println("Could not resolve version: " + versionId);
                System.exit(1);
            }

            log.info("Downloading client jar...");
            Path clientJar = versionManager.versionJarPath(versionId);
            if (!Files.exists(clientJar) && resolved.downloads != null
                    && resolved.downloads.client != null && resolved.downloads.client.url != null) {
                try {
                    Files.createDirectories(clientJar.getParent());
                    launcher.util.DownloadUtil.download(resolved.downloads.client.url, clientJar);
                } catch (Exception e) {
                    log.warn("Could not download client jar: {}", e.getMessage());
                }
            }

            log.info("Checking libraries...");
            var missing = libraryManager.getMissingLibraries(resolved);
            for (var m : missing) {
                log.info("Downloading {}", m.lib().name);
                try { libraryManager.downloadLibrary(m.path(), m.lib()); } catch (Exception e) {
                    log.warn("Skipping unavailable library: {}", m.lib().name);
                }
            }

            log.info("Preparing natives...");
            try { nativeManager.prepareNatives(resolved); } catch (Exception e) {
                log.warn("Native preparation skipped", e);
            }

            log.info("Preparing assets...");
            if (resolved.assetIndex != null) {
                try { assetManager.downloadIndex(resolved.assetIndex.id, resolved.assetIndex.url); } catch (Exception ignored) {}
            }
            if (resolved.assetIndexId() != null) {
                try { assetManager.prepareAssets(resolved.assetIndexId()); } catch (Exception ignored) {}
            }

            if (resolved.logging != null && resolved.logging.client != null
                    && resolved.logging.client.file != null) {
                try {
                    Path loggingDir = mcDir.resolve("assets/log_configs");
                    Files.createDirectories(loggingDir);
                    Path logConfig = loggingDir.resolve(resolved.logging.client.file.id);
                    if (!Files.exists(logConfig)) {
                        launcher.util.DownloadUtil.download(resolved.logging.client.file.url, logConfig);
                    }
                } catch (Exception e) {
                    log.warn("Logging config not available");
                }
            }

            log.info("Authenticating as {}...", username);
            var auth = new OfflineAuthManager(username);
            var profile = auth.authenticate();

            Path resolvedSkin = null;
            if (skinPath != null) {
                resolvedSkin = resolveSkin(skinPath, username, mcDir);
            }

            List<String> extraArgs = new ArrayList<>();
            if (serverAddress != null) {
                extraArgs.add("--server");
                extraArgs.add(serverAddress);
                extraArgs.add("--port");
                extraArgs.add(String.valueOf(serverPort));
            }
            if (resOverride != null) {
                String[] parts = resOverride.toLowerCase().split("x");
                if (parts.length == 2) {
                    try {
                        extraArgs.add("--width");
                        extraArgs.add(parts[0]);
                        extraArgs.add("--height");
                        extraArgs.add(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }

            for (Path modJar : modJars) {
                modManager.copyMod(modJar);
            }

            LaunchProfile launchProfile = new LaunchProfile(
                    profile.username(), profile.uuid(), profile.accessToken(),
                    versionId, mcDir.toString(),
                    mcDir.resolve("assets").toString(),
                    resolved.assetIndexId(), profile.userType(),
                    "java", null, extraArgs.isEmpty() ? null : extraArgs
            );

            log.info("Gathering libraries...");
            var classpath = libraryManager.gatherLibraries(resolved);

            // Splash + creeper countdown
            String splash = SPLASHES[ThreadLocalRandom.current().nextInt(SPLASHES.length)];
            printCountdown(splash);

            if (dryRun) {
                MinecraftLauncher mcLaunch = new MinecraftLauncher(
                        resolved, nativeManager,
                        versionManager.versionJarPath(versionId),
                        classpath, ramOverride
                );
                System.out.println("=== DRY RUN ===");
                System.out.println("Version: " + versionId);
                System.out.println("Main class: " + (resolved.mainClass != null ? resolved.mainClass : "net.minecraft.client.main.Main"));
                System.out.println("Classpath: " + String.join(System.getProperty("path.separator"), classpath.stream().map(Path::toString).toList()));
                System.out.println("Command: " + String.join(" ", mcLaunch.buildCommand(launchProfile)));
                System.exit(0);
            }

            log.info("Launching Minecraft...");
            MinecraftLauncher mcLaunch = new MinecraftLauncher(
                    resolved, nativeManager,
                    versionManager.versionJarPath(versionId),
                    classpath, ramOverride
            );

            Process process = mcLaunch.launch(launchProfile);

            // Save config on success
            config.set("username", username);
            config.set("version", versionId);
            if (serverAddress != null) config.set("server", serverAddress);
            if (ramOverride != null) config.set("ram", ramOverride);
            if (resOverride != null) config.set("resolution", resOverride);
            if (noSounds) config.set("no-sounds", "true");
            config.save();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            log.info("Minecraft exited with code {}", exitCode);
            System.exit(exitCode);

        } catch (Exception e) {
            log.error("Launch failed", e);
            System.err.println("Launch failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printCountdown(String splash) {
        // Creeper face
        String BLACK = "\033[30m";
        String GREEN = "\033[32m";
        String RESET = "\033[0m";
        String PIXEL = "██";

        String[] rows = {
            PIXEL + PIXEL + PIXEL + PIXEL + PIXEL + PIXEL + PIXEL + PIXEL,
            PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL,
            PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL,
            PIXEL + PIXEL + PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL + BLACK + PIXEL,
            PIXEL + BLACK + PIXEL + GREEN + PIXEL + GREEN + PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL,
            PIXEL + BLACK + PIXEL + GREEN + PIXEL + GREEN + PIXEL + GREEN + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL,
            PIXEL + BLACK + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL + GREEN + PIXEL + BLACK + PIXEL + BLACK + PIXEL,
        };

        for (String row : rows) {
            System.out.println(BLACK + row + RESET);
        }
        System.out.println();
        System.out.println("  \"" + splash + "\"");
        System.out.println("  Launching in 3...");
        sleep(800);
        System.out.println("  Launching in 2...");
        sleep(800);
        System.out.println("  Launching in 1...");
        sleep(800);
        System.out.println();
    }

    private static Path resolveSkin(String input, String username, Path mcDir) throws IOException {
        return resolveImage(input, username, mcDir, "skins");
    }

    private static Path resolveImage(String input, String username, Path mcDir, String subdir) throws IOException {
        Path dir = mcDir.resolve(subdir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(username + ".png");
        return copyOrDownload(input, subdir, dest);
    }

    private static Path resolveFile(String input, String label, Path mcDir, String subdir) throws IOException {
        Path dir = mcDir.resolve(subdir);
        Files.createDirectories(dir);
        String name = input.contains("/") ? input.substring(input.lastIndexOf('/') + 1) : input;
        Path dest = dir.resolve(name);
        return copyOrDownload(input, label, dest);
    }

    private static Path copyOrDownload(String input, String label, Path dest) throws IOException {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            log.info("Downloading {} from {}", label, input);
            try (InputStream in = URI.create(input).toURL().openStream()) {
                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Path src = Path.of(input);
            if (!Files.exists(src)) {
                log.warn("{} not found: {}", label, src);
                return null;
            }
            Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Saved {} to {}", label, dest);
        return dest;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String findModdedVersion(List<String> versions, String baseVersion, String loader) {
        String suffix = loader.equals("forge") ? "-forge-" : "-neoforge-";
        for (String v : versions) {
            if (v.startsWith(baseVersion) && v.contains(suffix)) return v;
        }
        for (String v : versions) {
            if (v.contains(suffix)) return v;
        }
        return null;
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar MinecraftStandalone.jar [options]");
        System.out.println();
        System.out.println("Launch options:");
        System.out.println("  --username <name>       Player name (default: Player#####)");
        System.out.println("  --version <id>          Minecraft version (default: latest)");
        System.out.println("  --server <address>      Direct connect to server");
        System.out.println("  --port <port>           Server port (default: 25565)");
        System.out.println("  --ram <size>            Max heap for child JVM (e.g. 4G, 2048M)");
        System.out.println("  --res <WxH>             Window resolution (e.g. 1920x1080)");
        System.out.println("  --mod-forge             Use Forge version (if installed)");
        System.out.println("  --mod-neoforge          Use NeoForge version (if installed)");
        System.out.println("  --mods <file.jar>       Copy mod to mods/ before launch");
        System.out.println("  --skin <file|url>       Skin PNG path or URL");
        System.out.println("  --cape <file|url>       Cape PNG path or URL");
        System.out.println("  --resource-pack <z|url> Resource pack zip path or URL");
        System.out.println("  --resource-pack-rm <n>  Remove resource pack by name");
        System.out.println("  --shader <zip|url>      Shader pack zip path or URL");
        System.out.println("  --dry-run               Print the launch command and exit");
        System.out.println("  --verbose               Debug-level logging");
        System.out.println();
        System.out.println("Setup / Mod loader management:");
        System.out.println("  --setup                 Download version manifest and prepare");
        System.out.println("  --add-mods forge|neoforge|optifine  Install Forge/NeoForge/OptiFine");
        System.out.println("  --optifine-jar <file>   Use local OptiFine jar (with --add-mods optifine)");
        System.out.println("  --list-versions         List all installed/available versions");
        System.out.println("  --unpin                 Symlink jar to ~/.local/bin/mc");
        System.out.println("  --help, -h              Show this help");
        System.out.println();
        System.out.println("Config file: ~/.minecraft/launcher.properties");
        System.out.println("  Settings are saved automatically on launch.");
        System.out.println("  CLI flags override config values.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar MinecraftStandalone.jar");
        System.out.println("  java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org");
        System.out.println("  java -jar MinecraftStandalone.jar --setup");
        System.out.println("  java -jar MinecraftStandalone.jar --ram 4G --res 1920x1080");
        System.out.println("  java -jar MinecraftStandalone.jar --add-mods forge --version 1.21.1");
    }
}
