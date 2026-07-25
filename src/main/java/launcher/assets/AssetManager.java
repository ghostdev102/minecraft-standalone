package launcher.assets;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import launcher.util.DownloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AssetManager {

    private static final Logger log = LoggerFactory.getLogger(AssetManager.class);
    private static final String ASSETS_URL = "https://resources.download.minecraft.net";
    private static final Gson gson = new Gson();

    private final Path mcDir;
    private boolean noSounds;

    public AssetManager(Path mcDir) {
        this.mcDir = mcDir;
    }

    public void setNoSounds(boolean noSounds) {
        this.noSounds = noSounds;
    }

    public void downloadIndex(String id, String url) throws IOException {
        Path indexesDir = mcDir.resolve("assets/indexes");
        Files.createDirectories(indexesDir);
        Path indexFile = indexesDir.resolve(id + ".json");
        if (!Files.exists(indexFile)) {
            log.info("Downloading asset index: {}", id);
            DownloadUtil.download(url, indexFile);
        }
    }

    public void prepareAssets(String assetIndexId) throws IOException {
        Path indexFile = mcDir.resolve("assets/indexes/" + assetIndexId + ".json");
        if (!Files.exists(indexFile)) {
            log.warn("Asset index not found: {}", indexFile);
            return;
        }

        Type mapType = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
        Map<String, Object> root;
        try (FileReader reader = new FileReader(indexFile.toFile())) {
            root = gson.fromJson(reader, mapType);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> objects = (Map<String, Object>) root.get("objects");
        if (objects == null) return;

        Path objectsDir = mcDir.resolve("assets/objects");
        int count = 0;

        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) entry.getValue();
            String hash = (String) info.get("hash");
            if (hash == null) continue;

            String assetName = entry.getKey();
            if (noSounds && (assetName.endsWith(".ogg") || assetName.endsWith(".wav"))) {
                continue;
            }

            Path objectFile = objectsDir.resolve(hash.substring(0, 2) + "/" + hash);
            if (!Files.exists(objectFile)) {
                String url = ASSETS_URL + "/" + hash.substring(0, 2) + "/" + hash;
                try {
                    Files.createDirectories(objectFile.getParent());
                    DownloadUtil.download(url, objectFile);
                    count++;
                } catch (Exception e) {
                    log.debug("Could not download asset: {}", hash);
                }
            }
        }

        if (count > 0) {
            log.info("Downloaded {} missing assets", count);
        }
    }
}
