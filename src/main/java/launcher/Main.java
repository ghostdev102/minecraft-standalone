package launcher;

import launcher.assets.AssetManager;
import launcher.auth.OfflineAuthManager;
import launcher.launch.LaunchProfile;
import launcher.launch.MinecraftLauncher;
import launcher.libraries.LibraryManager;
import launcher.natives.NativeManager;
import launcher.util.OsUtil;
import launcher.util.VersionJson;
import launcher.versions.VersionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0])))) {
            System.out.println("Usage: java -jar MinecraftStandalone.jar [options]");
            System.out.println("Options:");
            System.out.println("  --username <name>     Set player name (default: Player#####)");
            System.out.println("  --version <id>        Choose Minecraft version (default: embedded 26.3)");
            System.out.println("  --server <address>    Direct connect to server");
            System.out.println("  --port <port>         Server port (default: 25565)");
            System.out.println("  --help, -h            Show this help");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  java -jar MinecraftStandalone.jar");
            System.out.println("  java -jar MinecraftStandalone.jar --username Notch --server 2b2t.org");
            System.exit(0);
        }

        String versionId = null;
        String username = "Player" + ThreadLocalRandom.current().nextInt(10000, 99999);
        String serverAddress = null;
        int serverPort = 25565;

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
            }
        }

        try {
            Path mcDir = Path.of(OsUtil.mcDir());

            VersionManager versionManager = new VersionManager(mcDir);
            LibraryManager libraryManager = new LibraryManager(mcDir);
            NativeManager nativeManager = new NativeManager(mcDir);
            AssetManager assetManager = new AssetManager(mcDir);

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

            versionManager.discoverVersions();
            VersionJson resolved = versionManager.resolve(versionId);
            if (resolved == null) {
                System.err.println("Could not resolve version: " + versionId);
                System.exit(1);
            }

            log.info("Downloading client jar...");
            Path clientJar = versionManager.versionJarPath(versionId);
            if (!java.nio.file.Files.exists(clientJar) && resolved.downloads != null
                    && resolved.downloads.client != null && resolved.downloads.client.url != null) {
                try {
                    java.nio.file.Files.createDirectories(clientJar.getParent());
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
                    java.nio.file.Files.createDirectories(loggingDir);
                    Path logConfig = loggingDir.resolve(resolved.logging.client.file.id);
                    if (!java.nio.file.Files.exists(logConfig)) {
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
}
