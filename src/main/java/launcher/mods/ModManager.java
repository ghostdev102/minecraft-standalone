package launcher.mods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModManager {

    private static final Logger log = LoggerFactory.getLogger(ModManager.class);
    private static final HttpClient client = HttpClient.newHttpClient();

    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

    private final Path mcDir;

    public ModManager(Path mcDir) {
        this.mcDir = mcDir;
    }

    public record ForgeVersion(String fullVersion, String mcVersion, String forgeBuild) {}

    public List<ForgeVersion> availableForgeVersions() throws IOException {
        return parseMavenMetadata(FORGE_MAVEN);
    }

    public List<ForgeVersion> availableNeoForgeVersions() throws IOException {
        return parseMavenMetadata(NEOFORGE_MAVEN);
    }

    public String findForge(String mcVersion) throws IOException {
        var versions = availableForgeVersions();
        return versions.stream()
                .filter(v -> v.mcVersion.equals(mcVersion))
                .map(v -> v.fullVersion)
                .reduce((a, b) -> a.compareTo(b) > 0 ? a : b)
                .orElse(null);
    }

    public String findNeoForge(String mcVersion) throws IOException {
        var versions = availableNeoForgeVersions();
        return versions.stream()
                .filter(v -> v.mcVersion.equals(mcVersion))
                .map(v -> v.fullVersion)
                .reduce((a, b) -> a.compareTo(b) > 0 ? a : b)
                .orElse(null);
    }

    public void installForge(String forgeVersion) throws IOException {
        String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/"
                + forgeVersion + "/forge-" + forgeVersion + "-installer.jar";
        Path installer = mcDir.resolve(".launcher/forge-installer-" + forgeVersion + ".jar");
        Files.createDirectories(installer.getParent());

        if (!Files.exists(installer)) {
            log.info("Downloading Forge installer: {}", forgeVersion);
            launcher.util.DownloadUtil.download(url, installer);
        }

        log.info("Running Forge installer...");
        Process proc = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", installer.toAbsolutePath().toString(),
                "--installClient",
                mcDir.toAbsolutePath().toString()
        ).inheritIO().start();

        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while installing Forge", e);
        }

        if (exit != 0) {
            throw new IOException("Forge installer exited with code " + exit);
        }
        log.info("Forge {} installed successfully", forgeVersion);
    }

    public void installNeoForge(String neoVersion) throws IOException {
        String url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                + neoVersion + "/neoforge-" + neoVersion + "-installer.jar";
        Path installer = mcDir.resolve(".launcher/neoforge-installer-" + neoVersion + ".jar");
        Files.createDirectories(installer.getParent());

        if (!Files.exists(installer)) {
            log.info("Downloading NeoForge installer: {}", neoVersion);
            launcher.util.DownloadUtil.download(url, installer);
        }

        log.info("Running NeoForge installer...");
        Process proc = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", installer.toAbsolutePath().toString(),
                "--installClient",
                mcDir.toAbsolutePath().toString()
        ).inheritIO().start();

        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while installing NeoForge", e);
        }

        if (exit != 0) {
            throw new IOException("NeoForge installer exited with code " + exit);
        }
        log.info("NeoForge {} installed successfully", neoVersion);
    }

    public void copyMod(Path modJar) throws IOException {
        Path modsDir = mcDir.resolve("mods");
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(modJar.getFileName());
        if (!dest.equals(modJar)) {
            Files.copy(modJar, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied mod: {} → {}", modJar.getFileName(), dest);
        }
    }

    private List<ForgeVersion> parseMavenMetadata(String metadataUrl) throws IOException {
        List<ForgeVersion> result = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(metadataUrl)).GET().build();
            HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode() + " fetching " + metadataUrl);
            }

            XMLStreamReader xml = XMLInputFactory.newInstance().createXMLStreamReader(res.body());
            String currentVersion = null;
            boolean inVersion = false;

            while (xml.hasNext()) {
                xml.next();
                if (xml.isStartElement() && "version".equals(xml.getLocalName())) {
                    inVersion = true;
                } else if (inVersion && xml.isCharacters()) {
                    currentVersion = xml.getText().trim();
                } else if (xml.isEndElement() && "version".equals(xml.getLocalName())) {
                    if (currentVersion != null && !currentVersion.isEmpty()) {
                        String[] parts = currentVersion.split("-", 2);
                        String mc = parts[0];
                        String build = parts.length > 1 ? parts[1] : "";
                        result.add(new ForgeVersion(currentVersion, mc, build));
                    }
                    currentVersion = null;
                    inVersion = false;
                }
            }
            xml.close();
        } catch (Exception e) {
            throw new IOException("Failed to parse maven metadata from " + metadataUrl, e);
        }
        return result;
    }
}
