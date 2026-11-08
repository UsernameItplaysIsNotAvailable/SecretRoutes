package xyz.yourboykyle.secretroutes.dungeons.detection;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class DungeonRoomClearTracker {
    private static final DungeonMapState STATE = new DungeonMapState();
    private static MapId mapId;
    private static int ticks;
    private static boolean mapAvailable;

    private DungeonRoomClearTracker() { }

    public static void tick(Minecraft client) {
        if (ticks++ % 5 != 0) return;

        ItemStack stack = client.player.getInventory().getItem(8);
        mapAvailable = false;
        if (!(stack.getItem() instanceof MapItem) || !stack.getHoverName().getString().contains("Magical Map")) return;

        MapId currentId = stack.get(DataComponents.MAP_ID);
        if (currentId == null) return;
        MapItemSavedData data = MapItem.getSavedData(stack, client.level);
        if (data == null) return;
        if (!currentId.equals(mapId)) {
            STATE.reset();
            mapId = currentId;
        }
        mapAvailable = true;
        STATE.update(data.colors);
    }

    public static DungeonMapState.Checkmark getCheckmark(DungeonRoom room) {
        DungeonMapState.Checkmark result = DungeonMapState.Checkmark.UNKNOWN;
        if (room != null) {
            for (RoomComponent component : room.roomComponents) {
                result = result.combine(STATE.checkmarkAtWorld(component.x(), component.z()));
            }
        }
        return result;
    }

    public static String debugStatus() {
        return "Magical Map available: " + mapAvailable + ", calibrated: " + STATE.isCalibrated();
    }

    public static void reset() {
        STATE.reset();
        mapId = null;
        ticks = 0;
        mapAvailable = false;
    }
}
