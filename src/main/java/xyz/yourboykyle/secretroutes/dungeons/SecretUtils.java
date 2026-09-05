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
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.joml.Vector3d;
import xyz.yourboykyle.secretroutes.Main;
import xyz.yourboykyle.secretroutes.config.SRMConfig;
import xyz.yourboykyle.secretroutes.dungeons.rendering.RenderTypes;
import xyz.yourboykyle.secretroutes.dungeons.rendering.RenderingBackend;
import xyz.yourboykyle.secretroutes.utils.*;

import java.awt.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SecretUtils {
    public static JsonArray secrets = null;
    private static final RoomSecretCache SECRET_CACHE = new RoomSecretCache(() -> {
        try (Reader reader = new InputStreamReader(
                Main.class.getResourceAsStream("/assets/secretroutesmod/secretlocations.json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }, LogUtils::error);
    public static boolean renderLever = false;
    public static BlockPos currentLeverPos = null;
    public static BlockPos lastInteract;
    public static Long removeBannerTime = null;
    public static boolean first = true;
    public static String chestName = null;
    public static String leverName = null;
    public static String leverNumber = null;
    public static ArrayList<String> secretLocations = new ArrayList<>();

    public static final Set<BlockPos> brokenBlocks = new HashSet<>();

    public static int activeEtherwarpStep = -1;
    public static int targetEtherwarpIndex = 0;
    private static BlockPos currentEtherwarpTarget;

    private static String getColorCode(SRMConfig.TextColor color) {
        return color.formatting.toString();
    }

    private static boolean shouldRenderRouteStep(int stepIndex) {
        int visibleRouteSteps = Math.max(1, Math.min(5, SRMConfig.get().visibleRouteSteps));
        int currentStepIndex = Main.currentRoom.currentSecretIndex;
        return SRMConfig.get().wholeRoute || SRMConfig.get().allSteps || (stepIndex >= currentStepIndex && stepIndex < currentStepIndex + visibleRouteSteps);
    }

    private static Color colorForRouteStep(int stepIndex, Color currentColor, Color secondStepColor) {
        return stepIndex == Main.currentRoom.currentSecretIndex ? currentColor : secondStepColor;
    }

    private static BlockPos getActualWaypointPosition(JsonElement element) {
        return Main.currentRoom.positions().waypoint(element.getAsJsonArray()).block();
    }

    private static boolean isPlayerNearEtherwarp(LocalPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getY() - (pos.getY() + 1.0);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double distance = SRMConfig.get().etherwarpDetectionDistance;
        return (dx * dx + dy * dy + dz * dz) <= distance * distance;
    }

    private static void updateEtherwarpTarget(JsonArray locations, LocalPlayer player) {
        if (SRMConfig.get().autoSkipEtherwarps) {
            int furthestNearbyIndex = -1;
            for (int i = targetEtherwarpIndex + 1; i < locations.size(); i++) {
                BlockPos pos = getActualWaypointPosition(locations.get(i));
                if (!brokenBlocks.contains(pos) && isPlayerNearEtherwarp(player, pos)) {
                    furthestNearbyIndex = i;
                }
            }

            if (furthestNearbyIndex >= 0) {
                targetEtherwarpIndex = furthestNearbyIndex + 1;
                return;
            }
        }

        if (targetEtherwarpIndex < locations.size()) {
            BlockPos pos = getActualWaypointPosition(locations.get(targetEtherwarpIndex));
            if (!brokenBlocks.contains(pos) && isPlayerNearEtherwarp(player, pos)) {
                targetEtherwarpIndex++;
            }
        }
    }

    public static BlockPos updateCurrentEtherwarpTarget(LocalPlayer player) {
        SRMConfig config = SRMConfig.get();
        boolean needsTarget = config.etherwarpAimSound || (config.playerToEtherwarp && !config.wholeRoute);

        if (!needsTarget || player == null || Main.currentRoom == null || Main.currentRoom.currentSecretWaypoints == null
                || !Main.currentRoom.currentSecretWaypoints.has("etherwarps")) {
            clearEtherwarpTargetTracking();
            return null;
        }

        int activeStep = Main.currentRoom.currentSecretIndex;
        if (activeEtherwarpStep != activeStep) {
            activeEtherwarpStep = activeStep;
            targetEtherwarpIndex = 0;
        }

        JsonArray locations = Main.currentRoom.currentSecretWaypoints.getAsJsonArray("etherwarps");
        updateEtherwarpTarget(locations, player);

        if (targetEtherwarpIndex >= locations.size()) {
            currentEtherwarpTarget = null;
            return null;
        }

        BlockPos target = getActualWaypointPosition(locations.get(targetEtherwarpIndex));
        currentEtherwarpTarget = brokenBlocks.contains(target) ? null : target;
        return currentEtherwarpTarget;
    }

    public static void clearEtherwarpTargetTracking() {
        activeEtherwarpStep = -1;
        targetEtherwarpIndex = 0;
        currentEtherwarpTarget = null;
    }

    public static void renderingCallback(JsonObject waypoints, int index2) {
        if (waypoints == null) return;
        if (!shouldRenderRouteStep(index2)) return;

        // Player to Secret Line
        if (SRMConfig.get().playerWaypointLine) {
            RoutePositionCache.Position nextSecret = Main.currentRoom.getSecretPosition();
            if (nextSecret != null) {
                RenderingBackend.addLineFromCursor(new RenderTypes.LineFromCursor(nextSecret.center(), SRMConfig.get().playerToSecretLineColor, SRMConfig.get().playerToSecretLineWidth));
            }
        }

        renderWaypointCategory(
                waypoints, index2, "etherwarps", SRMConfig.get().renderEtherwarps, SRMConfig.get().etherWarp, SRMConfig.get().secondStepEtherWarp,
                SRMConfig.get().etherwarpFullBlock, SRMConfig.get().etherwarpBoxLineWidth, SRMConfig.get().etherwarpsTextToggle, SRMConfig.get().etherwarpNumberingToggle, SRMConfig.get().etherwarpsWaypointColor, "etherwarp", SRMConfig.get().etherwarpsTextSize, true
        );

        renderWaypointCategory(
                waypoints, index2, "mines", SRMConfig.get().renderMines, SRMConfig.get().mine, SRMConfig.get().secondStepMine,
                SRMConfig.get().mineFullBlock, SRMConfig.get().mineBoxLineWidth, SRMConfig.get().minesTextToggle, !SRMConfig.get().minesEnumToggle, SRMConfig.get().minesWaypointColor, "mine", SRMConfig.get().minesTextSize, false
        );

        renderWaypointCategory(
                waypoints, index2, "interacts", SRMConfig.get().renderInteracts, SRMConfig.get().interacts, SRMConfig.get().secondStepInteracts,
                SRMConfig.get().interactsFullBlock, SRMConfig.get().leverBoxLineWidth, SRMConfig.get().interactsTextToggle, !SRMConfig.get().interactsEnumToggle, SRMConfig.get().interactWaypointColor, "interact", SRMConfig.get().interactsTextSize, false
        );

        renderWaypointCategory(
                waypoints, index2, "tnts", SRMConfig.get().renderSuperboom, SRMConfig.get().superbooms, SRMConfig.get().secondStepSuperbooms,
                SRMConfig.get().superboomsFullBlock, SRMConfig.get().superboomBoxLineWidth, SRMConfig.get().superboomsTextToggle, !SRMConfig.get().superboomsEnumToggle, SRMConfig.get().superboomsWaypointColor, "superboom", SRMConfig.get().superboomsTextSize, false
        );

        // Bonzo Staff
        renderWaypointCategory(
                waypoints, index2, "bonzo_staffs", SRMConfig.get().renderBonzoStaff, SRMConfig.get().bonzoStaff, SRMConfig.get().secondStepBonzoStaff,
                SRMConfig.get().bonzoStaffFullBlock, SRMConfig.get().bonzoStaffBoxLineWidth, SRMConfig.get().bonzoStaffTextToggle, !SRMConfig.get().bonzoStaffEnumToggle, SRMConfig.get().bonzoStaffWaypointColor, "bonzo staff", SRMConfig.get().bonzoStaffTextSize, false
        );

        // Render Normal Lines
        if (waypoints.has("locations") && SRMConfig.get().lineType == SRMConfig.LineType.LINES) {
            renderConnectingLines(waypoints.getAsJsonArray("locations"));
        }

        // Render Ender Pearls
        if (waypoints.has("enderpearls") && SRMConfig.get().renderEnderpearls) {
            renderEnderPearls(waypoints, index2);
        }

        if (waypoints.has("secret") && !waypoints.get("secret").isJsonNull() && waypoints.get("secret").isJsonObject()) {
            renderCurrentSecret(waypoints.getAsJsonObject("secret"), index2);
        }

        // Render Start/Exit Labels
        renderStartAndExitLabels(waypoints, index2);
    }

    private static void renderWaypointCategory(JsonObject waypoints, int stepIndex, String jsonKey, boolean isEnabled, Color primaryColor, Color secondaryColor, boolean isFullBlock, float boxLineWidth, boolean textToggle, boolean showNumbering, SRMConfig.TextColor textColor, String textPrefix, float textSize, boolean isEtherwarp) {
        if (!isEnabled || !waypoints.has(jsonKey)) return;

        JsonArray locations = waypoints.getAsJsonArray(jsonKey);
        int counter = 1;
        Color boxColor = colorForRouteStep(stepIndex, primaryColor, secondaryColor);

        boolean isActiveStep = (stepIndex == Main.currentRoom.currentSecretIndex);
        boolean renderPlayerToEtherwarp = isEtherwarp && isActiveStep && !SRMConfig.get().wholeRoute && SRMConfig.get().playerToEtherwarp;
        RoutePositionCache positions = Main.currentRoom.positions();

        for (int i = 0; i < locations.size(); i++) {
            JsonElement element = locations.get(i);
            int currentNum = counter++;

            RoutePositionCache.Position position = positions.waypoint(element.getAsJsonArray());
            BlockPos pos = position.block();

            if (brokenBlocks.contains(pos)) continue;

            if (jsonKey.equals("mines") && Minecraft.getInstance().level != null) {
                if (Minecraft.getInstance().level.getBlockState(pos).isAir()) {
                    continue;
                }
            }

            if (renderPlayerToEtherwarp && pos.equals(currentEtherwarpTarget)) {
                Color lineColor = SRMConfig.get().useEtherwarpColorForLine ? SRMConfig.get().etherWarp : SRMConfig.get().playerToEtherwarpLineColor;
                RenderingBackend.addLineFromCursor(new RenderTypes.LineFromCursor(position.center(), lineColor, SRMConfig.get().playerToEtherwarpLineWidth));
            }

            String textContent = null;
            if (textToggle) {
                textContent = showNumbering ? textPrefix + " " + currentNum : textPrefix;
            }

            submitBoxAndText(position, boxColor, isFullBlock, boxLineWidth, textToggle, textColor, textContent, textSize, false);
        }
    }

    private static void renderConnectingLines(JsonArray lineLocations) {
        if (!SRMConfig.get().modEnabled) return;
        RenderingBackend.addLinesFromPoints(Main.currentRoom.positions().linePoints(lineLocations),
                SRMConfig.get().lineColor, SRMConfig.get().width, SRMConfig.get().renderLinesThroughWalls);
    }

    private static void renderEnderPearls(JsonObject waypoints, int stepIndex) {
        int index = 0;
        Color enderpearlColor = colorForRouteStep(stepIndex, SRMConfig.get().enderpearls, SRMConfig.get().secondStepEnderpearls);

        for (RoutePositionCache.Pearl pearl : Main.currentRoom.positions().pearls(waypoints)) {
            String textContent = SRMConfig.get().enderpearlEnumToggle ? "ender pearl" : "ender pearl " + (index + 1);

            submitBoxAndText(pearl.box(), enderpearlColor, SRMConfig.get().enderpearlFullBlock,
                    SRMConfig.get().enderpearlBoxLineWidth, SRMConfig.get().enderpearlTextToggle, SRMConfig.get().enderpearlWaypointColor, textContent, SRMConfig.get().enderpearlTextSize, pearl.box());

            RenderingBackend.addLine(new RenderTypes.Line(pearl.start(), pearl.end(), SRMConfig.get().pearlLineColor, SRMConfig.get().pearlLineWidth, true));
            index++;
        }
    }

    private static void renderCurrentSecret(JsonObject secret, int stepIndex) {
        if (!secret.has("type") || !secret.has("location")) return;

        String type = secret.get("type").getAsString();
        JsonArray loc = secret.get("location").getAsJsonArray();
        RoutePositionCache.Position position = Main.currentRoom.positions().waypoint(loc);

        switch (type) {
            case "interact":
                if (SRMConfig.get().renderSecretIteract) {
                    Color c = colorForRouteStep(stepIndex, SRMConfig.get().secretsInteract, SRMConfig.get().secondStepSecretsInteract);
                    submitBoxAndText(position, c, SRMConfig.get().secretsInteractFullBlock, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().interactTextToggle, SRMConfig.get().interactWaypointColor, "Interact", SRMConfig.get().interactTextSize, true);
                }
                break;
            case "item":
                if (SRMConfig.get().renderSecretsItem) {
                    Color c = colorForRouteStep(stepIndex, SRMConfig.get().secretsItem, SRMConfig.get().secondStepSecretsItem);
                    submitBoxAndText(position, c, SRMConfig.get().secretsItemFullBlock, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().itemTextToggle, SRMConfig.get().itemWaypointColor, "Item", SRMConfig.get().itemTextSize, true);
                }
                break;
            case "bat":
                if (SRMConfig.get().renderSecretBat) {
                    Color c = colorForRouteStep(stepIndex, SRMConfig.get().secretsBat, SRMConfig.get().secondStepSecretsBat);
                    submitBoxAndText(position, c, SRMConfig.get().secretsBatFullBlock, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().batTextToggle, SRMConfig.get().batWaypointColor, "Bat", SRMConfig.get().batTextSize, true);
                }
                break;
        }
    }

    private static void renderStartAndExitLabels(JsonObject waypoints, int index2) {
        if (!waypoints.has("locations") || waypoints.getAsJsonArray("locations").isEmpty()) return;

        if (index2 == 0 && SRMConfig.get().startTextToggle) {
            JsonArray startCoords = waypoints.getAsJsonArray("locations").get(0).getAsJsonArray();
            Vector3d pos = Main.currentRoom.positions().waypoint(startCoords).origin();
            RenderingBackend.addWorldText(new RenderTypes.WorldText(Component.literal(getColorCode(SRMConfig.get().startWaypointColor) + "Start"), pos, true, SRMConfig.get().startTextSize));
        }

        if (index2 == Main.currentRoom.currentSecretRoute.size() - 1 && SRMConfig.get().exitTextToggle) {
            if (waypoints.has("secret") && !waypoints.get("secret").isJsonNull() && waypoints.get("secret").isJsonObject()) {
                JsonObject secret = waypoints.getAsJsonObject("secret");
                if (secret.has("type") && secret.get("type").getAsString().equals("exitroute")) {
                    JsonArray loc = secret.getAsJsonArray("location");
                    Vector3d pos = Main.currentRoom.positions().waypoint(loc).origin();
                    RenderingBackend.addWorldText(new RenderTypes.WorldText(Component.literal(getColorCode(SRMConfig.get().exitWaypointColor) + "Exit"), pos, true, SRMConfig.get().exitTextSize));
                }
            }
        }
    }

    public static void renderSecrets() {
        secrets = getSecrets();
        if (secrets == null) return;

        for (JsonElement secret : secrets) {
            JsonObject secretInfos = secret.getAsJsonObject();
            String name = secretInfos.get("secretName").getAsString();

            if (!name.contains("Chest") && !name.contains("Bat") && !name.contains("Wither Essence") && !name.contains("Lever") && !name.contains("Item"))
                continue;

            RoutePositionCache.SecretMarker marker = Main.currentRoom.positions().secret(secretInfos);
            if (secretLocations.contains(marker.relativeKey())) continue;
            RoutePositionCache.Position boxPos = marker.position();

            if (name.contains("Chest") || name.contains("Wither Essence")) {
                submitBoxAndText(boxPos, SRMConfig.get().secretsInteract, false, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().interactTextToggle, SRMConfig.get().interactWaypointColor, "Interact", SRMConfig.get().interactTextSize, true);
            } else if (name.contains("Bat")) {
                submitBoxAndText(boxPos, SRMConfig.get().secretsBat, false, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().batTextToggle, SRMConfig.get().batWaypointColor, "Bat", SRMConfig.get().batTextSize, true);
            } else if (name.contains("Lever")) {
                submitBoxAndText(boxPos, SRMConfig.get().interacts, false, SRMConfig.get().leverBoxLineWidth, SRMConfig.get().interactsTextToggle, SRMConfig.get().interactWaypointColor, "Interact", SRMConfig.get().interactsTextSize, true);
            } else if (name.contains("Item")) {
                submitBoxAndText(boxPos, SRMConfig.get().secretsItem, false, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().itemTextToggle, SRMConfig.get().itemWaypointColor, "Item", SRMConfig.get().itemTextSize, true);
            }
        }
    }

    public static void renderLever() {
        ArrayList<JsonElement> levers = new ArrayList<>();
        JsonArray csr = getSecrets();
        String leverNum = null;
        if (csr == null) {
            SecretUtils.renderLever = false;
        }
        if (currentLeverPos == null && csr != null) {
            for (JsonElement secret : csr) {
                JsonObject secretInfos = secret.getAsJsonObject();
                String name = secretInfos.get("secretName").getAsString();
                String category = secretInfos.get("category").getAsString();
                if (category.equals("chest") && leverNum == null) {
                    BlockPos pos = Main.currentRoom.positions().secret(secretInfos).position().block();
                    if (BlockUtils.blockPos(pos).equals(BlockUtils.blockPos(lastInteract))) {
                        leverNum = name.split(" ")[0];
                        leverNumber = leverNum;
                        chestName = name;
                    }
                }
                if (category.equals("lever")) {
                    if (leverNum == null) {
                        levers.add(secret);
                    } else {
                        String[] nums = leverNum.split("/");
                        for (String num : nums) {
                            if (name.contains(num)) {
                                currentLeverPos = new BlockPos(secretInfos.get("x").getAsInt(), secretInfos.get("y").getAsInt(), secretInfos.get("z").getAsInt());
                                leverName = name;
                            }
                        }

                    }
                }

            }
        }


        if (currentLeverPos != null || !levers.isEmpty()) {
            if (currentLeverPos == null && leverNum != null) {
                for (JsonElement secret : levers) {
                    JsonObject secretInfos = secret.getAsJsonObject();
                    String name = secretInfos.get("secretName").getAsString();
                    String[] nums = leverNum.split("/");
                    for (String num : nums) {
                        if (name.contains(num)) {
                            currentLeverPos = new BlockPos(secretInfos.get("x").getAsInt(), secretInfos.get("y").getAsInt(), secretInfos.get("z").getAsInt());
                            leverName = name;
                        }
                    }
                }
            }
            if (currentLeverPos == null) {
                ChatUtils.sendChatMessage("§cLever not found :(");
            } else {
                Vector3d position = Main.currentRoom.positions().block(currentLeverPos).origin();
                if (SRMConfig.get().secretsInteractFullBlock) {
                    RenderingBackend.addFilledBox(new RenderTypes.FilledBox(position, SRMConfig.get().secretsInteract, 1f, 1f, SRMConfig.get().renderLinesThroughWalls));
                } else {
                    RenderingBackend.addOutlinedBox(new RenderTypes.OutlinedBox(position, SRMConfig.get().secretsInteract, 1f, 1f, SRMConfig.get().secretBoxLineWidth, SRMConfig.get().renderLinesThroughWalls));
                }

                if (SRMConfig.get().interactsTextToggle) {
                    Component text = Component.literal(getColorCode(SRMConfig.get().interactsWaypointColor) + "Locked chest lever");
                    RenderingBackend.addWorldText(new RenderTypes.WorldText(text, position, true, SRMConfig.get().interactsTextSize));
                }

                if (first) {
                    removeBannerTime = System.currentTimeMillis() + 5000;
                    SchedulerUtils.schedule(5000, () -> removeBannerTime = null);
                    first = false;
                }
            }
        } else {
            ChatUtils.sendChatMessage("§cLever not found :(");
        }
    }

    private static void submitBoxAndText(RoutePositionCache.Position pos, Color boxColor, boolean isFull, float boxLineWidth, boolean textToggle, SRMConfig.TextColor textColor, String textContent, float textSize, boolean shiftTextUp) {
        submitBoxAndText(pos.origin(), boxColor, isFull, boxLineWidth, textToggle, textColor, textContent, textSize,
                shiftTextUp ? pos.center() : pos.origin());
    }

    private static void submitBoxAndText(Vector3d pos, Color boxColor, boolean isFull, float boxLineWidth, boolean textToggle, SRMConfig.TextColor textColor, String textContent, float textSize, Vector3d textPos) {
        if (isFull)
            RenderingBackend.addFilledBox(new RenderTypes.FilledBox(pos, boxColor, 1, 1, SRMConfig.get().renderLinesThroughWalls));
        else
            RenderingBackend.addOutlinedBox(new RenderTypes.OutlinedBox(pos, boxColor, 1, 1, boxLineWidth, SRMConfig.get().renderLinesThroughWalls));

        if (textToggle && textContent != null) {
            RenderingBackend.addWorldText(new RenderTypes.WorldText(Component.literal(getColorCode(textColor) + textContent), textPos, true, textSize));
        }
    }

    public static JsonArray getSecrets() {
        secrets = SECRET_CACHE.get(Main.currentRoom == null ? null : Main.currentRoom.name);
        return secrets;
    }

    public static void registerResourceReload() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(Main.MODID, "secret_data"), new SimpleReloadListener<Void>() {
                    @Override
                    protected Void prepare(PreparableReloadListener.SharedState state) {
                        return null;
                    }

                    @Override
                    protected void apply(Void prepared, PreparableReloadListener.SharedState state) {
                        SECRET_CACHE.invalidate();
                        secrets = null;
                        if (Main.currentRoom != null) Main.currentRoom.invalidatePositionCache();
                    }
                });
    }

    public static void resetValues() {
        EtherwarpAimAssist.reset();
        renderLever = false;
        currentLeverPos = null;
        removeBannerTime = null;
        first = true;
        chestName = null;
        leverName = null;
        leverNumber = null;

        clearEtherwarpTargetTracking();

        brokenBlocks.clear();
    }
}
//#endif
