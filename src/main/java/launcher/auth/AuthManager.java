package launcher.auth;

public class AuthManager {

    public static class AuthException extends Exception {
        public AuthException(String msg) { super(msg); }
    }

    public record AuthProfile(String username, String uuid, String accessToken, String userType) {}
}
