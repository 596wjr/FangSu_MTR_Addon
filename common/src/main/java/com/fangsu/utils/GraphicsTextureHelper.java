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

    // 抽象 ID -> DrawInfo.id
    private final Map<String, String> idToDrawInfoId = new ConcurrentHashMap<>();
    private final Map<String, GTInfo> loadGts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService pool =
            Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> future;

    private int maxFps = 10;
    private boolean closed = true;

    private final ExecutorService drawExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "graphics-texture-draw");
        t.setDaemon(true);
        return t;
    });

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
            drawExecutor.shutdownNow();
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
            if (info.needsUpload) {
                info.gt.upload();
                info.needsUpload = false;
                info.flameCompleted = false;
            }
        }

        for (GTInfo info : loadGts.values()) {
            try {
                // 静态纹理已上传完成（needsUpload 已由第一个循环处理），无需每 tick 重复 upload
                if (info.isStatic && info.available &&
                        info.gt.isValid()) {
                    continue;
                }
                if (info.isClosed) continue;
                if (info.drawing) continue;        // 正在绘制中，跳过
                if (!info.flameCompleted) continue; // 本帧尚未完成，等待下一帧

                if (info.waitUntilDraw) {
                    info.waitUntilDraw = false;
                    continue;
                }

                // 静态贴图：如果已标记为可用，不再重复绘制
                if (info.isStatic && info.available) continue;

                // 超过最大重试次数，放弃
                if (info.retryCount >= GTInfo.MAX_RETRIES) {
                    if (info.retryCount == GTInfo.MAX_RETRIES) {
                        info.retryCount++;
                        Main.LOGGER.warn("Draw failed after {} retries for {}, giving up", GTInfo.MAX_RETRIES, info.ids);
                    }
                    continue;
                }

                info.drawing = true;
                info.flameCompleted = false;
                // 非静态贴图：保持纹理可用，渲染线程可继续显示旧内容，避免闪烁

                CompletableFuture.runAsync(() -> {
                            info.drawFunction.draw(info.gt);
                            info.available = true;
                            info.needsUpload = true;
                            info.retryCount = 0; // 成功绘制后重置重试计数
                        }, drawExecutor).orTimeout(200, TimeUnit.MILLISECONDS)
                        .exceptionally(t -> {
                            // 超时或报错：重置 flameCompleted 使下次 tick 可重试
                            info.flameCompleted = true;
                            info.retryCount++;
                            Main.LOGGER.warn("Draw failed (attempt {}/{}) for {}: {}",
                                    info.retryCount, GTInfo.MAX_RETRIES, info.ids, t.getMessage());
                            return null;
                        })
                        .thenRun(() -> info.drawing = false);

            } catch (Throwable t) {
                Main.LOGGER.warn("Error when running draw function: {}", t.getLocalizedMessage());
            }
        }
    }

    /* =========================
       对外 API（通用 ID 版本）
       ========================= */

    /**
     * 为一个抽象 ID 绑定图形纹理
     */
    public synchronized void addDrawGraphicWithGt(
            String id,
            DrawInfo drawInfo,
            DrawFunctionGt drawFunction
    ) {
        String drawInfoId = drawInfo.id;

        // 同一 ID 只能绑定一次
        if (idToDrawInfoId.containsKey(id)) return;
        idToDrawInfoId.put(id, drawInfoId);

        GTInfo info = loadGts.get(drawInfoId);
        if (info != null && !info.isClosed) {
            if (!info.ids.contains(id)) {
                info.ids.add(id);
            }
            return;
        }

        info = new GTInfo();
        info.ids.add(id);
        info.drawFunction = drawFunction;
        info.gt = new GraphicsTexture(drawInfo.w, drawInfo.h);
        info.isStatic = drawInfo.isStatic;
        info.waitUntilDraw = drawInfo.waitUntilDraw;

        loadGts.put(drawInfoId, info);
    }

    public synchronized void addDrawGraphic(String id,
                                            DrawInfo drawInfo,
                                            DrawFunction drawFunction) {
        addDrawGraphicWithGt(id, drawInfo, (gt) -> {
            drawFunction.draw(gt.graphics);
        });
    }

    /**
     * 替换已注册抽象 ID 的绘制函数（不重建纹理，保留纹理现有内容）
     */
    public synchronized void replaceDrawFunction(String id, DrawFunctionGt drawFunction) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || info.isClosed) return;

        info.drawFunction = drawFunction;
        info.retryCount = 0;
    }

    /**
     * 移除一个抽象 ID 的绑定
     */
    public synchronized void removeDrawGraphic(String id) {
        String drawInfoId = idToDrawInfoId.remove(id);
        if (drawInfoId == null) return;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null) return;

        info.ids.remove(id);
        if (info.ids.isEmpty()) {
            info.gt.closeLater();
            info.isClosed = true;
            loadGts.remove(drawInfoId);
        }
    }

    /**
     * 清除所有以指定前缀开头的绘制条目。用于世界重载时清理旧数据。
     */
    public synchronized void removeDrawGraphicsByPrefix(String idPrefix) {
        List<String> toRemove = new ArrayList<>();
        for (String id : idToDrawInfoId.keySet()) {
            if (id.startsWith(idPrefix)) {
                toRemove.add(id);
            }
        }
        for (String id : toRemove) {
            removeDrawGraphic(id);
        }
    }

    /**
     * 获取抽象 ID 对应的 GraphicsTexture
     */
    public synchronized GraphicsTexture getGraphics(String id) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return null;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null) {
            return null;
        }
        if (info.available) {
            info.markFlameCompleted();
        }
        return info.gt;
    }

    /**
     * 判断抽象 ID 是否有可用的图形
     */
    public synchronized boolean hasGraphic(String id) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        return info != null && info.available;
    }

    public synchronized boolean isTextureAvailable(String id) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        return info != null && info.available;
    }

    /* =========================
       对外 API（BlockPos 兼容版本）
       ========================= */

    public synchronized void addDrawGraphic(
            BlockPos block,
            DrawInfo drawInfo,
            DrawFunction drawFunction
    ) {
        addDrawGraphic(getBlockId(block), drawInfo, drawFunction);
    }

    public synchronized void addDrawGraphicWithGt(
            BlockPos block,
            DrawInfo drawInfo,
            DrawFunctionGt drawFunction
    ) {
        addDrawGraphicWithGt(getBlockId(block), drawInfo, drawFunction);
    }

    public synchronized void removeDrawGraphic(BlockPos block) {
        removeDrawGraphic(getBlockId(block));
    }

    public GraphicsTexture getBlockGraphics(BlockPos block) {
        return getGraphics(getBlockId(block));
    }

    public boolean hasDrawGraphic(String id) {
        String drawInfoId = idToDrawInfoId.get(id);
        return drawInfoId != null && hasGraphic(drawInfoId);
    }

    public boolean hasDrawGraphic(BlockPos block) {
        return hasGraphic(getBlockId(block));
    }

    public boolean isTextureAvailable(BlockPos block) {
        return hasGraphic(getBlockId(block));
    }

    /* =========================
       动态配置
       ========================= */

    public synchronized void setMaxFps(int fps) {
        this.maxFps = fps;
        stopTicking();
        startTicking();
    }

    /* =========================
       工具方法
       ========================= */

    /**
     * 将 BlockPos 转为内部使用的 ID
     */
    private static String getBlockId(BlockPos pos) {
        return "block_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /* =========================
       内部结构
       ========================= */

    private static class GTInfo {
        List<String> ids = new ArrayList<>();   // 绑定的抽象 ID 列表
        GraphicsTexture gt;

        DrawFunctionGt drawFunction;

        volatile boolean available = false;
        boolean isClosed = false;
        boolean isStatic = false;
        boolean waitUntilDraw = false;

        volatile boolean flameCompleted = true;

        volatile boolean drawing = false;
        volatile boolean needsUpload = false;

        int expectedExceptionCount = 0;

        /**
         * 当前绘制失败/超时的重试次数
         */
        volatile int retryCount = 0;
        /**
         * 最大重试次数
         */
        static final int MAX_RETRIES = 5;

        @Override
        public String toString() {
            return "GTInfo [ids=" + ids + ", gt=" + gt + ", drawFunction=" + drawFunction + ", available=" + available + ", isClosed=" + isClosed + ", isStatic=" + isStatic + ", waitUntilDraw=" + waitUntilDraw + "]@" + hashCode();
        }

        public void markFlameCompleted() {
            flameCompleted = true;
        }
    }

    public record DrawInfo(String id, int w, int h, boolean isStatic, boolean waitUntilDraw) {
    }

    @FunctionalInterface
    public interface DrawFunction {
        void draw(Graphics2D g);
    }

    @FunctionalInterface
    public interface DrawFunctionGt {
        void draw(GraphicsTexture gt);
    }
}