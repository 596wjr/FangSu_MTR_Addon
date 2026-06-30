package com.fangsu.drawing.sign;

import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.utils.ResourceUtil;
import com.google.common.base.Objects;
import com.google.gson.JsonObject;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

public abstract class SignItem {
    public boolean withText = false;
    public String text = "";

    public final JsonObject toJson() {
        JsonObject json = this.saveToJson();
        json.addProperty("type", this.getType());
        return json;
    }

    protected abstract JsonObject saveToJson();

    /**
     * 类型 id，如 "str" / "img" / "route"
     */
    public abstract String getType();

    /**
     * UI 中用于显示的图标（资源 id），可为空
     */
    public BufferedImage getIcon() throws IOException {
        return ResourceUtil.loadImage(new ResourceLocation("mtrsteamloco:imgnotfound.png").getRaw());
    }

    /**
     * 该 item 占用的“宽度比例”
     * unit = 行高
     */
    public abstract float getWidth(Graphics2D g, float unit);

    /**
     * 实际绘制
     */
    public abstract void draw(SignDrawContext ctx);

    /**
     * 额外配置
     */
    public List<ConfigEntry<?>> getConfigs() {
        return null;
    }

    public abstract ResourceLocation getIconLocation();

    public final SignItem setText(String text) {
        this.withText = true;
        this.text = text;
        return this;
    }

    public boolean isReady() {
        return true;
    }

    public boolean isCompleted() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getType(), toJson());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SignItem signItem && this.getType().equals(signItem.getType()) && Objects.equal(this.toJson(), signItem.toJson());
    }
}
