package launcher.auth;

import java.util.UUID;

public class OfflineAuthManager {

    private final String username;

    public OfflineAuthManager(String username) {
        this.username = username;
    }

    public AuthManager.AuthProfile authenticate() {
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString();
        return new AuthManager.AuthProfile(username, uuid, "offline", "mojang");
    }
}
