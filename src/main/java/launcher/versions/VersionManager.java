package launcher.versions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import launcher.util.DownloadUtil;
import launcher.util.VersionJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class VersionManager {

    private static final Logger log = LoggerFactory.getLogger(VersionManager.class);
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final Path mcDir;
    private final Gson gson = new Gson();
    private final Map<String, VersionJson> versions = new LinkedHashMap<>();
    private String latestRelease;

    public VersionManager(Path mcDir) {
        this.mcDir = mcDir;
    }

    public List<String> discoverVersions() {
        versions.clear();
        Path versionsDir = mcDir.resolve("versions");
        if (!Files.isDirectory(versionsDir)) return List.of();

        try (Stream<Path> dirs = Files.list(versionsDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                String id = dir.getFileName().toString();
                Path jsonFile = dir.resolve(id + ".json");
                if (Files.exists(jsonFile)) {
                    try (FileReader reader = new FileReader(jsonFile.toFile())) {
                        VersionJson v = gson.fromJson(reader, VersionJson.class);
                        if (v != null) versions.put(id, v);
                    } catch (IOException e) {
                        log.warn("Error reading version {}", id, e);
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Error discovering versions", e);
        }

        return new ArrayList<>(versions.keySet());
    }

    public void fetchVersionManifest() throws IOException {
        Path tmp = Files.createTempFile("manifest", ".json");
        try {
            DownloadUtil.download(MANIFEST_URL, tmp);
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> manifest;
            try (FileReader reader = new FileReader(tmp.toFile())) {
                manifest = gson.fromJson(reader, mapType);
            }
            if (manifest == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) manifest.get("versions");
            if (entries == null) return;

            Path versionsDir = mcDir.resolve("versions");
            Files.createDirectories(versionsDir);

            for (Map<String, Object> entry : entries) {
                String id = (String) entry.get("id");
                String url = (String) entry.get("url");
                String type = (String) entry.get("type");
                if (id == null || url == null) continue;

                if ("release".equals(type)) {
                    latestRelease = id;
                }

                Path versionDir = versionsDir.resolve(id);
                Path jsonFile = versionDir.resolve(id + ".json");
                if (Files.exists(jsonFile)) continue;

                Files.createDirectories(versionDir);
                try {
                    DownloadUtil.download(url, jsonFile);
                    log.info("Downloaded version metadata: {}", id);
                } catch (Exception e) {
                    log.warn("Failed to download version {}: {}", id, e.getMessage());
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public VersionJson resolve(String versionId) {
        VersionJson v = versions.get(versionId);
        if (v == null) {
            try {
                downloadVersion(versionId);
                discoverVersions();
                v = versions.get(versionId);
            } catch (IOException e) {
                log.warn("Could not download version {}", versionId);
            }
        }
        if (v == null) return null;

        Set<String> seen = new HashSet<>();
        while (v.inheritsFrom != null && seen.add(v.inheritsFrom)) {
            VersionJson parent = versions.get(v.inheritsFrom);
            if (parent == null) {
                try {
                    downloadVersion(v.inheritsFrom);
                } catch (IOException e) {
                    log.warn("Could not download parent: {}", v.inheritsFrom);
                    break;
                }
                discoverVersions();
                parent = versions.get(v.inheritsFrom);
            }
            if (parent == null) break;
            v = merge(v, parent);
        }

        return v;
    }

    public String latestRelease() {
        return latestRelease;
    }

    public Path versionJarPath(String versionId) {
        return mcDir.resolve("versions/" + versionId + "/" + versionId + ".jar");
    }

    public void extractAll(Path destDir) throws IOException {
        copyResource("/embedded/version.json",
                destDir.resolve("versions/26.3/26.3.json"));
        copyResource("/embedded/client.bin",
                destDir.resolve("versions/26.3/26.3.jar"));
        extractEmbeddedLibraries(destDir);
    }

    private void copyResource(String resourcePath, Path dest) throws IOException {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null) {
            in = getClass().getClassLoader().getResourceAsStream(
                    resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
        }
        if (in != null) {
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            in.close();
            log.info("Extracted {} to {}", resourcePath, dest);
        }
    }

    private void extractEmbeddedLibraries(Path destDir) {
        // Embedded libraries would be extracted here if packed
    }

    private VersionJson merge(VersionJson child, VersionJson parent) {
        VersionJson result = new VersionJson();
        result.id = child.id != null ? child.id : parent.id;
        result.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
        result.minecraftArguments = child.minecraftArguments != null ? child.minecraftArguments : parent.minecraftArguments;
        result.assetIndex = child.assetIndex != null ? child.assetIndex : parent.assetIndex;
        result.downloads = child.downloads != null ? child.downloads : parent.downloads;
        result.logging = child.logging != null ? child.logging : parent.logging;
        result.javaVersion = child.javaVersion != null ? child.javaVersion : parent.javaVersion;
        result.arguments = child.arguments != null ? child.arguments : parent.arguments;
        result.libraries = child.libraries != null ? child.libraries : parent.libraries;
        result.inheritsFrom = null;
        return result;
    }

    private void downloadVersion(String versionId) throws IOException {
        Path tmp = Files.createTempFile("manifest", ".json");
        try {
            DownloadUtil.download(MANIFEST_URL, tmp);
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> manifest;
            try (FileReader reader = new FileReader(tmp.toFile())) {
                manifest = gson.fromJson(reader, mapType);
            }
            if (manifest == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) manifest.get("versions");
            if (entries == null) return;

            for (Map<String, Object> entry : entries) {
                if (versionId.equals(entry.get("id"))) {
                    String url = (String) entry.get("url");
                    if (url == null) return;
                    Path versionDir = mcDir.resolve("versions/" + versionId);
                    Path jsonFile = versionDir.resolve(versionId + ".json");
                    Files.createDirectories(versionDir);
                    DownloadUtil.download(url, jsonFile);
                    return;
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
