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
                        info.gt.isValid() && !info.redrawNeeded) {
                    continue;
                }
                if (info.isClosed) continue;
                if (info.drawing) continue;        // 正在绘制中，跳过
                if (!info.flameCompleted) continue; // 本帧尚未完成，等待下一帧

                if (info.waitUntilDraw) {
                    info.waitUntilDraw = false;
                    continue;
                }

                // 静态贴图：如果已标记为可用且无需重绘，不再重复绘制
                if (info.isStatic && info.available && !info.redrawNeeded) continue;

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
                info.redrawNeeded = false;

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
     * 替换已注册抽象 ID 的绘制函数（不重建纹理，保留纹理现有内容）。
     * 会重置 redrawNeeded 和 flameCompleted 标记，使 tick 循环能重新执行新的绘制函数。
     * 同时清除旧纹理内容，防止在下次绘制完成前显示残留的旧画面（不清屏重叠绘制）。
     */
    public synchronized void replaceDrawFunction(String id, DrawFunctionGt drawFunction) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || info.isClosed) return;

        info.drawFunction = drawFunction;
        info.retryCount = 0;
        // 标记需要重绘，使 tick 循环能跳过静态纹理跳过检查
        info.redrawNeeded = true;
        // 重置帧完成标记，使 tick 循环通过 !flameCompleted 检查（非首次时的保护）
        info.flameCompleted = true;
        // 清除旧纹理内容，防止在下次绘制完成前显示残留的旧画面
        clearTextureContent(info);
    }

    /**
     * 清除纹理的 BufferedImage 内容（填充完全透明），
     * 并立即上传到 GPU，避免旧内容在新绘制完成前显示。
     */
    private void clearTextureContent(GTInfo info) {
        if (info.gt == null || info.gt.graphics == null || info.gt.bufferedImage == null) return;
        try {
            final java.awt.Graphics2D g = info.gt.graphics;
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, info.gt.width, info.gt.height);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            info.gt.upload();
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to clear texture content for {}: {}", info.ids, e.getMessage());
        }
    }

    /**
     * 将抽象 ID 绑定到已存在的 drawInfoId 纹理上（共享模式）。
     * <p>
     * 与 {@link #addDrawGraphicWithGt} 不同，此方法假定 drawInfoId 已在 {@code loadGts} 中存在，
     * 仅建立 id → drawInfoId 的映射，不创建新纹理、不替换绘制函数、不触发重绘。
     * 用于多个方块显示完全相同内容时共享同一 GPU 纹理。
     *
     * @param id         抽象 ID
     * @param drawInfoId 已存在的绘制内容标识
     */
    public synchronized void bindToExistingDrawInfo(String id, String drawInfoId) {
        if (id == null || drawInfoId == null) return;

        // 如果已绑定到同一个 drawInfoId，无需操作
        final String existingDrawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId.equals(existingDrawInfoId)) return;

        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || info.isClosed) return;

        // 先解绑旧的 drawInfoId（如果有）
        if (existingDrawInfoId != null) {
            GTInfo oldInfo = loadGts.get(existingDrawInfoId);
            if (oldInfo != null) {
                oldInfo.ids.remove(id);
                if (oldInfo.ids.isEmpty()) {
                    oldInfo.gt.closeLater();
                    oldInfo.isClosed = true;
                    loadGts.remove(existingDrawInfoId);
                }
            }
        }

        idToDrawInfoId.put(id, drawInfoId);
        if (!info.ids.contains(id)) {
            info.ids.add(id);
        }
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

    /**
     * 检查指定 {@code drawInfoId} 是否已被注册（无论是否通过当前 abstract ID）。
     * 用于调用方在注册前判断是否有其他方块已注册了相同内容的纹理，从而实现共享。
     *
     * @param drawInfoId 绘制内容标识
     * @return 如果已有相同 drawInfoId 的 GTInfo 且未关闭，返回 true
     */
    public synchronized boolean hasDrawInfoId(String drawInfoId) {
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        return info != null && !info.isClosed;
    }

    /**
     * 获取指定 {@code drawInfoId} 对应的 GraphicsTexture（若有）。
     * 用于共享方直接在渲染时获取已由首个注册方绘制完成的纹理。
     *
     * @param drawInfoId 绘制内容标识
     * @return GraphicsTexture 或 null
     */
    public synchronized GraphicsTexture getGraphicsByDrawInfoId(String drawInfoId) {
        if (drawInfoId == null) return null;
        GTInfo info = loadGts.get(drawInfoId);
        if (info == null) return null;
        if (info.available) {
            info.markFlameCompleted();
        }
        return info.gt;
    }

    /**
     * 判断指定 {@code drawInfoId} 对应的纹理是否已就绪可用。
     */
    public synchronized boolean isDrawInfoAvailable(String drawInfoId) {
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        return info != null && info.available;
    }

    /**
     * 判断抽象 ID 是否有已注册的绘制条目（无论是否可用）。
     * 用于决定是替换绘制函数还是创建新的纹理。
     */
    public synchronized boolean hasRegisteredGraphic(String id) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        return info != null && !info.isClosed;
    }

    public synchronized boolean hasRegisteredGraphic(BlockPos block) {
        return hasRegisteredGraphic(getBlockId(block));
    }

    /**
     * 检查已注册条目的纹理尺寸是否与指定尺寸匹配。
     * 用于决定是替换绘制函数还是重建纹理。
     *
     * @param id 抽象 ID
     * @param w  期望宽度
     * @param h  期望高度
     * @return 如果已有纹理且尺寸完全匹配返回 true；否则返回 false
     */
    public synchronized boolean getRegisteredGraphicSize(String id, int w, int h) {
        String drawInfoId = idToDrawInfoId.get(id);
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || info.isClosed || info.gt == null) return false;
        return info.gt.width == w && info.gt.height == h;
    }

    public synchronized boolean getRegisteredGraphicSize(BlockPos block, int w, int h) {
        return getRegisteredGraphicSize(getBlockId(block), w, h);
    }

    /**
     * 按 drawInfoId 查询已注册纹理的尺寸是否匹配。
     * 用于跨方块共享判断：其他方块的纹理尺寸是否与当前需求匹配。
     */
    public synchronized boolean getRegisteredGraphicSizeByDrawInfoId(String drawInfoId, int w, int h) {
        if (drawInfoId == null) return false;
        GTInfo info = loadGts.get(drawInfoId);
        if (info == null || info.isClosed || info.gt == null) return false;
        return info.gt.width == w && info.gt.height == h;
    }

    /**
     * 获取抽象 ID 当前绑定的 drawInfoId。
     */
    public synchronized String getDrawInfoIdForId(String id) {
        return idToDrawInfoId.get(id);
    }

    public synchronized String getDrawInfoIdForId(BlockPos block) {
        return getDrawInfoIdForId(getBlockId(block));
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

    /**
     * 替换已注册 BlockPos 的绘制函数（不重建纹理，保留纹理现有内容）。
     * 用于参数变更时避免因为重建纹理而出现短暂黑色闪烁。
     */
    public synchronized void replaceDrawFunction(BlockPos block, DrawFunctionGt drawFunction) {
        replaceDrawFunction(getBlockId(block), drawFunction);
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
        /**
         * 需要重新绘制的标记。当替换绘制函数时设为 true，
         * tick 循环不会因静态纹理检查而跳过此条目。
         * 在下一次绘制完成后自动重置为 false。
         */
        volatile boolean redrawNeeded = false;

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