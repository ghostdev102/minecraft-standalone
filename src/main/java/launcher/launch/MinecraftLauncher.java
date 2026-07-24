package launcher.launch;

import launcher.natives.NativeManager;
import launcher.util.VersionJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MinecraftLauncher {

    private static final Logger log = LoggerFactory.getLogger(MinecraftLauncher.class);

    private final VersionJson version;
    private final NativeManager nativeManager;
    private final Path jarPath;
    private final List<Path> classpath;

    public MinecraftLauncher(VersionJson version, NativeManager nativeManager,
                             Path jarPath, List<Path> classpath) {
        this.version = version;
        this.nativeManager = nativeManager;
        this.jarPath = jarPath;
        this.classpath = classpath;
    }

    public Process launch(LaunchProfile profile) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(findJava());

        String nativesDir = nativeManager.nativesDir().toAbsolutePath().toString();
        cmd.add("-Djava.library.path=" + nativesDir);
        cmd.add("-Dminecraft.client.jar=" + jarPath.toAbsolutePath());

        if (version.logging != null && version.logging.client != null
                && version.logging.client.argument != null) {
            String arg = version.logging.client.argument
                    .replace("${path}", profile.mcDir() + "/assets/log_configs/"
                            + version.logging.client.file.id);
            cmd.add(arg);
        }

        if (version.arguments != null && version.arguments.jvm != null) {
            for (Object obj : version.arguments.jvm) {
                if (obj instanceof String s) {
                    cmd.add(expand(s, profile));
                } else if (obj instanceof Map<?, ?> map) {
                    addConditionalArg(cmd, map, profile);
                }
            }
        }

        StringBuilder cp = new StringBuilder();
        for (Path p : classpath) {
            if (cp.length() > 0) cp.append(File.pathSeparatorChar);
            cp.append(p.toAbsolutePath());
        }
        if (cp.length() > 0) cp.append(File.pathSeparatorChar);
        cp.append(jarPath.toAbsolutePath());
        cmd.add("-cp");
        cmd.add(cp.toString());

        cmd.add(version.mainClass != null ? version.mainClass : "net.minecraft.client.main.Main");

        if (version.arguments != null && version.arguments.game != null) {
            for (Object obj : version.arguments.game) {
                if (obj instanceof String s) {
                    cmd.add(expand(s, profile));
                } else if (obj instanceof Map<?, ?> map) {
                    addConditionalArg(cmd, map, profile);
                }
            }
        } else if (version.minecraftArguments != null) {
            for (String part : version.minecraftArguments.split(" ")) {
                cmd.add(expand(part, profile));
            }
        }

        if (profile.gameArgs() != null) {
            cmd.addAll(profile.gameArgs());
        }

        log.info("Launching Minecraft");
        return new ProcessBuilder(cmd)
                .directory(Path.of(profile.mcDir()).toFile())
                .inheritIO()
                .start();
    }

    @SuppressWarnings("unchecked")
    private void addConditionalArg(List<String> cmd, Map<?, ?> map, LaunchProfile profile) {
        Object rulesObj = map.get("rules");
        if (!(rulesObj instanceof List<?> rules)) return;
        if (!evaluateRules((List<Map<?, ?>>) rules)) return;

        Object value = map.get("value");
        if (value instanceof String s) {
            cmd.add(expand(s, profile));
        } else if (value instanceof List<?> list) {
            for (Object v : list) {
                if (v instanceof String s) {
                    cmd.add(expand(s, profile));
                }
            }
        }
    }

    private boolean evaluateRules(List<Map<?, ?>> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allow = false;
        for (var rule : rules) {
            boolean matches = true;
            if (rule.get("features") != null) {
                Map<?, ?> features = (Map<?, ?>) rule.get("features");
                if (!features.isEmpty()) matches = false;
            }
            if (matches && rule.get("os") instanceof Map<?, ?> os) {
                String name = os.get("name") != null ? os.get("name").toString() : null;
                String arch = os.get("arch") != null ? os.get("arch").toString() : null;
                matches = osMatches(name, arch);
            }
            if (matches) {
                allow = "allow".equals(rule.get("action"));
            }
        }
        return allow;
    }

    private boolean osMatches(String osName, String osArch) {
        if (osName == null) return true;
        String current = System.getProperty("os.name").toLowerCase();
        boolean match = switch (osName.toLowerCase()) {
            case "windows" -> current.contains("win");
            case "osx" -> current.contains("mac");
            case "linux" -> current.contains("nix") || current.contains("nux");
            default -> false;
        };
        if (!match) return false;
        if (osArch != null) {
            return normalizeArch(osArch).equals(normalizeArch(System.getProperty("os.arch")));
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

    private static String findJava() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private String expand(String arg, LaunchProfile profile) {
        return arg
                .replace("${auth_player_name}", profile.username())
                .replace("${auth_uuid}", profile.uuid())
                .replace("${auth_access_token}", profile.accessToken())
                .replace("${user_type}", profile.userType())
                .replace("${version_name}", profile.versionId())
                .replace("${game_directory}", profile.mcDir())
                .replace("${assets_root}", profile.assetsDir())
                .replace("${assets_index_name}", profile.assetIndexId())
                .replace("${game_assets}", profile.assetsDir())
                .replace("${user_properties}", "{}")
                .replace("${version_type}", profile.versionId())
                .replace("${natives_directory}", nativeManager.nativesDir().toAbsolutePath().toString())
                .replace("${launcher_name}", "standalone-launcher")
                .replace("${launcher_version}", "1.0")
                .replace("${classpath}", "")
                .replace("${clientid}", "")
                .replace("${auth_xuid}", "")
                .replace("${resolution_width}", "")
                .replace("${resolution_height}", "");
    }
}