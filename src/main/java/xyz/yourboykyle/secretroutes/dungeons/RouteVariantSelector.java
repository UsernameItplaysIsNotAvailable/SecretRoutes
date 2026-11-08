//#if FABRIC
/*
 * Secret Routes Mod - Secret Route Waypoints for Hypixel Skyblock Dungeons
 * Copyright 2025 yourboykyle & R-aMcC & christechs
 *
 * <DO NOT REMOVE THIS COPYRIGHT NOTICE>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.yourboykyle.secretroutes.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import xyz.yourboykyle.secretroutes.config.BossRouteMode;
import xyz.yourboykyle.secretroutes.config.DungeonClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RouteVariantSelector {
    private RouteVariantSelector() {
    }

    record RouteVariant(String jsonKey, int variantNumber, JsonArray steps, BlockPos startPosition) {
    }

    record Selection(int index, double distanceSquared) {
    }

    static List<RouteVariant> parseVariants(
            JsonObject rawData, String roomName, BossRouteMode bossRouteMode, DungeonClass bossRouteClass
    ) {
        if (rawData == null || roomName == null) return List.of();

        String normalizedRoomName = roomName.toLowerCase(Locale.ROOT);
        if (normalizedRoomName.equals("f7boss")) {
            return parseBossRoute(rawData, bossRouteMode, bossRouteClass);
        }

        List<RouteVariant> variants = new ArrayList<>();

        for (var entry : rawData.entrySet()) {
            int variantNumber = getVariantNumber(entry.getKey(), normalizedRoomName);
            if (variantNumber < 0) continue;

            RouteVariant variant = parseVariant(entry.getKey(), variantNumber, entry.getValue());
            if (variant != null) variants.add(variant);
        }

        variants.sort(Comparator
                .comparingInt(RouteVariant::variantNumber)
                .thenComparing(RouteVariant::jsonKey, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(variants);
    }

    private static List<RouteVariant> parseBossRoute(JsonObject rawData, BossRouteMode mode, DungeonClass dungeonClass) {
        var entry = findRoute(rawData, mode.routeKey(dungeonClass));
        // Older route files only contain the healer routes under their original keys.
        if (entry == null && dungeonClass == DungeonClass.HEALER) {
            entry = findRoute(rawData, mode.legacyRouteKey());
        }
        if (entry == null) return List.of();

        RouteVariant variant = parseVariant(entry.getKey(), 0, entry.getValue());
        return variant == null ? List.of() : List.of(variant);
    }

    private static Map.Entry<String, JsonElement> findRoute(JsonObject rawData, String key) {
        for (var entry : rawData.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry;
        }
        return null;
    }

    static Selection selectClosest(List<RouteVariant> variants, BlockPos playerPosition) {
        if (variants == null || variants.isEmpty() || playerPosition == null) return null;

        int selectedIndex = -1;
        double selectedDistance = Double.POSITIVE_INFINITY;

        for (int i = 0; i < variants.size(); i++) {
            RouteVariant candidate = variants.get(i);
            double distance = distanceSquared(playerPosition, candidate.startPosition());

            if (selectedIndex < 0 || distance < selectedDistance
                    || (Double.compare(distance, selectedDistance) == 0
                    && compareVariants(candidate, variants.get(selectedIndex)) < 0)) {
                selectedIndex = i;
                selectedDistance = distance;
            }
        }

        return new Selection(selectedIndex, selectedDistance);
    }

    static int cycleIndex(int currentIndex, int variantCount, int direction) {
        if (variantCount <= 0) return -1;
        int normalizedCurrentIndex = currentIndex >= 0 && currentIndex < variantCount ? currentIndex : 0;
        return Math.floorMod(normalizedCurrentIndex + direction, variantCount);
    }

    private static int getVariantNumber(String jsonKey, String normalizedRoomName) {
        String normalizedKey = jsonKey.toLowerCase(Locale.ROOT);
        if (normalizedKey.equals(normalizedRoomName)) return 0;

        String prefix = normalizedRoomName + ":";
        if (!normalizedKey.startsWith(prefix)) return -1;

        String suffix = normalizedKey.substring(prefix.length());
        try {
            int variantNumber = Integer.parseInt(suffix);
            return variantNumber > 0 ? variantNumber : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static RouteVariant parseVariant(String jsonKey, int variantNumber, JsonElement element) {
        try {
            JsonArray route = element.getAsJsonArray();
            if (route.isEmpty()) return null;

            JsonArray locations = route.get(0).getAsJsonObject().getAsJsonArray("locations");
            if (locations == null || locations.isEmpty()) return null;

            JsonArray start = locations.get(0).getAsJsonArray();
            if (start.size() < 3) return null;

            BlockPos startPosition = new BlockPos(
                    start.get(0).getAsInt(),
                    start.get(1).getAsInt(),
                    start.get(2).getAsInt()
            );
            return new RouteVariant(jsonKey, variantNumber, route, startPosition);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int compareVariants(RouteVariant first, RouteVariant second) {
        int numberComparison = Integer.compare(first.variantNumber(), second.variantNumber());
        return numberComparison != 0
                ? numberComparison
                : String.CASE_INSENSITIVE_ORDER.compare(first.jsonKey(), second.jsonKey());
    }

    private static double distanceSquared(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long y = (long) first.getY() - second.getY();
        long z = (long) first.getZ() - second.getZ();
        return (double) x * x + (double) y * y + (double) z * z;
    }
}
//#endif
