package com.fangsu.extraConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class CompoundWidget extends AbstractWidget {

    private final List<AbstractWidget> children;

    public CompoundWidget(AbstractWidget... widgets) {
        super(
                widgets[0].getX(),
                widgets[0].getY(),
                calcWidth(widgets),
                widgets[0].getHeight(),
                Component.empty()
        );
        this.children = Arrays.asList(widgets);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        for (AbstractWidget w : children) {
            if (w.visible) {
                w.render(g, mouseX, mouseY, partial);
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        for (AbstractWidget w : children) {
            if (w.mouseClicked(x, y, btn)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int btn, double dx, double dy) {
        for (AbstractWidget w : children) {
            if (w.mouseDragged(x, y, btn, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mod) {
        for (AbstractWidget w : children) {
            if (w.keyPressed(key, sc, mod)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    private static int calcWidth(AbstractWidget[] ws) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (AbstractWidget w : ws) {
            minX = Math.min(minX, w.getX());
            maxX = Math.max(maxX, w.getX() + w.getWidth());
        }
        return maxX - minX;
    }
}
