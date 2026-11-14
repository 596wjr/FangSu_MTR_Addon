package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.network.NetworkImageLoader;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 管理本地 GIF 和网络图片的加载、播放及共享纹理。
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

    private static final int MAX_WEB_IMAGES = 24;
    private static final int MAX_WEB_PIXELS = 4 * 1024 * 1024;
    private static final int MAX_WEB_DIMENSION = 4096;

    private final ThreadPoolExecutor webExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_WEB_IMAGES), task -> {
        Thread thread = new Thread(task, "NetworkImage-Loader");
        thread.setDaemon(true);
        return thread;
    });

    // 绑定和引用计数由同一把锁保护，后台加载的广告板可以安全地共用纹理。
    private final Map<Object, GifInstance> imageCache = new HashMap<>();
    private final Map<String, Object> bindingMap = new HashMap<>();

    /* =========================
       构造
       ========================= */

    private GifHelper() {
    }

    /* =========================
       内部类：单个 GIF 实例
       ========================= */

    private static class GifInstance implements AutoCloseable {
        volatile GraphicsTexture texture;
        volatile BufferedImage[] frames;
        volatile int[] delays;          // 每帧延迟（毫秒）
        final AtomicInteger currentFrame = new AtomicInteger(0);
        final AtomicBoolean playing = new AtomicBoolean(true);
        final AtomicBoolean closed = new AtomicBoolean(false);
        volatile int loopCount;         // 0 = 无限循环
        final AtomicInteger loopCounter = new AtomicInteger(0);

        /**
         * 标记是否已经完成异步加载
         */
        final AtomicBoolean loaded = new AtomicBoolean(false);

        // 引用计数（有多少个绑定在使用此实例）
        int refCount = 1;
        FutureTask<Void> webLoad;

        // 当前调度的 Future，用于取消
        ScheduledFuture<?> scheduledFuture;

        GifInstance(GraphicsTexture texture) {
            this.texture = texture;
        }

        /**
         * 在后台线程解析完成后调用，设置帧数据并启动动画。
         * 在渲染线程调用以确保纹理操作安全。
         */
        synchronized void onFramesLoaded(BufferedImage[] loadedFrames, int[] loadedDelays, int loadedLoopCount) {
            if (closed.get()) return;
            this.frames = loadedFrames;
            this.delays = loadedDelays;
            this.loopCount = loadedLoopCount;
            this.loaded.set(true);

            if (frames.length > 0) {
                BufferedImage firstFrame = frames[0];
                // 如果占位纹理尺寸与真实帧不匹配，重新创建纹理
                if (texture == null || texture.width != firstFrame.getWidth() || texture.height != firstFrame.getHeight()) {
                    GraphicsTexture oldTex = texture;
                    texture = new GraphicsTexture(firstFrame.getWidth(), firstFrame.getHeight());
                    if (oldTex != null) oldTex.close();
                }
                texture.updateTexture(firstFrame);
                if (frames.length > 1) {
                    startAnimation();
                }
            }
        }

        private void startAnimation() {
            scheduleNextFrame();
        }

        private synchronized void scheduleNextFrame() {
            if (closed.get() || !playing.get()) return;

            BufferedImage[] f = frames;
            int[] d = delays;
            if (f == null || d == null || f.length <= 1) return;

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
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            playing.set(false);
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
            if (webLoad != null) {
                webLoad.cancel(true);
                INSTANCE.webExecutor.remove(webLoad);
                webLoad = null;
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
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            return decodeImage(input, false);
        }
    }

    private static GifData decodeImage(ImageInputStream input, boolean web) throws IOException {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new IOException("Unsupported image format");
        ImageReader reader = readers.next();
        try {
            reader.setInput(input, false);
            if (!"gif".equalsIgnoreCase(reader.getFormatName())) {
                if (!web) throw new IOException("Expected a GIF image");
                checkWebSize(reader.getWidth(0), reader.getHeight(0), 1);
                return new GifData(new BufferedImage[]{reader.read(0)}, new int[]{100}, 1);
            }

            int[] size = readGifCanvasSize(reader);
            int canvasW = size[0];
            int canvasH = size[1];
            int numFrames = reader.getNumImages(true);
            if (numFrames == 0) throw new IOException("GIF has no frames");
            if (web) checkWebSize(canvasW, canvasH, numFrames);

            var frames = new BufferedImage[numFrames];
            var delays = new int[numFrames];
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();
            try {
                for (int i = 0; i < numFrames; i++) {
                    if (web) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Image decoding cancelled");
                        checkWebSize(reader.getWidth(i), reader.getHeight(i), 1);
                    }
                    BufferedImage frame = reader.read(i);
                    int[] offset = getFrameOffset(reader, i);
                    int disposal = getDisposalMethod(reader, i);
                    BufferedImage previous = disposal == 3 ? copyFrame(canvas) : null;

                    graphics.drawImage(frame, offset[0], offset[1], null);
                    frames[i] = copyFrame(canvas);
                    delays[i] = getFrameDelay(reader, i);

                    // 保存本帧后再恢复画布，下一帧才能正确合成。
                    if (disposal == 2) {
                        graphics.setComposite(AlphaComposite.Clear);
                        graphics.fillRect(offset[0], offset[1], frame.getWidth(), frame.getHeight());
                        graphics.setComposite(AlphaComposite.SrcOver);
                    } else if (previous != null) {
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.drawImage(previous, 0, 0, null);
                        graphics.setComposite(AlphaComposite.SrcOver);
                    }
                }
            } finally {
                graphics.dispose();
            }
            return new GifData(frames, delays, getLoopCount(reader));
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage copyFrame(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.setData(image.getRaster());
        return copy;
    }

    private static void checkWebSize(int width, int height, int frames) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_WEB_DIMENSION || height > MAX_WEB_DIMENSION
                || (long) width * height * frames > MAX_WEB_PIXELS) {
            throw new IOException("Decoded image is too large");
        }
    }

    private static int getLoopCount(ImageReader reader) {
        try {
            var metadata = reader.getImageMetadata(0);
            var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            for (var node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!"ApplicationExtensions".equals(node.getNodeName())) continue;
                for (var child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (!(child instanceof IIOMetadataNode extension)) continue;
                    if (!"NETSCAPE".equals(extension.getAttribute("applicationID"))
                            || !"2.0".equals(extension.getAttribute("authenticationCode"))) continue;
                    if (extension.getUserObject() instanceof byte[] bytes && bytes.length >= 3 && bytes[0] == 1) {
                        int repeats = (bytes[1] & 0xFF) | ((bytes[2] & 0xFF) << 8);
                        return repeats == 0 ? 0 : repeats + 1;
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
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input, false);
                return readGifCanvasSize(reader);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return new int[]{16, 16};
        }
    }

    private static int[] readGifCanvasSize(ImageReader reader) throws IOException {
        var metadata = reader.getStreamMetadata();
        if (metadata != null) {
            var root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
            for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof IIOMetadataNode node && "LogicalScreenDescriptor".equals(node.getNodeName())) {
                    int width = Integer.parseInt(node.getAttribute("logicalScreenWidth"));
                    int height = Integer.parseInt(node.getAttribute("logicalScreenHeight"));
                    return new int[]{Math.max(width, 1), Math.max(height, 1)};
                }
            }
        }
        return new int[]{reader.getWidth(0), reader.getHeight(0)};
    }

    private GifInstance loadLocalGif(ResourceLocation location) {
        byte[] bytes;
        try {
            bytes = ResourceUtil.loadResourceBytes(location);
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load GIF resource bytes for {}: {}", location, e.getMessage());
            return new GifInstance(new GraphicsTexture(16, 16));
        }
        if (bytes == null || bytes.length == 0) {
            Main.LOGGER.warn("GIF resource not found or empty: {}", location);
            return new GifInstance(new GraphicsTexture(16, 16));
        }

        int[] size = readGifCanvasSize(bytes);
        GifInstance instance = new GifInstance(new GraphicsTexture(size[0], size[1]));
        parserExecutor.submit(() -> {
            try {
                publishFrames(instance, parseGif(bytes));
            } catch (Exception e) {
                Main.LOGGER.warn("Failed to parse GIF {} asynchronously: {}", location, e.getMessage());
            }
        });
        return instance;
    }

    private GifInstance loadWebImage(URI uri) {
        long webImages = imageCache.keySet().stream().filter(URI.class::isInstance).count();
        if (webImages >= MAX_WEB_IMAGES) return null;
        GifInstance instance = new GifInstance(null);
        instance.webLoad = new FutureTask<>(() -> {
            try {
                byte[] bytes = NetworkImageLoader.download(uri);
                if (instance.closed.get()) return null;
                try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                    publishFrames(instance, decodeImage(input, true));
                }
            } catch (Exception e) {
                if (!instance.closed.get()) {
                    Main.LOGGER.warn("Failed to load network image from {} ({})", uri.getHost(), e.getClass().getSimpleName());
                }
            }
            return null;
        });
        try {
            webExecutor.execute(instance.webLoad);
            return instance;
        } catch (RejectedExecutionException e) {
            return null;
        }
    }

    private static void publishFrames(GifInstance instance, GifData data) {
        Minecraft.getInstance().tell(() -> instance.onFramesLoaded(data.frames, data.delays, data.loopCount));
    }

    private GifInstance getBoundInstance(String id) {
        return imageCache.get(bindingMap.get(id));
    }

    private GifInstance bindImage(String id, Object source) {
        if (source.equals(bindingMap.get(id))) return getBoundInstance(id);
        unbindGif(id);
        GifInstance instance = imageCache.get(source);
        if (instance == null) {
            instance = source instanceof URI uri ? loadWebImage(uri) : loadLocalGif((ResourceLocation) source);
            if (instance == null) return null;
            imageCache.put(source, instance);
        } else {
            instance.refCount++;
        }
        bindingMap.put(id, source);
        return instance;
    }

    /** 绑定本地 GIF，返回当前的纹理标识。 */
    public synchronized ResourceLocation bindGif(String id, ResourceLocation location) {
        return bindImage(id, location).texture.identifier;
    }

    /** 绑定网络图片，下载完成后可通过 getGifTextureLocation 获取纹理。 */
    public synchronized void bindWebImage(String id, String url) {
        try {
            if (NetworkImageLoader.isHttpUrl(url)) bindImage(id, URI.create(url));
        } catch (IllegalArgumentException ignored) {
            // 配置输入期间可能暂时不是有效的 URL。
        }
    }

    /** 获取脚本绘制所需的当前帧，等待下载期间返回透明图像。 */
    public synchronized BufferedImage getWebImageFrame(String url) {
        String id = "web-script:" + url;
        if (!hasGif(id)) bindWebImage(id, url);
        return getCurrentFrame(id);
    }

    public synchronized void unbindGif(String id) {
        Object source = bindingMap.remove(id);
        GifInstance instance = imageCache.get(source);
        if (instance != null && --instance.refCount == 0) {
            imageCache.remove(source);
            instance.close();
        }
    }

    /** 每次取当前纹理标识，避免继续使用已释放的占位纹理。 */
    public synchronized ResourceLocation getGifTextureLocation(String id) {
        GifInstance instance = getBoundInstance(id);
        GraphicsTexture texture = instance == null ? null : instance.texture;
        return texture == null ? null : texture.identifier;
    }

    public synchronized boolean hasGif(String id) {
        return getBoundInstance(id) != null;
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
    public synchronized BufferedImage getCurrentFrame(String id) {
        GifInstance instance = getBoundInstance(id);
        if (instance == null || !instance.loaded.get()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        return instance.frames[instance.currentFrame.get()];
    }

    // ========== 播放控制 ==========

    /**
     * 暂停指定 GIF 的播放。
     */
    public synchronized void pause(ResourceLocation location) {
        GifInstance inst = imageCache.get(location);
        if (inst != null) inst.playing.set(false);
    }

    /**
     * 恢复指定 GIF 的播放。
     */
    public synchronized void resume(ResourceLocation location) {
        GifInstance inst = imageCache.get(location);
        if (inst != null && inst.playing.compareAndSet(false, true)) {
            inst.startAnimation();
        }
    }

    // ========== 全局清理 ==========

    /**
     * 关闭所有缓存的 GIF 实例，清空所有绑定。
     */
    public synchronized void disposeAll() {
        for (GifInstance inst : imageCache.values()) {
            inst.close();
        }
        imageCache.clear();
        bindingMap.clear();
    }

    /**
     * 关闭调度器。
     */
    public void shutdown() {
        disposeAll();
        scheduler.shutdownNow();
        parserExecutor.shutdownNow();
        webExecutor.shutdownNow();
    }
}
