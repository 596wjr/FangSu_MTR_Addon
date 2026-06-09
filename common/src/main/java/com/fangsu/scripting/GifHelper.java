package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * GIF 单实例工具类——集中管理所有 GIF 动画的加载、播放与生命周期。
 * 内部维护 GifInstance 来追踪每个 GIF 的帧数据、定时调度与 GraphicsTexture。
 * 对外提供按 BlockPos 或 抽象ID 绑定/解绑的 API，风格与 GraphicsTextureHelper 一致。
 */
public class GifHelper {

    /* =========================
       单例
       ========================= */

    private static final GifHelper INSTANCE = new GifHelper();

    public static GifHelper getInstance() {
        return INSTANCE;
    }

    /* =========================
       调度器 & 解析线程池
       ========================= */

    /**
     * 帧切换定时调度器（单线程）
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GifHelper-Scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * GIF 文件解析线程池（独立于调度器，避免解析阻塞帧切换）
     */
    private final ExecutorService parserExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "GifHelper-Parser");
        t.setDaemon(true);
        return t;
    });

    /* =========================
       内部数据结构
       ========================= */

    /**
     * 资源 location -> GifInstance（复用已解析的 GIF 数据）
     */
    private final Map<ResourceLocation, GifInstance> gifCache = new ConcurrentHashMap<>();

    /**
     * 抽象 ID（如 BlockPos.toString）-> 当前使用的 ResourceLocation
     */
    private final Map<String, ResourceLocation> bindingMap = new ConcurrentHashMap<>();

    /**
     * 绑定 ID -> 最新纹理 identifier（纹理替换时更新，供 getGifTextureLocation 返回正确值）
     */
    private final Map<String, ResourceLocation> bindingTextureMap = new ConcurrentHashMap<>();

    /* =========================
       构造
       ========================= */

    private GifHelper() {
    }

    /* =========================
       内部类：单个 GIF 实例
       ========================= */

    private static class GifInstance implements AutoCloseable {
        GraphicsTexture texture;
        volatile BufferedImage[] frames;
        volatile int[] delays;          // 每帧延迟（毫秒）
        final AtomicInteger currentFrame = new AtomicInteger(0);
        final AtomicBoolean playing = new AtomicBoolean(true);
        final AtomicBoolean closed = new AtomicBoolean(false);
        volatile int loopCount;         // 0 = 无限循环
        final AtomicInteger loopCounter = new AtomicInteger(0);
        final ResourceLocation sourceLocation;

        /**
         * 标记是否已经完成异步加载
         */
        final AtomicBoolean loaded = new AtomicBoolean(false);

        // 引用计数（有多少个绑定在使用此实例）
        final AtomicInteger refCount = new AtomicInteger(1);

        // 当前调度的 Future，用于取消
        ScheduledFuture<?> scheduledFuture;

        /** 纹理被替换时的回调列表（用于通知 GifHelper 更新绑定映射） */
        final List<Runnable> onTextureReplacedCallbacks = new ArrayList<>();

        GifInstance(ResourceLocation location, int w, int h) {
            this.sourceLocation = location;
            // 先创建纹理占位，等加载完成后更新
            this.texture = new GraphicsTexture(Math.max(w, 1), Math.max(h, 1));
        }

        /**
         * 在后台线程解析完成后调用，设置帧数据并启动动画。
         * 在渲染线程调用以确保纹理操作安全。
         */
        void onFramesLoaded(BufferedImage[] loadedFrames, int[] loadedDelays, int loadedLoopCount) {
            if (closed.get()) return;
            this.frames = loadedFrames;
            this.delays = loadedDelays;
            this.loopCount = loadedLoopCount;
            this.loaded.set(true);

            if (frames.length > 0) {
                BufferedImage firstFrame = frames[0];
                // 如果占位纹理尺寸与真实帧不匹配，重新创建纹理
                if (texture.width != firstFrame.getWidth() || texture.height != firstFrame.getHeight()) {
                    GraphicsTexture oldTex = texture;
                    texture = new GraphicsTexture(firstFrame.getWidth(), firstFrame.getHeight());
                    oldTex.close();
                    // 通知 GifHelper 更新所有使用此 GIF 的绑定的纹理位置
                    for (Runnable cb : onTextureReplacedCallbacks) {
                        cb.run();
                    }
                }
                texture.updateTexture(firstFrame);
                if (frames.length > 1) {
                    startAnimation();
                }
            }
        }

        boolean isSingleFrame() {
            BufferedImage[] f = frames;
            return f == null || f.length <= 1;
        }

        private void startAnimation() {
            scheduleNextFrame();
        }

        private synchronized void scheduleNextFrame() {
            if (closed.get() || !playing.get()) return;

            BufferedImage[] f = frames;
            int[] d = delays;
            if (f == null || d == null || f.length == 0) return;

            int idx = currentFrame.get();
            int delay = d[idx % d.length];

            scheduledFuture = INSTANCE.scheduler.schedule(() -> {
                if (closed.get() || !playing.get()) return;

                BufferedImage[] curFrames = frames;
                if (curFrames == null || curFrames.length == 0) return;

                // 移动到下一帧
                int next = (idx + 1) % curFrames.length;

                // 循环计数检查
                if (next == 0 && loopCount > 0) {
                    int loops = loopCounter.incrementAndGet();
                    if (loops >= loopCount) {
                        playing.set(false);
                        return;
                    }
                }

                currentFrame.set(next);

                // 在 Minecraft 渲染线程上更新纹理
                BufferedImage frameImage = curFrames[next];
                Minecraft.getInstance().tell(() -> {
                    if (!closed.get() && texture != null) {
                        texture.updateTexture(frameImage);
                    }
                });

                // 调度下一帧
                scheduleNextFrame();
            }, delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            playing.set(false);
            synchronized (this) {
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                    scheduledFuture = null;
                }
            }
            Minecraft.getInstance().tell(() -> {
                if (texture != null) {
                    texture.close();
                }
            });
        }
    }

    // ========== GIF 解析（静态） ==========

    private static class GifData {
        final BufferedImage[] frames;
        final int[] delays;
        final int loopCount;

        GifData(BufferedImage[] frames, int[] delays, int loopCount) {
            this.frames = frames;
            this.delays = delays;
            this.loopCount = loopCount;
        }
    }

    private static GifData parseGif(byte[] data) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        List<BufferedImage> frameList = new ArrayList<>();
        List<Integer> delayList = new ArrayList<>();
        int loopCount = 0;

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(iis, false);
            loopCount = getLoopCount(reader);
            int numFrames = reader.getNumImages(true);

            // 获取 GIF 逻辑屏幕尺寸（画布尺寸）
            int canvasW = 1, canvasH = 1;
            try {
                var streamMeta = reader.getStreamMetadata();
                if (streamMeta != null) {
                    var root = streamMeta.getAsTree(streamMeta.getNativeMetadataFormatName());
                    if (root instanceof IIOMetadataNode) {
                        var child = ((IIOMetadataNode) root).getFirstChild();
                        while (child != null) {
                            if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                                String sw = ((IIOMetadataNode) child).getAttribute("logicalScreenWidth");
                                String sh = ((IIOMetadataNode) child).getAttribute("logicalScreenHeight");
                                if (sw != null && !sw.isEmpty()) canvasW = Integer.parseInt(sw);
                                if (sh != null && !sh.isEmpty()) canvasH = Integer.parseInt(sh);
                                break;
                            }
                            child = child.getNextSibling();
                        }
                    }
                }
            } catch (Exception e) {
                // fallback: 用第一帧尺寸
                canvasW = reader.getWidth(0);
                canvasH = reader.getHeight(0);
            }
            if (canvasW <= 0) canvasW = 1;
            if (canvasH <= 0) canvasH = 1;

            // 用于帧合成的累积画布
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvasG = canvas.createGraphics();

            for (int i = 0; i < numFrames; i++) {
                // 读取原始帧（可能是增量帧，尺寸可能小于画布）
                BufferedImage rawFrame = reader.read(i);

                // 获取 disposal method 和帧偏移
                int disposalMethod = getDisposalMethod(reader, i);
                int[] offset = getFrameOffset(reader, i);
                int frameX = offset[0];
                int frameY = offset[1];

                // 根据 disposal method 处理
                if (i > 0 && disposalMethod == 2) {
                    // Dispose to background color: 清除当前帧区域为透明
                    int fw = rawFrame.getWidth();
                    int fh = rawFrame.getHeight();
                    canvasG.setComposite(AlphaComposite.Clear);
                    canvasG.fillRect(frameX, frameY, fw, fh);
                    canvasG.setComposite(AlphaComposite.SrcOver);
                }

                // 将原始帧绘制到画布的对应位置
                canvasG.drawImage(rawFrame, frameX, frameY, null);

                // 复制完整画布作为当前帧
                BufferedImage fullFrame = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D fullG = fullFrame.createGraphics();
                fullG.drawImage(canvas, 0, 0, null);
                fullG.dispose();
                frameList.add(fullFrame);

                int delay = getFrameDelay(reader, i);
                delayList.add(delay);

                // disposal method 3 (Restore to previous): 需要在下一次迭代前恢复画布
                // 但对于简单的实现，我们不处理 method 3（极少使用）
                // disposal method 1 (No disposal): 保留画布不变（默认行为）
                // disposal method 0 (No disposal specified): 同上
            }

            canvasG.dispose();
        } finally {
            reader.dispose();
        }

        return new GifData(
                frameList.toArray(new BufferedImage[0]),
                delayList.stream().mapToInt(Integer::intValue).toArray(),
                loopCount
        );
    }

    private static int getLoopCount(ImageReader reader) {
        try {
            var metadata = reader.getStreamMetadata();
            if (metadata != null) {
                var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
                if (root instanceof IIOMetadataNode) {
                    var child = ((IIOMetadataNode) root).getFirstChild();
                    while (child != null) {
                        if ("ApplicationExtensions".equals(child.getNodeName())) {
                            var ext = child.getFirstChild();
                            while (ext != null) {
                                if ("ApplicationExtension".equals(ext.getNodeName())) {
                                    var attrs = ((IIOMetadataNode) ext).getUserObject();
                                    if (attrs instanceof byte[] b && b.length >= 4
                                            && b[0] == 0x4E && b[1] == 0x45 && b[2] == 0x54 && b[3] == 0x53) {
                                        var extData = ext.getNextSibling();
                                        if (extData != null) {
                                            var loopBytes = ((IIOMetadataNode) extData).getUserObject();
                                            if (loopBytes instanceof byte[] lb && lb.length >= 3 && lb[0] == 0x01) {
                                                return (lb[1] & 0xFF) | ((lb[2] & 0xFF) << 8);
                                            }
                                        }
                                    }
                                }
                                ext = ext.getNextSibling();
                            }
                        }
                        child = child.getNextSibling();
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to read GIF loop count", e);
        }
        return 0;
    }

    private static int getFrameDelay(ImageReader reader, int frameIndex) {
        try {
            var metadata = reader.getImageMetadata(frameIndex);
            var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            if (root instanceof IIOMetadataNode) {
                var child = ((IIOMetadataNode) root).getFirstChild();
                while (child != null) {
                    if ("GraphicControlExtension".equals(child.getNodeName())) {
                        var delayAttr = ((IIOMetadataNode) child).getAttribute("delayTime");
                        if (delayAttr != null && !delayAttr.isEmpty()) {
                            int delay = Integer.parseInt(delayAttr) * 10;
                            return Math.max(delay, 10);
                        }
                    }
                    child = child.getNextSibling();
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to read frame delay for frame {}", frameIndex, e);
        }
        return 100;
    }

    /**
     * 获取帧的 disposal method（0=无指定, 1=不处理, 2=恢复为背景色, 3=恢复为前一帧）
     * <p>
     * 注意：javax.imageio 的 GIF 插件中 disposalMethod 属性可能是字符串形式
     * （如 "doNotDispose", "restoreToBackgroundColor" 等），也可能是数字。
     * 这里统一做数值转换。
     */
    private static int getDisposalMethod(ImageReader reader, int frameIndex) {
        try {
            var metadata = reader.getImageMetadata(frameIndex);
            var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            if (root instanceof IIOMetadataNode) {
                var child = ((IIOMetadataNode) root).getFirstChild();
                while (child != null) {
                    if ("GraphicControlExtension".equals(child.getNodeName())) {
                        var disposalAttr = ((IIOMetadataNode) child).getAttribute("disposalMethod");
                        if (disposalAttr != null && !disposalAttr.isEmpty()) {
                            return parseDisposalMethod(disposalAttr);
                        }
                    }
                    child = child.getNextSibling();
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to read disposal method for frame {}", frameIndex, e);
        }
        return 0;
    }

    /**
     * 将 disposalMethod 属性值解析为数字。
     * javax.imageio 可能返回数字字符串或枚举名称字符串。
     */
    private static int parseDisposalMethod(String value) {
        if (value == null || value.isEmpty()) return 0;
        // 先尝试数字解析
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        // 字符串映射
        switch (value) {
            case "doNotDispose":
            case "none":
                return 1;
            case "restoreToBackgroundColor":
            case "backgroundColor":
                return 2;
            case "restoreToPrevious":
            case "previous":
                return 3;
            default:
                return 0;
        }
    }

    /**
     * 获取帧在画布上的偏移量 (x, y)
     */
    private static int[] getFrameOffset(ImageReader reader, int frameIndex) {
        try {
            var metadata = reader.getImageMetadata(frameIndex);
            var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            if (root instanceof IIOMetadataNode) {
                var child = ((IIOMetadataNode) root).getFirstChild();
                while (child != null) {
                    if ("ImageDescriptor".equals(child.getNodeName())) {
                        int x = 0, y = 0;
                        var xAttr = ((IIOMetadataNode) child).getAttribute("imageLeftPosition");
                        var yAttr = ((IIOMetadataNode) child).getAttribute("imageTopPosition");
                        if (xAttr != null && !xAttr.isEmpty()) x = Integer.parseInt(xAttr);
                        if (yAttr != null && !yAttr.isEmpty()) y = Integer.parseInt(yAttr);
                        return new int[]{x, y};
                    }
                    child = child.getNextSibling();
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to read frame offset for frame {}", frameIndex, e);
        }
        return new int[]{0, 0};
    }

    // ========== 内部方法 ==========

    /**
     * 快速读取 GIF 画布尺寸，不进行完整解析。
     */
    private static int[] readGifCanvasSize(byte[] data) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(iis, false);
                var streamMeta = reader.getStreamMetadata();
                if (streamMeta != null) {
                    var root = streamMeta.getAsTree(streamMeta.getNativeMetadataFormatName());
                    if (root instanceof IIOMetadataNode) {
                        var child = ((IIOMetadataNode) root).getFirstChild();
                        while (child != null) {
                            if ("LogicalScreenDescriptor".equals(child.getNodeName())) {
                                String sw = ((IIOMetadataNode) child).getAttribute("logicalScreenWidth");
                                String sh = ((IIOMetadataNode) child).getAttribute("logicalScreenHeight");
                                int w = 1, h = 1;
                                if (sw != null && !sw.isEmpty()) w = Integer.parseInt(sw);
                                if (sh != null && !sh.isEmpty()) h = Integer.parseInt(sh);
                                return new int[]{Math.max(w, 1), Math.max(h, 1)};
                            }
                            child = child.getNextSibling();
                        }
                    }
                }
                // fallback: 第一帧尺寸
                return new int[]{Math.max(reader.getWidth(0), 1), Math.max(reader.getHeight(0), 1)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return new int[]{16, 16};
        }
    }

    /**
     * 获取或创建 GifInstance（带引用计数）。
     * 立即返回占位实例，GIF 解析在后台线程异步执行，不阻塞渲染线程。
     */
    private GifInstance getOrCreateInstance(ResourceLocation location) {
        GifInstance existing = gifCache.get(location);
        if (existing != null && !existing.closed.get()) {
            existing.refCount.incrementAndGet();
            return existing;
        }

        // 尝试在主线程加载资源字节
        byte[] bytes;
        try {
            bytes = ResourceUtil.loadResourceBytes(location);
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load GIF resource bytes for {}: {}", location, e.getMessage());
            GifInstance fallback = new GifInstance(location, 16, 16);
            fallback.loaded.set(true);
            fallback.frames = new BufferedImage[0];
            fallback.delays = new int[]{100};
            gifCache.put(location, fallback);
            return fallback;
        }
        if (bytes == null || bytes.length == 0) {
            Main.LOGGER.warn("GIF resource not found or empty: {}", location);
            GifInstance fallback = new GifInstance(location, 16, 16);
            fallback.loaded.set(true);
            fallback.frames = new BufferedImage[0];
            fallback.delays = new int[]{100};
            gifCache.put(location, fallback);
            return fallback;
        }

        // 快速读取 GIF 真实尺寸，创建匹配的占位纹理
        int[] size = readGifCanvasSize(bytes);
        GifInstance inst = new GifInstance(location, size[0], size[1]);
        gifCache.put(location, inst);

        // 在后台线程异步解析 GIF
        INSTANCE.parserExecutor.submit(() -> {
            try {
                GifData data = parseGif(bytes);
                // 在渲染线程设置帧数据
                Minecraft.getInstance().tell(() -> {
                    inst.onFramesLoaded(data.frames, data.delays, data.loopCount);
                });
            } catch (Exception e) {
                Main.LOGGER.warn("Failed to parse GIF {} asynchronously: {}", location, e.getMessage());
                Minecraft.getInstance().tell(() -> {
                    inst.loaded.set(true);
                    inst.frames = new BufferedImage[0];
                    inst.delays = new int[]{100};
                });
            }
        });

        return inst;
    }

    /**
     * 释放一个 GifInstance 的引用，引用归零时关闭。
     */
    private void releaseInstance(ResourceLocation location) {
        GifInstance inst = gifCache.get(location);
        if (inst == null) return;
        int refs = inst.refCount.decrementAndGet();
        if (refs <= 0) {
            gifCache.remove(location);
            inst.close();
        }
    }

    // ========== 对外 API（按 ID 绑定） ==========

    /**
     * 为一个抽象 ID 绑定 GIF 动画。返回该 GIF 的 GraphicsTexture 的 ResourceLocation，
     * 可用于模型纹理替换。
     *
     * @param id       绑定 ID（如 BlockPos.toString）
     * @param location GIF 资源位置
     * @return GraphicsTexture 的 ResourceLocation，失败返回 null
     */
    public ResourceLocation bindGif(String id, ResourceLocation location) {
        // 先解绑旧的
        unbindGif(id);

        GifInstance inst = getOrCreateInstance(location);
        if (inst == null || inst.closed.get()) {
            Main.LOGGER.warn("Failed to create GIF instance for {} binding {}", location, id);
            return null;
        }
        bindingMap.put(id, location);
        // 记录当前纹理位置
        bindingTextureMap.put(id, inst.texture.identifier);

        // 注册纹理替换回调：更新 bindingTextureMap
        GifInstance finalInst = inst;
        inst.onTextureReplacedCallbacks.add(() -> {
            ResourceLocation newTexId = finalInst.texture.identifier;
            // 找到所有绑定此 GIF location 的 ID，更新它们的纹理位置
            for (Map.Entry<String, ResourceLocation> entry : bindingMap.entrySet()) {
                if (location.equals(entry.getValue())) {
                    bindingTextureMap.put(entry.getKey(), newTexId);
                }
            }
        });

        return inst.texture.identifier;
    }

    /**
     * 解绑指定 ID 的 GIF。
     */
    public void unbindGif(String id) {
        bindingTextureMap.remove(id);
        ResourceLocation old = bindingMap.remove(id);
        if (old != null) {
            releaseInstance(old);
        }
    }

    /**
     * 获取指定 ID 绑定的 GIF 的 GraphicsTexture ResourceLocation。
     * 优先从 bindingTextureMap 获取最新纹理位置（纹理替换后已更新），
     * 若没有则回退到从 gifCache 获取。
     */
    public ResourceLocation getGifTextureLocation(String id) {
        // 先查最新的纹理位置映射
        ResourceLocation texId = bindingTextureMap.get(id);
        if (texId != null) return texId;

        // 回退：通过 bindingMap + gifCache 获取
        ResourceLocation loc = bindingMap.get(id);
        if (loc == null) return null;
        GifInstance inst = gifCache.get(loc);
        if (inst == null || inst.closed.get()) return null;
        return inst.texture.identifier;
    }

    /**
     * 判断指定 ID 是否绑定了一个有效的 GIF。
     */
    public boolean hasGif(String id) {
        ResourceLocation loc = bindingMap.get(id);
        if (loc == null) return false;
        GifInstance inst = gifCache.get(loc);
        return inst != null && !inst.closed.get();
    }

    // ========== 对外 API（BlockPos 版本） ==========

    private static String blockPosToId(BlockPos pos) {
        return "gif_" + pos.toShortString();
    }

    public ResourceLocation bindGif(BlockPos pos, ResourceLocation location) {
        return bindGif(blockPosToId(pos), location);
    }

    public void unbindGif(BlockPos pos) {
        unbindGif(blockPosToId(pos));
    }

    public ResourceLocation getGifTextureLocation(BlockPos pos) {
        return getGifTextureLocation(blockPosToId(pos));
    }

    public boolean hasGif(BlockPos pos) {
        return hasGif(blockPosToId(pos));
    }

    /**
     * 获取指定 ID 绑定的 GIF 当前帧的 BufferedImage。
     * 如果未绑定、尚未完成加载或没有帧数据，返回一个空的透明 BufferedImage。
     *
     * @param id 绑定 ID
     * @return 当前帧 BufferedImage，未就绪时返回 1x1 透明图像
     */
    public BufferedImage getCurrentFrame(String id) {
        ResourceLocation loc = bindingMap.get(id);
        if (loc == null) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        GifInstance inst = gifCache.get(loc);
        if (inst == null || !inst.loaded.get()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage[] frames = inst.frames;
        if (frames == null || frames.length == 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        return frames[inst.currentFrame.get() % frames.length];
    }

    // ========== 播放控制 ==========

    /**
     * 暂停指定 GIF 的播放。
     */
    public void pause(ResourceLocation location) {
        GifInstance inst = gifCache.get(location);
        if (inst != null) inst.playing.set(false);
    }

    /**
     * 恢复指定 GIF 的播放。
     */
    public void resume(ResourceLocation location) {
        GifInstance inst = gifCache.get(location);
        if (inst != null && inst.playing.compareAndSet(false, true)) {
            inst.startAnimation();
        }
    }

    // ========== 全局清理 ==========

    /**
     * 关闭所有缓存的 GIF 实例，清空所有绑定。
     */
    public void disposeAll() {
        for (GifInstance inst : gifCache.values()) {
            inst.close();
        }
        gifCache.clear();
        bindingMap.clear();
        bindingTextureMap.clear();
    }

    /**
     * 关闭调度器。
     */
    public void shutdown() {
        disposeAll();
        scheduler.shutdownNow();
        parserExecutor.shutdownNow();
    }
}
