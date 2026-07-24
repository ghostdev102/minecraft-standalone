package launcher.launch;

import java.util.List;

public class LaunchProfile {

    private final String username;
    private final String uuid;
    private final String accessToken;
    private final String versionId;
    private final String mcDir;
    private final String assetsDir;
    private final String assetIndexId;
    private final String userType;
    private final String javaPath;
    private final List<String> jvmArgs;
    private final List<String> gameArgs;

    public LaunchProfile(String username, String uuid, String accessToken,
                         String versionId, String mcDir, String assetsDir,
                         String assetIndexId, String userType,
                         String javaPath, List<String> jvmArgs, List<String> gameArgs) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.versionId = versionId;
        this.mcDir = mcDir;
        this.assetsDir = assetsDir;
        this.assetIndexId = assetIndexId;
        this.userType = userType;
        this.javaPath = javaPath;
        this.jvmArgs = jvmArgs;
        this.gameArgs = gameArgs;
    }

    public String username() { return username; }
    public String uuid() { return uuid; }
    public String accessToken() { return accessToken; }
    public String versionId() { return versionId; }
    public String mcDir() { return mcDir; }
    public String assetsDir() { return assetsDir; }
    public String assetIndexId() { return assetIndexId; }
    public String userType() { return userType; }
    public String javaPath() { return javaPath; }
    public List<String> jvmArgs() { return jvmArgs; }
    public List<String> gameArgs() { return gameArgs; }
}
