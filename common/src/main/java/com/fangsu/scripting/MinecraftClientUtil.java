package com.fangsu.scripting;

import com.fangsu.render.sowcer.math.Vector3f;
import com.mojang.text2speech.Narrator;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import mtr.data.Rail;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;
import mtr.client.ClientData;
import mtr.data.RailwayData;
import mtr.data.Station;
import mtr.data.Platform;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderCall;
import net.minecraft.client.renderer.LevelRenderer;


import java.util.HashMap;
import java.util.Map;

public class MinecraftClientUtil {

    public static boolean worldIsRaining() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.isRaining();
    }

    public static boolean worldIsRainingAt(Vector3f pos) {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.isRainingAt(pos.toBlockPos());
    }

    public static int worldDayTime() {
        return Minecraft.getInstance().level != null
                ? (int) Minecraft.getInstance().level.getDayTime() : 0;
    }

    public static void narrate(String message) {
        Minecraft.getInstance().execute(() -> {
            Narrator.getNarrator().say(message, true);
        });
    }

    public static void displayMessage(String message, boolean actionBar) {
        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            Minecraft.getInstance().execute(() -> {
                player.displayClientMessage(Text.literal(message), actionBar);
            });
        }
    }

    public static void execute(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }

    public static void recordRenderCall(RenderCall renderCall) {
        RenderSystem.recordRenderCall(renderCall);
    }

    public static boolean isOnRenderThreadOrInit() {
        return RenderSystem.isOnRenderThreadOrInit();
    }

    public static void levelEvent(int p_109534_, Vector3f p_109535_, int p_109536_) {
        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().level.levelEvent(player, p_109534_, p_109535_.toBlockPos(), p_109536_);
            });
        }
    }

    public static int getOccupiedAspect(Vector3f vPos, float facing, int aspects) {
        BlockPos pos = vPos.toBlockPos();
        Map<BlockPos, Float> nodesToScan = new HashMap<>();
        nodesToScan.put(pos, facing);
        int occupiedAspect = -1;

        for (int j = 1; j < aspects; j++) {
            final Map<BlockPos, Float> newNodesToScan = new HashMap<>();

            for (final Map.Entry<BlockPos, Float> checkNode : nodesToScan.entrySet()) {
                final Map<BlockPos, Rail> railMap = ClientData.RAILS.get(checkNode.getKey());

                if (railMap != null) {
                    for (final BlockPos endPos : railMap.keySet()) {
                        final Rail rail = railMap.get(endPos);

                        if (rail.facingStart.similarFacing(checkNode.getValue())) {
                            if (ClientData.SIGNAL_BLOCKS.isOccupied(PathData.getRailProduct(checkNode.getKey(), endPos))) {
                                return j;
                            } else {
                                final Boolean isOccupied = ClientData.OCCUPIED_RAILS.get(PathData.getRailProduct(checkNode.getKey(), endPos));
                                if (isOccupied != null && isOccupied) {
                                    return j;
                                }
                            }

                            newNodesToScan.put(endPos, rail.facingEnd.getOpposite().angleDegrees);
                            occupiedAspect = 0;
                        }
                    }
                }
            }

            nodesToScan = newNodesToScan;
        }

        return occupiedAspect;
    }

    public static Station getStationAt(Vector3f pos) {
        return RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, pos.toBlockPos());
    }

    public static Platform getPlatformAt(Vector3f pos, int radius, int lower, int upper) {
        Station station = RailwayData.getStation(ClientData.STATIONS, ClientData.DATA_CACHE, pos.toBlockPos());
        Map<Long, Platform> platformPositions = ClientData.DATA_CACHE.requestStationIdToPlatforms(station.id);
        Long id = RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos.toBlockPos(), radius, lower, upper);
        Platform platform = platformPositions.get(id);
        return platform;
    }

    public static Vector3f getCameraPos() {
        //#if MC_VERSION >= 11903
        return new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().toVector3f());
        //#else
        //$$ net.minecraft.world.phys.Vec3 pos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //$$ return new Vector3f((float) pos.x, (float) pos.y, (float) pos.z);
        //#endif
    }

    public static float getCameraDistance(Vector3f from) {
        Vector3f cameraPos = getCameraPos();
        return cameraPos.distance(from);
    }

    public static Level getLevel() {
        return Minecraft.getInstance().level;
    }

    static double getAverage(double a, double b) {
        return (a + b) / 2;
    }

    static double asin(double value) {
        return Math.asin(value);
    }

    public static int packLightTexture(int p_109886_, int p_109887_) {
        return p_109886_ << 4 | p_109887_ << 20;
    }

    public static int getLightColor(Vector3f pos) {
        return LevelRenderer.getLightColor(getLevel(), pos.toBlockPos());
    }

    public static void reloadResourcePacks() {
        execute(Minecraft.getInstance()::reloadResourcePacks);
    }

    public static void markRendererAllChanged() {
        Minecraft.getInstance().levelRenderer.allChanged();
    }
}