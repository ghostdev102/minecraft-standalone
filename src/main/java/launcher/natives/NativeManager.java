package launcher.natives;

import launcher.util.VersionJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NativeManager {

    private static final Logger log = LoggerFactory.getLogger(NativeManager.class);

    private final Path mcDir;
    private Path nativesDir;

    public NativeManager(Path mcDir) {
        this.mcDir = mcDir;
    }

    public Path nativesDir() {
        if (nativesDir == null) {
            nativesDir = mcDir.resolve("natives/" + System.currentTimeMillis());
        }
        return nativesDir;
    }

    public void prepareNatives(VersionJson resolved) throws IOException {
        if (resolved.libraries == null) return;

        Path dir = nativesDir();
        if (Files.exists(dir)) {
            try (var files = Files.list(dir)) {
                if (files.findAny().isPresent()) return;
            }
        }
        Files.createDirectories(dir);

        for (VersionJson.Library lib : resolved.libraries) {
            // Legacy format: natives map
            if (lib.natives != null && !lib.natives.isEmpty()) {
                String classifier = lib.natives.get(osKey());
                if (classifier == null) continue;
                classifier = classifier.replace("${arch}", archNumber());
                Path nativeJar = resolveNativeJar(lib, classifier);
                if (Files.exists(nativeJar)) {
                    extractNatives(nativeJar, dir, lib.extract != null ? lib.extract.exclude : null);
                }
                continue;
            }
            // Modern format: classifier in name (e.g. natives-macos-arm64)
            String[] parts = lib.name.split(":");
            if (parts.length >= 4 && (parts[3].startsWith("natives-") || parts[3].startsWith("natives_"))) {
                Path nativeJar = resolveNativeJarModern(lib);
                if (Files.exists(nativeJar)) {
                    extractNatives(nativeJar, dir, lib.extract != null ? lib.extract.exclude : null);
                }
            }
        }
    }

    private Path resolveNativeJar(VersionJson.Library lib, String classifier) {
        String[] parts = lib.name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        return mcDir.resolve("libraries/" + group + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + "-" + classifier + ".jar");
    }

    private Path resolveNativeJarModern(VersionJson.Library lib) {
        if (lib.downloads != null && lib.downloads.artifact != null && lib.downloads.artifact.path != null) {
            return mcDir.resolve("libraries/" + lib.downloads.artifact.path);
        }
        String[] parts = lib.name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : "";
        return mcDir.resolve("libraries/" + group + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + "-" + classifier + ".jar");
    }

    private void extractNatives(Path jarFile, Path destDir, java.util.List<String> excludes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\")) continue;
                if (excludes != null && excludes.stream().anyMatch(name::startsWith)) continue;
                if (!isNativeFile(name)) continue;

                Path out = destDir.resolve(name);
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isNativeFile(String name) {
        return name.endsWith(".so") || name.endsWith(".dll")
                || name.endsWith(".dylib") || name.endsWith(".jnilib");
    }

    private String osKey() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private String archNumber() {
        String arch = System.getProperty("os.arch");
        if (arch.contains("64") || arch.equals("amd64") || arch.contains("aarch64")) return "64";
        return "32";
    }
}
