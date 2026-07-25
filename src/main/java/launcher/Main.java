package launcher;

import launcher.assets.AssetManager;
import launcher.auth.OfflineAuthManager;
import launcher.launch.LaunchProfile;
import launcher.launch.MinecraftLauncher;
import launcher.libraries.LibraryManager;
import launcher.mods.ModManager;
import launcher.natives.NativeManager;
import launcher.util.OsUtil;
import launcher.util.VersionJson;
import launcher.versions.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0])))) {
            printHelp();
            System.exit(0);
        }

        String versionId = null;
        String username = "Player" + ThreadLocalRandom.current().nextInt(10000, 99999);
        String serverAddress = null;
        int serverPort = 25565;
        boolean setupMode = false;
        boolean noSounds = false;
        boolean modForge = false;
        boolean modNeoForge = false;
        String addModsLoader = null;
        List<Path> modJars = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--username" -> {
                    if (i + 1 < args.length) username = args[++i];
                }
                case "--version" -> {
                    if (i + 1 < args.length) versionId = args[++i];
                }
                case "--server" -> {
                    if (i + 1 < args.length) serverAddress = args[++i];
                }
                case "--port" -> {
                    if (i + 1 < args.length) {
                        try { serverPort = Integer.parseInt(args[++i]); } catch (NumberFormatException ignored) {}
                    }
                }
                case "--no-sounds" -> noSounds = true;
                case "--setup" -> setupMode = true;
                case "--mod-forge" -> modForge = true;
                case "--mod-neoforge" -> modNeoForge = true;
                case "--add-mods" -> {
                    if (i + 1 < args.length) addModsLoader = args[++i];
                }
                case "--mods" -> {
                    if (i + 1 < args.length) {
                        String modPath = args[++i];
                        Path p = Path.of(modPath);
                        if (Files.exists(p)) {
                            modJars.add(p);
                        } else {
                            log.warn("Mod not found: {}", modPath);
                        }
                    }
                }
            }
        }

        try {
            Path mcDir = Path.of(OsUtil.mcDir());

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
                        if (fv == null) {
                            System.err.println("Forge not available for " + versionId);
                            System.exit(1);
                        }
                        modManager.installForge(fv);
                    }
                    case "neoforge" -> {
                        String nv = modManager.findNeoForge(versionId);
                        if (nv == null) {
                            System.err.println("NeoForge not available for " + versionId);
                            System.exit(1);
                        }
                        modManager.installNeoForge(nv);
                    }
                    default -> {
                        System.err.println("Unknown mod loader: " + addModsLoader + " (use forge or neoforge)");
                        System.exit(1);
                    }
                }

                log.info("Mod loader installed. You can now launch with --mod-forge or --mod-neoforge");
                System.exit(0);
            }

            versionManager.extractAll(mcDir);
            versionManager.discoverVersions();

            if (versionId == null) {
                List<String> versions = versionManager.discoverVersions();
                if (versions.contains("26.3")) {
                    versionId = "26.3";
                    log.info("Using embedded version: {}", versionId);
                } else if (!versions.isEmpty()) {
                    versionId = versions.get(0);
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

            // If --mod-forge or --mod-neoforge, look for a Forge/NeoForge version
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

            List<String> extraArgs = new ArrayList<>();
            if (serverAddress != null) {
                extraArgs.add("--server");
                extraArgs.add(serverAddress);
                extraArgs.add("--port");
                extraArgs.add(String.valueOf(serverPort));
            }

            // Copy mod jars to mods folder
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

            log.info("Launching Minecraft...");
            MinecraftLauncher mcLaunch = new MinecraftLauncher(
                    resolved, nativeManager,
                    versionManager.versionJarPath(versionId),
                    classpath
            );

            Process process = mcLaunch.launch(launchProfile);

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

    private static String findModdedVersion(List<String> versions, String baseVersion, String loader) {
        // Look for something like "1.21-forge-51.0.33" or "1.21.4-neoforge-21.4.xxx"
        String suffix = loader.equals("forge") ? "-forge-" : "-neoforge-";
        for (String v : versions) {
            if (v.startsWith(baseVersion) && v.contains(suffix)) {
                return v;
            }
        }
        // Broader match: any version with the loader name
        String altSuffix = loader.equals("forge") ? "-neoforge-" : "-forge-";
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
        System.out.println("  --version <id>          Minecraft version (default: latest release)");
        System.out.println("  --server <address>      Direct connect to server");
        System.out.println("  --port <port>           Server port (default: 25565)");
        System.out.println("  --mod-forge             Use Forge version (if installed)");
        System.out.println("  --mod-neoforge          Use NeoForge version (if installed)");
        System.out.println("  --mods <file.jar>       Copy mod jar to mods/ folder before launch");
        System.out.println("  --no-sounds             Skip downloading sound assets (.ogg/.wav)");
        System.out.println();
        System.out.println("Setup / Mod loader management:");
        System.out.println("  --setup                 Download version manifest and prepare");
        System.out.println("  --add-mods forge|neoforge  Install Forge/NeoForge for current version");
        System.out.println("  --help, -h              Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar MinecraftStandalone.jar");
        System.out.println("  java -jar MinecraftStandalone.jar --setup");
        System.out.println("  java -jar MinecraftStandalone.jar --add-mods forge --version 1.21.1");
        System.out.println("  java -jar MinecraftStandalone.jar --mod-forge");
        System.out.println("  java -jar MinecraftStandalone.jar --mods mymod.jar");
        System.out.println("  java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org");
    }
}
