package xyz.yourboykyle.secretroutes.dungeons.detection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.yourboykyle.secretroutes.dungeons.detection.DungeonMapState.Checkmark.*;

class DungeonMapStateTest {
    private final DungeonMapState state = new DungeonMapState();

    @ParameterizedTest
    @CsvSource({"16,5,5", "16,5,16", "18,11,11", "18,22,11", "18,22,22"})
    void calibratesFloorLayoutsAndFindsChecks(int size, int startX, int startZ) {
        MapFixture map = new MapFixture(size, startX, startZ);
        map.room(1, 2, 63, 34);
        state.update(map.colors);

        assertTrue(state.isCalibrated());
        assertEquals(WHITE, at(1, 2));
        assertEquals(UNKNOWN, at(2, 1));
        assertEquals(NONE, at(3, 0), "The solid green entrance is not a green check");
    }

    @Test
    void whiteAdvancesToGreenAndNeverRegressesDuringIncompleteUpdates() {
        MapFixture map = new MapFixture(16, 5, 5);
        map.room(1, 2, 63, 34);
        state.update(map.colors);
        assertEquals(WHITE, at(1, 2));

        state.update(null);
        state.update(new byte[128 * 128]);
        assertEquals(WHITE, at(1, 2));

        map.room(1, 2, 63, 30);
        state.update(map.colors);
        assertEquals(GREEN, at(1, 2));
        assertFalse(at(1, 2).shouldRender(true, false, false));
        assertTrue(at(1, 2).shouldRender(true, true, false));

        map.room(1, 2, 63, 34);
        state.update(map.colors);
        state.update(new byte[128 * 128]);
        state.update(null);
        assertEquals(GREEN, at(1, 2));
    }

    @ParameterizedTest
    @CsvSource({"63,63,NONE", "66,18,NONE", "85,34,UNKNOWN", "63,119,UNKNOWN", "18,18,NONE", "0,34,UNKNOWN", "255,30,UNKNOWN"})
    void backgroundsAndFailedOrUnexploredRoomsDoNotUnlockRoutes(int background, int check, String expected) {
        MapFixture map = new MapFixture(16, 5, 5);
        map.room(1, 2, background, check);
        state.update(map.colors);
        assertEquals(DungeonMapState.Checkmark.valueOf(expected), at(1, 2));
        assertFalse(at(1, 2).shouldRender(true, true, false));
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN,false,false,true", "NONE,false,false,true", "WHITE,false,false,true", "GREEN,false,false,false",
            "UNKNOWN,false,true,true", "NONE,false,true,true", "WHITE,false,true,true", "GREEN,false,true,true",
            "UNKNOWN,true,false,false", "NONE,true,false,false", "WHITE,true,false,true", "GREEN,true,false,false",
            "UNKNOWN,true,true,false", "NONE,true,true,false", "WHITE,true,true,true", "GREEN,true,true,true"})
    void controlsClearAndCompletionSeparately(String check, boolean onlyAfterClear, boolean renderCompleted, boolean expected) {
        assertEquals(expected, DungeonMapState.Checkmark.valueOf(check).shouldRender(onlyAfterClear, renderCompleted, false));
    }

    @Test
    void keepsExistingSecretCountCompletionBehavior() {
        assertFalse(WHITE.shouldRender(false, false, true));
        assertFalse(WHITE.shouldRender(true, false, true));
        assertTrue(WHITE.shouldRender(false, true, true));
        assertTrue(WHITE.shouldRender(true, true, true));
        assertFalse(UNKNOWN.shouldRender(true, true, true), "Secrets alone cannot confirm a mob clear");
    }

    @Test
    void checksAllComponentsOfLongLAndSquareRoomsWithoutUnlockingNeighbors() {
        int[][][] shapes = {
                {{0, 1}, {1, 1}},
                {{0, 1}, {1, 1}, {2, 1}},
                {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
                {{0, 1}, {1, 1}, {0, 2}},
                {{0, 1}, {1, 1}, {0, 2}, {1, 2}}
        };
        for (int[][] shape : shapes) {
            state.reset();
            MapFixture map = new MapFixture(16, 5, 5);
            for (int[] tile : shape) map.room(tile[0], tile[1], 63, 63);
            int[] last = shape[shape.length - 1];
            map.room(last[0], last[1], 63, 34);
            state.update(map.colors);

            DungeonMapState.Checkmark combined = UNKNOWN;
            for (int[] tile : shape) combined = combined.combine(at(tile[0], tile[1]));
            assertEquals(WHITE, combined);
            assertEquals(UNKNOWN, at(5, 5));

            map.room(last[0], last[1], 63, 30);
            state.update(map.colors);
            combined = UNKNOWN;
            for (int[] tile : shape) combined = combined.combine(at(tile[0], tile[1]));
            assertEquals(GREEN, combined);
        }
    }

    @Test
    void resetDropsCalibrationAndPreviousRunChecks() {
        MapFixture first = new MapFixture(16, 5, 5);
        first.room(1, 2, 63, 30);
        state.update(first.colors);
        state.reset();
        assertFalse(state.isCalibrated());
        assertEquals(UNKNOWN, at(1, 2));

        MapFixture next = new MapFixture(18, 22, 11);
        next.room(1, 2, 63, 63);
        state.update(next.colors);
        assertTrue(state.isCalibrated());
        assertEquals(NONE, at(1, 2));
    }

    @Test
    void ignoresMalformedMapsAndRejectsGreenStripesAsEntrances() {
        state.update(null);
        state.update(new byte[12]);
        state.update(new byte[128 * 128 + 1]);
        byte[] stripe = new byte[128 * 128];
        for (int x = 5; x < 21; x++) stripe[x + 5 * 128] = 30;
        state.update(stripe);
        assertFalse(state.isCalibrated());

        MapFixture map = new MapFixture(16, 5, 5);
        map.colors[0] = 63;
        state.update(map.colors);
        assertFalse(state.isCalibrated());
    }

    @Test
    void handlesMapEdgesAndInvalidWorldCoordinates() {
        MapFixture map = new MapFixture(18, 22, 22);
        map.room(3, 3, 63, 34);
        state.update(map.colors);
        assertEquals(WHITE, at(3, 3));
        assertEquals(UNKNOWN, at(5, 5));
        assertEquals(UNKNOWN, at(-1, 0));
        assertEquals(UNKNOWN, at(6, 0));
        assertEquals(UNKNOWN, state.checkmarkAtWorld(-184, -185));
    }

    private DungeonMapState.Checkmark at(int x, int z) {
        return state.checkmarkAtWorld(-185 + x * 32, -185 + z * 32);
    }

    private static final class MapFixture {
        private final byte[] colors = new byte[128 * 128];
        private final int size;
        private final int startX;
        private final int startZ;

        private MapFixture(int size, int startX, int startZ) {
            this.size = size;
            this.startX = startX;
            this.startZ = startZ;
            room(3, 0, 30, 30);
        }

        private void room(int x, int z, int background, int check) {
            int originX = startX + x * (size + 4);
            int originZ = startZ + z * (size + 4);
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) colors[originX + dx + (originZ + dz) * 128] = (byte) background;
            }
            if (check == background) return;
            String[] icon = {"    #", "   ##", "# ## ", "###  ", " #   "};
            for (int dz = 0; dz < icon.length; dz++) {
                for (int dx = 0; dx < icon[dz].length(); dx++) {
                    if (icon[dz].charAt(dx) == '#') {
                        colors[originX + size / 2 - 2 + dx + (originZ + size / 2 - 2 + dz) * 128] = (byte) check;
                    }
                }
            }
        }
    }
}
