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

package xyz.yourboykyle.secretroutes.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import xyz.yourboykyle.secretroutes.Main;
import xyz.yourboykyle.secretroutes.config.DungeonClass;
import xyz.yourboykyle.secretroutes.config.SRMConfig;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonUtil {
    private static final String STORM_END_MESSAGE = "[BOSS] Storm: I should have known that I stood no chance.";
    private static final String GOLDOR_START_MESSAGE = "[BOSS] Goldor: Who dares trespass into my domain?";
    // Hypixel's dungeon party rows show a level, optional rank tags, player name and class.
    private static final Pattern DUNGEON_PARTY_ROW = Pattern.compile(
            "^\\s*\\[\\d+]\\s+(?:\\[[^\\]]+]\\s+)*(\\w{1,16})\\s+.*?"
                    + "\\((Healer|Berserk|Archer|Tank|Mage)(?:\\s+\\w+)?\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static boolean inF7 = false;
    private static boolean predevWindowClosed = false;
    private static int tickCounter = 0;
    private static DungeonClass detectedClass;

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!LocationUtils.isInDungeons() || client.level == null) {
                inF7 = false;
                detectedClass = null;
                return;
            }

            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                checkFloor(client);
                if (inF7 && SRMConfig.get().autoClassDetection) {
                    checkDungeonClass(client);
                }
            }
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;

            String cleanText = ChatFormatting.stripFormatting(message.getString()).trim();

            if (!predevWindowClosed && (STORM_END_MESSAGE.equals(cleanText) || GOLDOR_START_MESSAGE.equals(cleanText))) {
                predevWindowClosed = true;
                LogUtils.info("Predev route window closed by boss message: " + cleanText);
            }

            return true;
        });
    }

    private static void checkFloor(Minecraft client) {
        Scoreboard scoreboard = client.level.getScoreboard();
        boolean foundF7 = false;

        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            String rawText = team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString();

            String cleanText = ChatFormatting.stripFormatting(rawText);

            if (cleanText.contains("The Catacombs (F7)") || cleanText.contains("The Catacombs (M7)")) {
                foundF7 = true;
                break;
            }
        }

        inF7 = foundF7;
    }

    private static void checkDungeonClass(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        if (client.player == null || connection == null) return;

        String playerName = client.player.getName().getString();
        for (PlayerInfo playerInfo : connection.getListedOnlinePlayers()) {
            Component displayName = playerInfo.getTabListDisplayName();
            if (displayName == null) continue;

            Matcher row = DUNGEON_PARTY_ROW.matcher(ChatFormatting.stripFormatting(displayName.getString()));
            if (!row.matches() || !playerName.equalsIgnoreCase(row.group(1))) continue;

            DungeonClass currentClass = DungeonClass.valueOf(row.group(2).toUpperCase(Locale.ROOT));
            if (currentClass != detectedClass) {
                detectedClass = currentClass;
                if (Main.currentRoom != null) Main.currentRoom.reloadIfBossRouteClassChanged();
            }
            return;
        }
    }

    public static DungeonClass getDetectedClass() {
        return detectedClass;
    }

    public static boolean isF7() {
        return inF7;
    }

    public static boolean isPredevWindowClosed() {
        return predevWindowClosed;
    }

    public static void reset() {
        inF7 = false;
        predevWindowClosed = false;
        tickCounter = 0;
        detectedClass = null;
    }
}
