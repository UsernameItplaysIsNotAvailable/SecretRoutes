//#if FABRIC
package xyz.yourboykyle.secretroutes.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.yourboykyle.secretroutes.utils.RoomRotationUtils;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

class RoutePositionCacheTest {
    @ParameterizedTest
    @ValueSource(strings = {"S", "W", "N", "E", "UNKNOWN"})
    void blockAndFractionalLineCoordinatesMatchPreviousTransforms(String direction) {
        RoutePositionCache cache = new RoutePositionCache();
        cache.updateContext(new JsonArray(), direction, new BlockPos(-185, 70, -153), false);
        JsonArray point = xyz(-3.75, 68.875, 5.625);
        JsonArray locations = list(point);

        BlockPos expectedBlock = RoomRotationUtils.relativeToActual(new BlockPos(-3, 68, 5), direction, new Point(-185, -153));
        assertEquals(expectedBlock, cache.waypoint(point).block());
        assertEquals(expectedBlock, cache.particlePoints(locations).getFirst());
        assertVector(cache.waypoint(point).origin(), expectedBlock.getX(), expectedBlock.getY(), expectedBlock.getZ());
        assertVector(cache.waypoint(point).center(), expectedBlock.getX() + 0.5, expectedBlock.getY() + 0.5, expectedBlock.getZ() + 0.5);

        var expectedLine = RoomRotationUtils.relativeToActual(-3.75, 68.875, 5.625, direction, new Point(-185, -153));
        assertVector(cache.linePoints(locations)[0], expectedLine.getOne() + 0.5, expectedLine.getTwo() + 0.5, expectedLine.getThree() + 0.5);
    }

    @Test
    void unchangedContextReusesAllPositionCollections() {
        RoutePositionCache cache = new RoutePositionCache();
        JsonArray route = new JsonArray();
        BlockPos anchor = new BlockPos(10, 70, 20);
        cache.updateContext(route, "S", anchor, false);
        JsonArray point = xyz(2, 65, 3);
        JsonArray locations = list(point);
        JsonObject step = pearlStep();
        JsonObject secret = secret(2, 65, 3);
        var waypoint = cache.waypoint(point);
        var lines = cache.linePoints(locations);
        var particles = cache.particlePoints(locations);
        var pearls = cache.pearls(step);
        var marker = cache.secret(secret);

        for (int frame = 0; frame < 1000; frame++) {
            cache.updateContext(route, "S", new BlockPos(10, 70, 20), false);
            assertSame(waypoint, cache.waypoint(point));
            assertSame(lines, cache.linePoints(locations));
            assertSame(particles, cache.particlePoints(locations));
            assertSame(pearls, cache.pearls(step));
            assertSame(marker, cache.secret(secret));
        }
    }

    @Test
    void providerOrVariantReplacementDropsPreviousGeometryEvenForEqualJson() {
        RoutePositionCache cache = new RoutePositionCache();
        JsonArray route = new JsonArray();
        JsonArray locations = list(xyz(1, 2, 3));
        cache.updateContext(route, "S", BlockPos.ZERO, false);
        var original = cache.linePoints(locations);
        cache.updateContext(route.deepCopy(), "S", BlockPos.ZERO, false);
        assertNotSame(original, cache.linePoints(locations));
        assertEquals(original[0], cache.linePoints(locations)[0]);
    }

    @Test
    void rotationAnchorAndBossModeChangesRefreshPositions() {
        RoutePositionCache cache = new RoutePositionCache();
        JsonArray route = new JsonArray();
        JsonArray point = xyz(2, 65, 3);
        BlockPos.MutableBlockPos anchor = new BlockPos.MutableBlockPos(10, 70, 20);
        cache.updateContext(route, "S", anchor, false);
        assertEquals(new BlockPos(12, 65, 23), cache.waypoint(point).block());
        cache.updateContext(route, "W", anchor, false);
        assertEquals(new BlockPos(7, 65, 22), cache.waypoint(point).block());
        anchor.set(100, 70, 200);
        cache.updateContext(route, "W", anchor, false);
        assertEquals(new BlockPos(97, 65, 202), cache.waypoint(point).block());
        cache.updateContext(route, "UNKNOWN", null, true);
        assertEquals(new BlockPos(2, 65, 3), cache.waypoint(point).block());
        assertVector(cache.linePoints(list(point))[0], 2.5, 65.5, 3.5);
        cache.updateContext(route, "S", anchor, false);
        assertEquals(new BlockPos(102, 65, 203), cache.waypoint(point).block());
    }

    @Test
    void explicitInvalidationRefreshesInstalledEditsAndResourceMarkers() {
        RoutePositionCache cache = new RoutePositionCache();
        JsonArray route = new JsonArray();
        cache.updateContext(route, "S", BlockPos.ZERO, false);
        JsonArray point = xyz(2, 65, 3);
        JsonArray locations = list(point);
        JsonObject step = pearlStep();
        JsonObject secret = secret(2, 65, 3);
        cache.waypoint(point);
        cache.linePoints(locations);
        cache.particlePoints(locations);
        var oldPearl = cache.pearls(step).getFirst();
        cache.secret(secret);

        point.set(0, xyz(9, 0, 0).get(0));
        step.getAsJsonArray("enderpearlangles").get(0).getAsJsonArray().set(1, xyz(90, 0, 0).get(0));
        secret.addProperty("x", 9);
        cache.invalidate();
        cache.updateContext(route, "S", BlockPos.ZERO, false);

        assertEquals(new BlockPos(9, 65, 3), cache.waypoint(point).block());
        assertVector(cache.linePoints(locations)[0], 9.5, 65.5, 3.5);
        assertEquals(new BlockPos(9, 65, 3), cache.particlePoints(locations).getFirst());
        assertNotEquals(oldPearl.end(), cache.pearls(step).getFirst().end());
        assertEquals("9:65:3", cache.secret(secret).relativeKey());
        assertEquals(new BlockPos(9, 65, 3), cache.secret(secret).position().block());
    }

    @ParameterizedTest
    @ValueSource(strings = {"S", "W", "N", "E", "UNKNOWN"})
    void pearlGuidesKeepOffsetsPitchAndRoomYaw(String direction) {
        RoutePositionCache cache = new RoutePositionCache();
        cache.updateContext(new JsonArray(), direction, new BlockPos(100, 0, 200), false);
        var pearl = cache.pearls(pearlStep()).getFirst();
        var pos = RoomRotationUtils.relativeToActual(2.75, 65.125, -3.625, direction, new Point(100, 200));
        assertVector(pearl.box(), pos.getOne() - 0.25, pos.getTwo(), pos.getThree() - 0.25);
        assertVector(pearl.start(), pos.getOne(), pos.getTwo() + (double) 1.62F, pos.getThree());
        double horizontal = 10 * Math.cos(Math.toRadians(30));
        double dx = switch (direction) { case "N" -> horizontal; case "W", "E" -> 0; default -> -horizontal; };
        double dz = switch (direction) { case "W" -> -horizontal; case "E" -> horizontal; default -> 0; };
        assertVector(pearl.end(), pos.getOne() + dx, pos.getTwo() + 1.62 - 5, pos.getThree() + dz);
    }

    @Test
    void bossPearlsKeepAbsoluteCoordinatesAndCachesAreRoomLocal() {
        RoutePositionCache first = new RoutePositionCache();
        RoutePositionCache second = new RoutePositionCache();
        JsonArray route = new JsonArray();
        first.updateContext(route, "UNKNOWN", new BlockPos(100, 0, 200), true);
        second.updateContext(route, "S", new BlockPos(100, 0, 200), false);
        JsonObject step = pearlStep();
        assertVector(first.pearls(step).getFirst().box(), 2.5, 65.125, -3.875);
        assertVector(second.pearls(step).getFirst().box(), 102.5, 65.125, 196.125);
        assertNotSame(first.pearls(step), second.pearls(step));
    }

    private static JsonObject pearlStep() {
        JsonObject step = new JsonObject();
        step.add("enderpearls", list(xyz(2.75, 65.125, -3.625)));
        step.add("enderpearlangles", list(xyz(30, 0, 0)));
        return step;
    }

    private static JsonObject secret(int x, int y, int z) {
        JsonObject secret = new JsonObject();
        secret.addProperty("x", x);
        secret.addProperty("y", y);
        secret.addProperty("z", z);
        return secret;
    }

    private static JsonArray xyz(double x, double y, double z) {
        JsonArray point = new JsonArray();
        point.add(x);
        point.add(y);
        point.add(z);
        return point;
    }

    private static JsonArray list(JsonArray point) {
        JsonArray list = new JsonArray();
        list.add(point);
        return list;
    }

    private static void assertVector(Vector3d actual, double x, double y, double z) {
        assertEquals(x, actual.x, 1e-10);
        assertEquals(y, actual.y, 1e-10);
        assertEquals(z, actual.z, 1e-10);
    }
}
//#endif
