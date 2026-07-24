package launcher.libraries;

import launcher.util.VersionJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LibraryManager {

    private static final String LIBRARIES_URL = "https://libraries.minecraft.net";

    private final Path mcDir;

    public LibraryManager(Path mcDir) {
        this.mcDir = mcDir;
    }

    public record MissingLibrary(VersionJson.Library lib, Path path) {}

    public List<MissingLibrary> getMissingLibraries(VersionJson resolved) {
        List<MissingLibrary> missing = new ArrayList<>();
        if (resolved.libraries == null) return missing;

        for (VersionJson.Library lib : resolved.libraries) {
            if (!matchesPlatform(lib)) continue;
            Path jarPath = resolveJarPath(lib);
            if (!Files.exists(jarPath)) {
                missing.add(new MissingLibrary(lib, jarPath));
            }
        }
        return missing;
    }

    public List<Path> gatherLibraries(VersionJson resolved) {
        List<Path> paths = new ArrayList<>();
        if (resolved.libraries == null) return paths;

        for (VersionJson.Library lib : resolved.libraries) {
            if (!matchesPlatform(lib)) continue;
            Path jarPath = resolveJarPath(lib);
            if (Files.exists(jarPath)) {
                paths.add(jarPath);
            }
        }
        return paths;
    }

    public void downloadLibrary(Path libPath, VersionJson.Library lib) throws IOException {
        if (lib.downloads != null && lib.downloads.artifact != null && lib.downloads.artifact.url != null) {
            Files.createDirectories(libPath.getParent());
            launcher.util.DownloadUtil.download(lib.downloads.artifact.url, libPath);
            return;
        }
        String relative = mcDir.resolve("libraries").relativize(libPath).toString();
        String url = LIBRARIES_URL + "/" + relative.replace('\\', '/');
        Files.createDirectories(libPath.getParent());
        launcher.util.DownloadUtil.download(url, libPath);
    }

    private boolean matchesPlatform(VersionJson.Library lib) {
        if (lib.rules == null || lib.rules.isEmpty()) return true;
        boolean allow = false;
        for (VersionJson.Library.Rule rule : lib.rules) {
            boolean matches = true;
            if (rule.os != null) {
                matches = osMatches(rule.os);
            }
            if (matches) {
                allow = "allow".equals(rule.action);
            }
        }
        return allow;
    }

    private boolean osMatches(VersionJson.Library.Rule.OsCondition os) {
        if (os.name == null) return true;
        String current = System.getProperty("os.name").toLowerCase();
        boolean osMatch = switch (os.name.toLowerCase()) {
            case "windows" -> current.contains("win");
            case "osx" -> current.contains("mac");
            case "linux" -> current.contains("nix") || current.contains("nux");
            default -> false;
        };
        if (!osMatch) return false;
        if (os.arch != null) {
            String currentArch = System.getProperty("os.arch");
            return normalizeArch(os.arch).equals(normalizeArch(currentArch));
        }
        return true;
    }

    private String normalizeArch(String arch) {
        if (arch == null) return "";
        arch = arch.toLowerCase();
        if (arch.equals("x86_64") || arch.equals("amd64")) return "x64";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64";
        return arch;
    }

    private Path resolveJarPath(VersionJson.Library lib) {
        if (lib.downloads != null && lib.downloads.artifact != null && lib.downloads.artifact.path != null) {
            return mcDir.resolve("libraries/" + lib.downloads.artifact.path);
        }

        String[] parts = lib.name.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;

        StringBuilder fileName = new StringBuilder();
        fileName.append(artifact).append("-").append(version);
        if (classifier != null) {
            fileName.append("-").append(classifier);
        }
        fileName.append(".jar");

        return mcDir.resolve("libraries/" + group + "/" + artifact + "/" + version + "/" + fileName);
    }
}
