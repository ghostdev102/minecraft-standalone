package launcher.util;

import java.util.Locale;

public class OsUtil {

    public enum Os { WINDOWS, LINUX, macOS }

    public static Os currentOs() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Os.WINDOWS;
        if (os.contains("mac")) return Os.macOS;
        return Os.LINUX;
    }

    public static String arch() {
        return System.getProperty("os.arch");
    }

    public static String mcDir() {
        String home = System.getProperty("user.home");
        return switch (currentOs()) {
            case WINDOWS -> System.getenv("APPDATA") + "/.minecraft";
            case macOS -> home + "/Library/Application Support/minecraft";
            case LINUX -> home + "/.minecraft";
        };
    }
}
