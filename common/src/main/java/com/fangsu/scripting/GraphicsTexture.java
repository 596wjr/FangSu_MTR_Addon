package com.fangsu.scripting;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class GraphicsTexture implements AutoCloseable {
    private final DynamicTexture dynamicTexture;
    public final ResourceLocation identifier;
    public final BufferedImage bufferedImage;
    public final SmarterG2D graphics;
    public final int width;
    public final int height;
    public boolean isClosed;

    public GraphicsTexture(int width, int height) {
        this.width = width;
        this.height = height;

        // 创建 NativeImage 并绑定 DynamicTexture
        NativeImage nativeImage = new NativeImage(width, height, true);
        this.dynamicTexture = new DynamicTexture(nativeImage);

        // 唯一资源标识
        this.identifier = new ResourceLocation("fangsu", "dynamic/graphics/" + UUID.randomUUID());

        // 注册纹理到 Minecraft 的纹理管理器
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().getTextureManager().register(this.identifier, this.dynamicTexture)
        );

        // 创建可操作的 BufferedImage
        this.bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D raw = this.bufferedImage.createGraphics();
        this.graphics = new SmarterG2D(raw);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        this.isClosed = false;
    }

    /**
     * 创建 ARGB BufferedImage 的副本
     */
    public static BufferedImage createArgbBufferedImage(BufferedImage src) {
        BufferedImage newImage = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = newImage.createGraphics();
        graphics.drawImage(src, 0, 0, (ImageObserver) null);
        graphics.dispose();
        return newImage;
    }

    /**
     * 上传 BufferedImage 内容到 GPU
     */
    public void upload() {
        // 获取 BufferedImage 数据
        int[] pixels = ((DataBufferInt) this.bufferedImage.getRaster().getDataBuffer()).getData();
        NativeImage nativeImage = this.dynamicTexture.getPixels();

        // 将 ARGB 转成 RGBA 并写入 NativeImage
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // 在 upload() 中，组装 ABGR
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }

        // 将更新记录到渲染线程
        DynamicTexture texture = this.dynamicTexture;
        Objects.requireNonNull(texture);
        RenderSystem.recordRenderCall(texture::upload);
    }

    /**
     * 释放资源
     */
    @Override
    public void close() {
        this.isClosed = true;
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().getTextureManager().release(this.identifier)
        );
        this.graphics.dispose();
    }

    /**
     * 在客户端主线程的下一帧关闭资源
     */
    public void closeLater() {
        this.isClosed = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.execute(this::close));
    }
}
