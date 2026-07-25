package launcher.mods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModManager {

    private static final Logger log = LoggerFactory.getLogger(ModManager.class);
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String OPTIFINE_DOWNLOADS = "https://optifine.net/downloads";
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/maven-metadata.xml";
    private static final String FABRIC_INSTALLER_BASE = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/";

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
        runInstaller(installer);
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
        runInstaller(installer);
        log.info("NeoForge {} installed successfully", neoVersion);
    }

    public String findLatestFabricInstaller() throws IOException {
        List<ForgeVersion> versions = parseMavenMetadata(FABRIC_MAVEN);
        return versions.stream()
                .map(v -> v.fullVersion)
                .reduce((a, b) -> a.compareTo(b) > 0 ? a : b)
                .orElse(null);
    }

    public void installFabric(String installerVersion, String mcVersion) throws IOException {
        String url = FABRIC_INSTALLER_BASE + installerVersion + "/fabric-installer-" + installerVersion + ".jar";
        Path installer = mcDir.resolve(".launcher/fabric-installer-" + installerVersion + ".jar");
        Files.createDirectories(installer.getParent());
        if (!Files.exists(installer)) {
            log.info("Downloading Fabric installer: {}", installerVersion);
            launcher.util.DownloadUtil.download(url, installer);
        }
        log.info("Running Fabric installer for MC {}", mcVersion);
        ensureLauncherProfile();
        Process proc = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", installer.toAbsolutePath().toString(),
                "client",
                "-dir", mcDir.toAbsolutePath().toString(),
                "-mcversion", mcVersion
        ).inheritIO().start();
        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        if (exit != 0) {
            throw new IOException("Fabric installer exited with code " + exit);
        }
        log.info("Fabric {} installed for MC {}", installerVersion, mcVersion);
    }

    public Path downloadOptiFine(String mcVersion) throws IOException {
        String html;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(OPTIFINE_DOWNLOADS))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode() + " fetching OptiFine downloads");
            }
            html = res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }

        // Find download page link for this MC version
        // <tr>.*?<td[^>]*>1\.21\.1.*?href="(.*?)"
        Pattern versionPattern = Pattern.compile(
                "<td[^>]*>" + Pattern.quote(mcVersion) + "</td>\\s*<td[^>]*><a href=\"([^\"]+)\"",
                Pattern.DOTALL);
        Matcher m = versionPattern.matcher(html);
        if (!m.find()) {
            throw new IOException("OptiFine not available for " + mcVersion);
        }
        String dlPageUrl = m.group(1);
        if (!dlPageUrl.startsWith("http")) {
            dlPageUrl = "https://optifine.net" + dlPageUrl;
        }

        // Fetch download page to get actual jar URL
        String pageHtml;
        try {
            HttpRequest req2 = HttpRequest.newBuilder(URI.create(dlPageUrl))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> res2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            if (res2.statusCode() != 200) {
                throw new IOException("HTTP " + res2.statusCode() + " fetching " + dlPageUrl);
            }
            pageHtml = res2.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }

        // Find download link with "OptiFine" in URL
        Pattern jarPattern = Pattern.compile("href=\"([^\"]*OptiFine[^\"]*\\.jar)\"");
        Matcher jm = jarPattern.matcher(pageHtml);
        if (!jm.find()) {
            throw new IOException("Could not find OptiFine jar URL on download page");
        }
        String jarUrl = jm.group(1);
        if (!jarUrl.startsWith("http")) {
            jarUrl = "https://optifine.net" + jarUrl;
        }

        String fileName = jarUrl.substring(jarUrl.lastIndexOf('/') + 1);
        Path dest = mcDir.resolve("libraries/optifine/" + fileName);
        if (!Files.exists(dest)) {
            log.info("Downloading OptiFine: {}", fileName);
            Files.createDirectories(dest.getParent());
            launcher.util.DownloadUtil.download(jarUrl, dest);
        }
        return dest;
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

    private void ensureLauncherProfile() throws IOException {
        Path profileFile = mcDir.resolve("launcher_profiles.json");
        if (!Files.exists(profileFile)) {
            Files.writeString(profileFile, "{\"profiles\":{},\"selectedProfile\":\"\",\"clientToken\":\"00000000-0000-0000-0000-000000000000\"}");
            log.info("Created dummy launcher_profiles.json for installer");
        }
    }

    private void runInstaller(Path installer) throws IOException {
        ensureLauncherProfile();
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
            throw new IOException("Interrupted", e);
        }
        if (exit != 0) {
            throw new IOException("Installer exited with code " + exit);
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
                        result.add(new ForgeVersion(currentVersion, parts[0], parts.length > 1 ? parts[1] : ""));
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
