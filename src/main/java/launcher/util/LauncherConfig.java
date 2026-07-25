package launcher.util;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class LauncherConfig {

    private final Path file;
    private final Properties props = new Properties();

    public LauncherConfig(Path mcDir) {
        this.file = mcDir.resolve("launcher.properties");
        if (Files.exists(file)) {
            try (FileReader r = new FileReader(file.toFile())) {
                props.load(r);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public void set(String key, String value) {
        if (value != null && !value.isEmpty()) {
            props.setProperty(key, value);
        } else {
            props.remove(key);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (FileWriter w = new FileWriter(file.toFile())) {
                props.store(w, "MinecraftStandalone Launcher config");
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
