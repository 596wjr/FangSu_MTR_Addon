package com.fangsu.extraConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 一行配置控件：左 label + 右侧子控件
 */
public class ConfigWidget extends AbstractWidget {

    private final List<AbstractWidget> children = new ArrayList<>();

    public ConfigWidget(
            int x,
            int y,
            int width,
            int height,
            Component title,
            AbstractWidget... widgets
    ) {
        super(x, y, width, height, title);
        for (AbstractWidget w : widgets) {
            this.children.add(w);
        }
    }

    /* ================== 事件转发 ================== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AbstractWidget w : children) {
            if (w.mouseClicked(mouseX, mouseY, button)) {
                return true; // 让 Screen 处理焦点
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AbstractWidget w : children) {
            if (w.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        for (AbstractWidget w : children) {
            if (w.mouseDragged(mouseX, mouseY, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AbstractWidget w : children) {
            if (w.isFocused() && w.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (AbstractWidget w : children) {
            if (w.isFocused() && w.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /* ================== 渲染 ================== */

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        for (AbstractWidget w : children) {
            w.render(gui, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // 暂不实现
    }
}
