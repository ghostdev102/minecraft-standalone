package launcher.util;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class VersionJson {

    public String id;
    public String inheritsFrom;
    public String mainClass;
    public String minecraftArguments;
    public String server;
    public AssetIndex assetIndex;
    public Downloads downloads;
    public Logging logging;
    public JavaVersion javaVersion;
    public Arguments arguments;
    public List<Library> libraries;

    public static class AssetIndex {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }

    public static class Downloads {
        public Download client;
        public Download server;
        public Download windowsServer;
    }

    public static class Download {
        public String sha1;
        public long size;
        public String url;
        public String path;
    }

    public static class Logging {
        public LoggingEntry client;
    }

    public static class LoggingEntry {
        public String argument;
        public LoggingFile file;
        public String type;
    }

    public static class LoggingFile {
        public String id;
        public String sha1;
        public long size;
        public String url;
    }

    public static class JavaVersion {
        public int majorVersion;
    }

    public static class Arguments {
        public List<Object> game;
        public List<Object> jvm;
    }

    public static class Library {
        public String name;
        public LibraryDownloads downloads;
        public Map<String, String> natives;
        public List<Rule> rules;
        public Extract extract;
        public Map<String, Object> checksums;

        public static class Extract {
            public List<String> exclude;
        }

        public static class Rule {
            public String action;
            public OsCondition os;

            public static class OsCondition {
                public String name;
                public String arch;
            }
        }
    }

    public static class LibraryDownloads {
        public Download artifact;
        public Map<String, Download> classifiers;
    }

    public String assetIndexId() {
        return assetIndex != null ? assetIndex.id : null;
    }
}
