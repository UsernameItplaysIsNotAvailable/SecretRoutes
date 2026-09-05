//#if FABRIC
package xyz.yourboykyle.secretroutes.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Bundled secret data, including unsuccessful lookups, lives until resource reload. */
final class RoomSecretCache {
    @FunctionalInterface
    interface Loader {
        JsonObject load() throws Exception;
    }

    private final Loader loader;
    private final Consumer<Exception> onError;
    private Map<String, JsonArray> rooms;

    RoomSecretCache(Loader loader, Consumer<Exception> onError) {
        this.loader = loader;
        this.onError = onError;
    }

    JsonArray get(String roomName) {
        if (roomName == null) return null;
        if (rooms == null) {
            // Publish an empty result even on failure, so the render loop never retries.
            rooms = Map.of();
            try {
                Map<String, JsonArray> loaded = new HashMap<>();
                for (var entry : loader.load().entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        loaded.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsJsonArray());
                    }
                }
                rooms = loaded;
            } catch (Exception e) {
                onError.accept(e);
            }
        }
        return rooms.get(roomName.toLowerCase(Locale.ROOT));
    }

    void invalidate() {
        rooms = null;
    }
}
//#endif
