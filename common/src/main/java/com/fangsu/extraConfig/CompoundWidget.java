package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class CompoundWidget extends AbstractWidget {

    private final List<AbstractWidget> children;

    public CompoundWidget(AbstractWidget... widgets) {
        //#if MC_VERSION >= 11903
        super(widgets[0].getX(), widgets[0].getY(), calcWidth(widgets), widgets[0].getHeight(), Component.empty());
        //#else
        //$$ super(widgets[0].x, widgets[0].y, calcWidth(widgets), widgets[0].getHeight(), ComponentHelper.empty());
        //#endif
        this.children = Arrays.asList(widgets);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        for (AbstractWidget w : children) {
            if (w.visible) {
                w.render(g, mouseX, mouseY, partial);
            }
        }
    }
    //#elseif MC_VERSION >= 11904
    //$$@Override
    //$$public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    for (AbstractWidget w : children) {
    //$$        if (w.visible) {
    //$$            w.render(poseStack, mouseX, mouseY, partial);
    //$$        }
    //$$    }
    //$$}
    //#elseif MC_VERSION >= 11903
    //$$@Override
    //$$public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    for (AbstractWidget w : children) {
    //$$        if (w.visible) {
    //$$            w.render(poseStack, mouseX, mouseY, partial);
    //$$        }
    //$$    }
    //$$}
    //#else
    //$$@Override
    //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    for (AbstractWidget w : children) {
    //$$        if (w.visible) {
    //$$            w.render(poseStack, mouseX, mouseY, partial);
    //$$        }
    //$$    }
    //$$}
    //#endif

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

    //#if MC_VERSION >= 11903
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    //$$ @Override
    //$$ public void updateNarration(NarrationElementOutput narration) {
    //$$ }
    //#endif

    private static int calcWidth(AbstractWidget[] ws) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (AbstractWidget w : ws) {
            //#if MC_VERSION >= 11903
            minX = Math.min(minX, w.getX());
            maxX = Math.max(maxX, w.getX() + w.getWidth());
            //#else
            //$$ minX = Math.min(minX, w.x);
            //$$ maxX = Math.max(maxX, w.x + w.getWidth());
            //#endif
        }
        return maxX - minX;
    }
}

