package com.fangsu.scripting;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public class GraphicsTexture implements AutoCloseable {
    private DynamicTexture dynamicTexture;
    public final ResourceLocation identifier;
    public final BufferedImage bufferedImage;
    public final Graphics2D graphics;
    public final int width;
    public final int height;

    public final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    private final AtomicBoolean isTextureValid = new AtomicBoolean(false);

    // 标记是否已经从TextureManager释放
    private final AtomicBoolean isReleased = new AtomicBoolean(false);

    private static final ConcurrentHashMap<ResourceLocation, GraphicsTexture> ACTIVE_TEXTURES = new ConcurrentHashMap<>();

    // 默认白色纹理的ResourceLocation
    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("textures/misc/white.png");

    public GraphicsTexture(int width, int height) {
        this.width = width;
        this.height = height;

        // 创建可操作的 BufferedImage
        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.graphics = this.bufferedImage.createGraphics();
        this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        this.graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 唯一资源标识 - 使用更短的UUID
        this.identifier = new ResourceLocation("fangsu", String.format("dynamic/graphics/%s",
                UUID.randomUUID().toString().replace("-", "")));

        // 初始化纹理（必须在渲染线程执行）
        initializeTexture();
    }

    /**
     * 初始化纹理（必须在渲染线程执行）
     */
    private void initializeTexture() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::initializeTexture);
            return;
        }

        try {
            // 创建 NativeImage - 使用正确的格式
            NativeImage nativeImage = new NativeImage(width, height, false);

            // 初始化为透明
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    nativeImage.setPixelRGBA(x, y, 0); // 完全透明
                }
            }

            this.dynamicTexture = new DynamicTexture(nativeImage);

            // 注册纹理
            registerTexture();
        } catch (Exception e) {
            System.err.println("Failed to initialize texture: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 注册纹理到 TextureManager
     */
    private void registerTexture() {
        if (isClosed.get()) return;

        try {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();

            // 确保没有同名的纹理
            AbstractTexture existing = textureManager.getTexture(this.identifier, null);
            if (existing != null && existing != this.dynamicTexture) {
                textureManager.release(this.identifier);
            }

            // 注册新纹理
            textureManager.register(this.identifier, this.dynamicTexture);
            isRegistered.set(true);
            isReleased.set(false);

            // 添加到活动纹理缓存
            ACTIVE_TEXTURES.put(this.identifier, this);

            // 立即上传初始内容
            upload();

        } catch (Exception e) {
            System.err.println("Failed to register texture: " + e.getMessage());
            isRegistered.set(false);
        }
    }

    /**
     * 上传 BufferedImage 内容到 GPU
     */
    public void upload() {
        if (isClosed.get() || !isRegistered.get() || dynamicTexture == null) {
            return;
        }

        NativeImage nativeImage = dynamicTexture.getPixels();
        if (nativeImage == null) {
            System.err.println("NativeImage is null for texture: " + identifier);
            isTextureValid.set(false);
            return;
        }

        // 获取 BufferedImage 数据
        int[] pixels;
        try {
            pixels = ((DataBufferInt) this.bufferedImage.getRaster().getDataBuffer()).getData();
        } catch (Exception e) {
            System.err.println("Failed to get pixel data: " + e.getMessage());
            isTextureValid.set(false);
            return;
        }

        // 将 ARGB 转换为 RGBA 并写入 NativeImage
        try {
            // BufferedImage 是 ARGB，NativeImage 需要 ABGR
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = pixels[y * width + x];

                    // 转换 ARGB 到 ABGR
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;

                    // NativeImage 使用 ABGR
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setPixelRGBA(x, y, abgr);
                }
            }

            isTextureValid.set(true);

            // 上传到 GPU
            if (RenderSystem.isOnRenderThread()) {
                dynamicTexture.upload();
            } else {
                DynamicTexture finalTexture = this.dynamicTexture;
                RenderSystem.recordRenderCall(() -> {
                    if (!isClosed.get() && isRegistered.get() && finalTexture.getPixels() != null) {
                        finalTexture.upload();
                    }
                });
            }

        } catch (Exception e) {
            System.err.println("Error uploading texture: " + e.getMessage());
            isTextureValid.set(false);
        }
    }

    /**
     * 绑定纹理以供渲染
     */
    public void bind() {
        if (shouldUseValidTexture()) {
            RenderSystem.setShaderTexture(0, this.identifier);
        } else {
            // 如果纹理无效，绑定默认白色纹理
            RenderSystem.setShaderTexture(0, WHITE_TEXTURE);
        }
    }

    /**
     * 检查是否应该使用有效纹理
     */
    private boolean shouldUseValidTexture() {
        return !isClosed.get() &&
                isRegistered.get() &&
                isTextureValid.get() &&
                dynamicTexture != null &&
                dynamicTexture.getPixels() != null &&
                !isReleased.get();
    }

    /**
     * 检查纹理是否有效
     */
    public boolean isValid() {
        return shouldUseValidTexture();
    }

    /**
     * 立即释放资源
     */
    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        // 从缓存中移除
        ACTIVE_TEXTURES.remove(this.identifier);

        // 在渲染线程中释放 OpenGL 资源
        if (RenderSystem.isOnRenderThread()) {
            releaseResources();
        } else {
            RenderSystem.recordRenderCall(this::releaseResources);
        }

        // 释放 Java 2D 资源
        if (this.graphics != null) {
            this.graphics.dispose();
        }
    }

    /**
     * 实际释放资源的逻辑
     */
    private void releaseResources() {
        if (isReleased.get()) return;

        try {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();

            // 检查纹理是否仍然存在且是我们注册的那个
            AbstractTexture currentTexture = textureManager.getTexture(this.identifier, null);
            if (currentTexture == this.dynamicTexture) {
                textureManager.release(this.identifier);
                isReleased.set(true);
            }

            // 关闭 DynamicTexture
            if (this.dynamicTexture != null) {
                this.dynamicTexture.close();
            }

        } catch (Exception e) {
            // 忽略释放时的错误
        }

        isRegistered.set(false);
        isTextureValid.set(false);
        dynamicTexture = null;
    }

    /**
     * 安全地关闭并在指定延迟后释放
     */
    public void closeLater() {
        if (isClosed.get()) return;

        // 先标记为无效，这样 bind() 会使用白色纹理
        isTextureValid.set(false);

        // 延迟一帧后真正释放资源
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            // 再延迟一帧以确保当前帧的渲染完成
            mc.tell(this::close);
        });
    }

    /**
     * 更新纹理内容（线程安全版本）
     */
    public void updateTexture(BufferedImage newImage) {
        if (isClosed.get() || newImage == null) return;

        // 复制新图像内容到 bufferedImage
        Graphics2D g = this.bufferedImage.createGraphics();
        // 先清除旧内容（透明背景），防止透明帧叠加造成残影
        g.setBackground(new java.awt.Color(0, 0, 0, 0));
        g.clearRect(0, 0, width, height);
        g.drawImage(newImage, 0, 0, null);
        g.dispose();

        // 上传到 GPU
        upload();
    }

    public static GraphicsTexture getActiveTexture(ResourceLocation identifier) {
        return ACTIVE_TEXTURES.get(identifier);
    }

    /**
     * 获取当前纹理的 ResourceLocation（用于调试）
     */
    public ResourceLocation getIdentifier() {
        return identifier;
    }
}