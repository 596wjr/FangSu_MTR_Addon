package com.fangsu.extraConfig;

//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ConfigRow extends AbstractWidget {

    private final AbstractWidget field;

    public ConfigRow(
            int x, int y, int w, int h,
            Component title,
            AbstractWidget field
    ) {
        super(x, y, w, h, title);
        this.field = field;
    }

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                getMessage(),
                getX(),
                getY() + (height - 8) / 2,
                0xFFFFFF
        );
        field.render(g, mouseX, mouseY, partial);
    }
    //#else
    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
        net.minecraft.client.Minecraft.getInstance().font.draw(poseStack, getMessage(), (float) x, (float) (y + (height - 8) / 2), 0xFFFFFF);
        field.render(poseStack, mouseX, mouseY, partial);
    }
    //#endif

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        return field.mouseClicked(x, y, btn);
    }

    //#if MC_VERSION >= 12000
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    @Override
    public void updateNarration(NarrationElementOutput narration) {
    }
    //#endif
}
