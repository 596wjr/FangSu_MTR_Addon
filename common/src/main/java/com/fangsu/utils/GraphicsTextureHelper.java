package com.fangsu.utils;

import com.fangsu.Main;
import com.fangsu.scripting.GraphicsTexture;
import net.minecraft.core.BlockPos;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class GraphicsTextureHelper {

    /* =========================
       单例
       ========================= */

    private static final GraphicsTextureHelper INSTANCE = new GraphicsTextureHelper();

    public static GraphicsTextureHelper getInstance() {
        return INSTANCE;
    }

    /* =========================
       字段
       ========================= */

    private final Map<String, String> blockIds = new HashMap<>();
    private final Map<String, GTInfo> loadGts = new HashMap<>();

    private final ScheduledExecutorService pool =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> future;

    private int maxFps = 10;
    private boolean closed = true;

    /* =========================
       生命周期
       ========================= */

    private GraphicsTextureHelper() {
        init();
    }

    public synchronized void init() {
        if (!closed) return;
        closed = false;
        startTicking();
    }

    public synchronized void dispose() {
        try {
            for (GTInfo info : loadGts.values()) {
                if (!info.isClosed) {
                    info.gt.close();
                    info.isClosed = true;
                }
            }
        } finally {
            stopTicking();
            pool.shutdown();
            closed = true;
        }
    }

    /* =========================
       Tick
       ========================= */

    private void startTicking() {
        long delay = 1000L / maxFps;
        future = pool.scheduleAtFixedRate(this::tick, 0, delay, TimeUnit.MILLISECONDS);
    }

    private void stopTicking() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    private void tick() {
        for (GTInfo info : loadGts.values()) {
            try {
                if (info.isStatic && info.available) continue;
                if (info.isClosed) continue;

                if (info.waitUntilDraw) {
                    info.waitUntilDraw = false;
                    continue;
                }

                Map<String, Object> detail = info.detailSupplier.get();
                Graphics2D g = info.gt.graphics;

                info.drawFunction.draw(g, detail);
                info.gt.upload();
                info.available = true;

            } catch (Throwable t) {
                Main.LOGGER.warn("Error when running draw function: {}", t.getLocalizedMessage());
            }
        }
    }

    /* =========================
       对外 API
       ========================= */

    public synchronized void addDrawGraphic(
            BlockPos block,
            DrawInfo drawInfo,
            DrawFunction drawFunction,
            DetailSupplier detailSupplier
    ) {
        String blockId = getBlockId(block);
        String drawInfoId = drawInfo.id;

        if (blockIds.containsKey(blockId)) return;
        blockIds.put(blockId, drawInfoId);

        GTInfo info = loadGts.get(drawInfoId);
        if (info != null && !info.isClosed) {
            if (!info.blocks.contains(block)) {
                info.blocks.add(block);
            }
            return;
        }

        info = new GTInfo();
        info.blocks.add(block);
        info.drawFunction = drawFunction;
        info.detailSupplier = detailSupplier;
        info.gt = new GraphicsTexture(drawInfo.w, drawInfo.h);
        info.isStatic = drawInfo.isStatic;
        info.waitUntilDraw = drawInfo.waitUntilDraw;

        loadGts.put(drawInfoId, info);
    }

    public synchronized void removeDrawGraphic(BlockPos block) {
        String blockId = getBlockId(block);
        String drawInfoId = blockIds.remove(blockId);
        if (drawInfoId == null) return;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null) return;

        info.blocks.remove(block);
        if (info.blocks.isEmpty()) {
            info.gt.closeLater();
            info.isClosed = true;
            loadGts.remove(drawInfoId);
        }
    }

    public GraphicsTexture getBlockGraphics(BlockPos block) {
        String drawInfoId = blockIds.get(getBlockId(block));
        if (drawInfoId == null) return null;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || !info.available) return null;

        return info.gt;
    }

    public boolean hasDrawGraphic(BlockPos block) {
        String drawInfoId = blockIds.get(getBlockId(block));
        if (drawInfoId == null) return false;
        return loadGts.containsKey(drawInfoId) && loadGts.get(drawInfoId).available;
    }

    public synchronized void setMaxFps(int fps) {
        this.maxFps = fps;
        stopTicking();
        startTicking();
    }

    /* =========================
       工具方法
       ========================= */

    private static String getBlockId(BlockPos pos) {
        return "block_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /* =========================
       内部结构
       ========================= */

    public static class GTInfo {
        List<BlockPos> blocks = new ArrayList<>();
        GraphicsTexture gt;

        DrawFunction drawFunction;
        DetailSupplier detailSupplier;

        boolean available = false;
        boolean isClosed = false;
        boolean isStatic = false;
        boolean waitUntilDraw = false;
    }

    public record DrawInfo(String id, int w, int h, boolean isStatic, boolean waitUntilDraw) {
    }

    @FunctionalInterface
    public interface DrawFunction {
        void draw(Graphics2D g, Map<String, Object> detail);
    }

    @FunctionalInterface
    public interface DetailSupplier {
        Map<String, Object> get();
    }
}
