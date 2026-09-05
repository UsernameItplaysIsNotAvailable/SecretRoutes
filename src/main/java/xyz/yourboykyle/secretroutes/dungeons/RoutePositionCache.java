//#if FABRIC
package xyz.yourboykyle.secretroutes.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import xyz.yourboykyle.secretroutes.utils.BlockUtils;
import xyz.yourboykyle.secretroutes.utils.RoomRotationUtils;
import xyz.yourboykyle.secretroutes.utils.RotationUtils;
import xyz.yourboykyle.secretroutes.utils.multistorage.Triple;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Client-thread, room-owned positions only. Returned vectors/arrays are read-only to callers. */
final class RoutePositionCache {
    record Position(BlockPos block, Vector3d origin, Vector3d center) {
        Position(BlockPos block) {
            this(block, new Vector3d(block.getX(), block.getY(), block.getZ()),
                    new Vector3d(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5));
        }
    }

    record Pearl(Vector3d box, Vector3d start, Vector3d end) { }
    record SecretMarker(String relativeKey, Position position) { }

    private JsonArray route;
    private String direction;
    private BlockPos anchor;
    private Point corner;
    private boolean absolute;
    private final Map<JsonArray, Position> waypoints = new IdentityHashMap<>();
    private final Map<JsonArray, Vector3d[]> lines = new IdentityHashMap<>();
    private final Map<JsonArray, List<BlockPos>> particles = new IdentityHashMap<>();
    private final Map<JsonObject, List<Pearl>> pearls = new IdentityHashMap<>();
    private final Map<JsonObject, SecretMarker> secrets = new IdentityHashMap<>();
    private final Map<BlockPos, Position> blocks = new HashMap<>();

    void updateContext(JsonArray route, String direction, BlockPos anchor, boolean absolute) {
        if (this.route == route && Objects.equals(this.direction, direction)
                && Objects.equals(this.anchor, anchor) && this.absolute == absolute) return;
        invalidate();
        this.route = route;
        this.direction = direction;
        this.anchor = anchor == null ? null : anchor.immutable();
        this.corner = anchor == null ? null : new Point(anchor.getX(), anchor.getZ());
        this.absolute = absolute;
    }

    /** Call when installing edits to an existing route object or reloading secret resources. */
    void invalidate() {
        route = null;
        waypoints.clear();
        lines.clear();
        particles.clear();
        pearls.clear();
        secrets.clear();
        blocks.clear();
    }

    Position waypoint(JsonArray location) {
        return waypoints.computeIfAbsent(location, loc -> block(new BlockPos(
                loc.get(0).getAsInt(), loc.get(1).getAsInt(), loc.get(2).getAsInt())));
    }

    Position block(BlockPos relative) {
        return blocks.computeIfAbsent(relative.immutable(), pos -> new Position(absolute ? pos
                : RoomRotationUtils.relativeToActual(pos, direction, corner)));
    }

    Vector3d[] linePoints(JsonArray locations) {
        return lines.computeIfAbsent(locations, source -> {
            Vector3d[] points = new Vector3d[source.size()];
            for (int i = 0; i < source.size(); i++) {
                JsonArray loc = source.get(i).getAsJsonArray();
                Triple<Double, Double, Double> pos = actual(
                        loc.get(0).getAsDouble(), loc.get(1).getAsDouble(), loc.get(2).getAsDouble());
                points[i] = new Vector3d(pos.getOne() + 0.5, pos.getTwo() + 0.5, pos.getThree() + 0.5);
            }
            return points;
        });
    }

    List<BlockPos> particlePoints(JsonArray locations) {
        return particles.computeIfAbsent(locations, source -> {
            List<BlockPos> points = new ArrayList<>(source.size());
            for (JsonElement loc : source) points.add(waypoint(loc.getAsJsonArray()).block());
            return List.copyOf(points);
        });
    }

    List<Pearl> pearls(JsonObject step) {
        return pearls.computeIfAbsent(step, source -> {
            JsonArray locations = source.getAsJsonArray("enderpearls");
            JsonArray angles = source.getAsJsonArray("enderpearlangles");
            List<Pearl> result = new ArrayList<>(locations.size());
            for (int i = 0; i < locations.size(); i++) {
                JsonArray loc = locations.get(i).getAsJsonArray();
                JsonArray angle = angles.get(i).getAsJsonArray();
                Triple<Double, Double, Double> pos = actual(
                        loc.get(0).getAsDouble(), loc.get(1).getAsDouble(), loc.get(2).getAsDouble());
                double px = pos.getOne() - 0.25, py = pos.getTwo(), pz = pos.getThree() - 0.25;
                double pitch = Math.toRadians(angle.get(0).getAsDouble());
                double yaw = Math.toRadians(RotationUtils.relativeToActualYaw(
                        angle.get(1).getAsFloat(), direction == null ? "S" : direction) + 90);
                double x = -Math.sin(yaw) * Math.cos(pitch);
                double y = -Math.sin(pitch);
                double z = Math.cos(yaw) * Math.cos(pitch);
                double length = Math.sqrt(x * x + y * y + z * z);
                x /= length;
                y /= length;
                z /= length;
                result.add(new Pearl(new Vector3d(px, py, pz),
                        new Vector3d(px + 0.25F, py + 1.62F, pz + 0.25F),
                        new Vector3d(px + x * 10.0 + 0.25, py + y * 10.0 + 1.62, pz + z * 10.0 + 0.25)));
            }
            return List.copyOf(result);
        });
    }

    SecretMarker secret(JsonObject secret) {
        return secrets.computeIfAbsent(secret, source -> {
            BlockPos relative = new BlockPos(source.get("x").getAsInt(), source.get("y").getAsInt(), source.get("z").getAsInt());
            return new SecretMarker(BlockUtils.blockPos(relative), block(relative));
        });
    }

    private Triple<Double, Double, Double> actual(double x, double y, double z) {
        return absolute ? new Triple<>(x, y, z) : RoomRotationUtils.relativeToActual(x, y, z, direction, corner);
    }
}
//#endif
