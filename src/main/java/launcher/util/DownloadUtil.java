package launcher.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadUtil {

    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void download(String url, Path dest) throws IOException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        try {
            Files.createDirectories(tmp.getParent());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200) {
                Files.deleteIfExists(tmp);
                throw new IOException("HTTP " + res.statusCode() + " downloading " + url);
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException e) {
            Files.deleteIfExists(tmp);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
}
