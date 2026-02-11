package com.fangsu.signItems;

import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public abstract class SignItem {

    /**
     * 类型 id，如 "str" / "img" / "route"
     */
    public abstract String getType();

    /**
     * UI 中用于显示的图标（资源 id），可为空
     */
    public BufferedImage getIcon() throws IOException {
        return ResourceUtil.loadImage(new ResourceLocation("mtrsteamloco:imgnotfound.png"));
    }

    /**
     * 该 item 占用的“宽度比例”
     * unit = 行高（等价于你 JS 里的 u）
     */
    public abstract float getWidth(SignDrawContext ctx);

    /**
     * 实际绘制
     * duiqi：0 左，1 中，2 右
     */
    public abstract void draw(SignDrawContext ctx);

    public List<ConfigEntry<?>> getConfigs() {
        return null;
    }
}
