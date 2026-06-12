package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConfigWidget extends AbstractWidget {

    private final List<AbstractWidget> children = new ArrayList<>();
    private final int labelWidth;

    public ConfigWidget(
            int x,
            int y,
            int width,
            int height,
            int labelWidth,
            Component title,
            AbstractWidget... widgets
    ) {
        super(x, y, width, height, title);
        this.labelWidth = labelWidth;
        for (AbstractWidget w : widgets) {
            this.children.add(w);
        }
    }

    /* ================== 事件转发 ================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active) {
            return false;
        }
        return forwardToChildren(widget -> {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                syncFocus(widget);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible || !active) {
            return false;
        }
        return forwardToChildren(w -> w.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!visible || !active) {
            return false;
        }
        return forwardToChildren(w -> w.mouseDragged(mouseX, mouseY, button, dx, dy));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !active) {
            return false;
        }
        return forwardToChildren(w -> w.isFocused() && w.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !active) {
            return false;
        }
        return forwardToChildren(w -> w.isFocused() && w.charTyped(codePoint, modifiers));
    }

    /* ================== 渲染 ================== */

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int maxWidth = Math.max(0, labelWidth - 4);
        String label = maxWidth > 0
                ? font.plainSubstrByWidth(getMessage().getString(), maxWidth)
                : getMessage().getString();
        int textY = getY() + (height - 8) / 2;
        gui.drawString(font, label, getX(), textY, 0x202020, false);
        for (AbstractWidget w : children) {
            w.render(gui, mouseX, mouseY, partialTick);
        }
    }
    //#elseif MC_VERSION >= 11904
    //$$@Override
    //$$public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    var font = net.minecraft.client.Minecraft.getInstance().font;
    //$$    int maxWidth = Math.max(0, labelWidth - 4);
    //$$    String label = maxWidth > 0
    //$$            ? font.plainSubstrByWidth(getMessage().getString(), maxWidth)
    //$$            : getMessage().getString();
    //$$    int textY = getY() + (height - 8) / 2;
    //$$    font.draw(poseStack, label, (float) getX(), (float) textY, 0x202020);
    //$$    for (AbstractWidget w : children) {
    //$$        w.render(poseStack, mouseX, mouseY, partialTick);
    //$$    }
    //$$}
    //#elseif MC_VERSION >= 11903
    //$$@Override
    //$$public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    var font = net.minecraft.client.Minecraft.getInstance().font;
    //$$    int maxWidth = Math.max(0, labelWidth - 4);
    //$$    String label = maxWidth > 0
    //$$            ? font.plainSubstrByWidth(getMessage().getString(), maxWidth)
    //$$            : getMessage().getString();
    //$$    int textY = getY() + (height - 8) / 2;
    //$$    font.draw(poseStack, label, (float) getX(), (float) textY, 0x202020);
    //$$    for (AbstractWidget w : children) {
    //$$        w.render(poseStack, mouseX, mouseY, partialTick);
    //$$    }
    //$$}
    //#else
    //$$@Override
    //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    var font = net.minecraft.client.Minecraft.getInstance().font;
    //$$    int maxWidth = Math.max(0, labelWidth - 4);
    //$$    String label = maxWidth > 0
    //$$            ? font.plainSubstrByWidth(getMessage().getString(), maxWidth)
    //$$            : getMessage().getString();
    //$$    int textY = this.y + (height - 8) / 2;
    //$$    font.draw(poseStack, label, (float) this.x, (float) textY, 0x202020);
    //$$    for (AbstractWidget w : children) {
    //$$        w.render(poseStack, mouseX, mouseY, partialTick);
    //$$    }
    //$$}
    //#endif

    //#if MC_VERSION >= 11903
    @Override
    public void setX(int x) {
        int delta = x - getX();
        super.setX(x);
        if (delta != 0) {
            for (AbstractWidget w : children) {
                w.setX(w.getX() + delta);
            }
        }
    }

    @Override
    public void setY(int y) {
        int delta = y - getY();
        super.setY(y);
        if (delta != 0) {
            for (AbstractWidget w : children) {
                w.setY(w.getY() + delta);
            }
        }
    }
    //#else
    //$$public void setX(int x) {
    //$$    int delta = x - this.x;
    //$$    this.x = x;
    //$$    if (delta != 0) {
    //$$        for (AbstractWidget w : children) {
    //$$            w.x += delta;
    //$$        }
    //$$    }
    //$$}
    //$$
    //$$public void setY(int y) {
    //$$    int delta = y - this.y;
    //$$    this.y = y;
    //$$    if (delta != 0) {
    //$$        for (AbstractWidget w : children) {
    //$$            w.y += delta;
    //$$        }
    //$$    }
    //$$}
    //#endif

    //#if MC_VERSION >= 11903
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    //$$@Override
    //$$public void updateNarration(NarrationElementOutput narration) {
    //$$}
    //#endif

    /**
     * 统一处理子控件事件转发，减少重复代码�?
     */
    private boolean forwardToChildren(java.util.function.Predicate<AbstractWidget> handler) {
        for (AbstractWidget w : children) {
            if (handler.test(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 控制同一行配置控件的焦点互斥�?
     */
    private void syncFocus(AbstractWidget focused) {
        for (AbstractWidget other : children) {
            //#if MC_VERSION >= 12000
            other.setFocused(other == focused);
            //#else
            //$$ if (other == focused && other != null) { try { java.lang.reflect.Method m = AbstractWidget.class.getDeclaredMethod("setFocused", boolean.class); m.setAccessible(true); m.invoke(other, true); } catch (Exception ignored) {} }
            //#endif
        }
    }
}
