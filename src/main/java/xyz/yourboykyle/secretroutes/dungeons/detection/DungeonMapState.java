/*BSD 3-Clause License

Copyright (c) 2025, odtheking

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.*/

package xyz.yourboykyle.secretroutes.dungeons.detection;

import java.util.Arrays;

/**
 * Reads server map checkmarks, using Odin's MapScan palette and entrance calibration.
 * Reference: https://github.com/odtheking/Odin, thank you!
 */
public final class DungeonMapState {
    private static final int MAP_SIZE = 128;
    private static final int GRID_SIZE = 6;
    private static final int ENTRANCE_COLOR = 30;

    public enum Checkmark {
        UNKNOWN, NONE, WHITE, GREEN;

        public Checkmark combine(Checkmark other) {
            return ordinal() >= other.ordinal() ? this : other;
        }

        public boolean shouldRender(boolean onlyAfterClear, boolean renderCompleted, boolean allSecretsFound) {
            if ((this == GREEN || allSecretsFound) && !renderCompleted) return false;
            return !onlyAfterClear || this == WHITE || this == GREEN;
        }
    }

    private final Checkmark[] checkmarks = new Checkmark[GRID_SIZE * GRID_SIZE];
    private int roomSize;
    private int startX;
    private int startZ;

    public DungeonMapState() {
        reset();
    }

    public void reset() {
        roomSize = 0;
        startX = 0;
        startZ = 0;
        Arrays.fill(checkmarks, Checkmark.UNKNOWN);
    }

    public boolean isCalibrated() {
        return roomSize != 0;
    }

    public void update(byte[] colors) {
        if (colors == null || colors.length != MAP_SIZE * MAP_SIZE || colors[0] != 0) return;
        if (!isCalibrated() && !calibrate(colors)) return;

        int gap = roomSize + 4;
        for (int z = 0; z < GRID_SIZE; z++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int mapX = startX + x * gap;
                int mapZ = startZ + z * gap;
                int background = pixel(colors, mapX, mapZ);
                if (!isRoomColor(background)) continue;

                int center = pixel(colors, mapX + roomSize / 2 - 1, mapZ + roomSize / 2 + 1);
                Checkmark checkmark = Checkmark.NONE;
                if (center != background && background != ENTRANCE_COLOR) {
                    checkmark = switch (center) {
                        case 34 -> Checkmark.WHITE;
                        case 30 -> Checkmark.GREEN;
                        case 119 -> Checkmark.UNKNOWN;
                        default -> Checkmark.NONE;
                    };
                }
                int index = x + z * GRID_SIZE;
                checkmarks[index] = checkmarks[index].combine(checkmark);
            }
        }
    }

    public Checkmark checkmarkAtWorld(int worldX, int worldZ) {
        int offsetX = worldX + 185;
        int offsetZ = worldZ + 185;
        if (offsetX % 32 != 0 || offsetZ % 32 != 0) return Checkmark.UNKNOWN;
        int x = offsetX / 32;
        int z = offsetZ / 32;
        if (x < 0 || x >= GRID_SIZE || z < 0 || z >= GRID_SIZE) return Checkmark.UNKNOWN;
        return checkmarks[x + z * GRID_SIZE];
    }

    private boolean calibrate(byte[] colors) {
        for (int z = 0; z < MAP_SIZE; z++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                if (pixel(colors, x, z) != ENTRANCE_COLOR
                        || pixel(colors, x - 1, z) == ENTRANCE_COLOR
                        || pixel(colors, x, z - 1) == ENTRANCE_COLOR) continue;

                int width = 0;
                while (x + width < MAP_SIZE && pixel(colors, x + width, z) == ENTRANCE_COLOR) width++;
                if ((width != 16 && width != 18) || z + width > MAP_SIZE) continue;

                boolean square = true;
                for (int dz = 0; dz < width && square; dz++) {
                    for (int dx = 0; dx < width; dx++) {
                        if (pixel(colors, x + dx, z + dz) != ENTRANCE_COLOR) {
                            square = false;
                            break;
                        }
                    }
                }
                if (!square) continue;

                roomSize = width;
                int gap = width + 4;
                startX = x % gap;
                startZ = z % gap;
                // Entrance/F1 maps leave one whole grid cell as padding.
                if (startX == 0) startX = gap;
                if (startZ == 0) startZ = gap;
                return true;
            }
        }
        return false;
    }

    private static boolean isRoomColor(int color) {
        return switch (color) {
            case 18, 30, 62, 63, 66, 74, 82 -> true;
            default -> false;
        };
    }

    private static int pixel(byte[] colors, int x, int z) {
        return x >= 0 && x < MAP_SIZE && z >= 0 && z < MAP_SIZE ? colors[x + z * MAP_SIZE] & 0xff : 0;
    }
}
