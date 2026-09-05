//#if FABRIC
package xyz.yourboykyle.secretroutes.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoomSecretCacheTest {
    @Test
    void repeatedMissesAndRoomChangesReuseOneDataset() {
        AtomicInteger loads = new AtomicInteger();
        JsonObject dataset = new JsonObject();
        JsonArray first = new JsonArray();
        JsonArray second = new JsonArray();
        dataset.add("First", first);
        dataset.add("Second", second);
        dataset.addProperty("Version", 1);
        RoomSecretCache cache = new RoomSecretCache(() -> {
            loads.incrementAndGet();
            return dataset;
        }, e -> fail(e));

        assertNull(cache.get(null));
        assertEquals(0, loads.get());
        for (int frame = 0; frame < 1000; frame++) assertNull(cache.get("missing"));
        assertSame(first, cache.get("FIRST"));
        assertSame(second, cache.get("second"));
        assertSame(first, cache.get("first"));
        assertNull(cache.get("version"));
        assertEquals(1, loads.get());
    }

    @Test
    void resourceReloadReplacesDataAndPreviouslyMissingRooms() {
        AtomicInteger loads = new AtomicInteger();
        JsonArray oldData = new JsonArray();
        JsonArray newData = new JsonArray();
        JsonArray addedRoom = new JsonArray();
        RoomSecretCache cache = new RoomSecretCache(() -> {
            JsonObject dataset = new JsonObject();
            boolean reloaded = loads.incrementAndGet() > 1;
            dataset.add("room", reloaded ? newData : oldData);
            if (reloaded) dataset.add("added", addedRoom);
            return dataset;
        }, e -> fail(e));

        assertSame(oldData, cache.get("room"));
        assertNull(cache.get("added"));
        cache.invalidate();
        assertSame(newData, cache.get("room"));
        assertSame(addedRoom, cache.get("added"));
        assertEquals(2, loads.get());
    }

    @Test
    void failedLoadIsLoggedOnceAndRetriesOnlyAfterReload() {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        JsonArray recovered = new JsonArray();
        RoomSecretCache cache = new RoomSecretCache(() -> {
            if (loads.incrementAndGet() == 1) throw new IOException("unavailable");
            JsonObject dataset = new JsonObject();
            dataset.add("room", recovered);
            return dataset;
        }, e -> errors.incrementAndGet());

        for (int frame = 0; frame < 1000; frame++) assertNull(cache.get("room"));
        assertEquals(1, loads.get());
        assertEquals(1, errors.get());
        cache.invalidate();
        assertSame(recovered, cache.get("room"));
        assertEquals(2, loads.get());
        assertEquals(1, errors.get());
    }

    @Test
    void lookupDoesNotDependOnSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            JsonObject dataset = new JsonObject();
            JsonArray secrets = new JsonArray();
            dataset.add("ICE", secrets);
            RoomSecretCache cache = new RoomSecretCache(() -> dataset, e -> fail(e));
            assertSame(secrets, cache.get("ice"));
            assertSame(secrets, cache.get("ICE"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
//#endif
