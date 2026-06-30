package com.fangsu.extraConfig;

//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import com.fangsu.mappings.LocalComponent;
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

    public ConfigRow(
            int x, int y, int w, int h,
            LocalComponent title,
            AbstractWidget field
    ) {
        this(x, y, w, h, title.getRaw(), field);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX(), getY() + (height - 8) / 2, 0xFFFFFF);
        field.render(g, mouseX, mouseY, partial);
    }
    //#elseif MC_VERSION >= 11904
    //$$@Override
    //$$public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    net.minecraft.client.Minecraft.getInstance().font.draw(poseStack, getMessage(), (float) getX(), (float) (getY() + (height - 8) / 2), 0xFFFFFF);
    //$$    field.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#elseif MC_VERSION >= 11903
    //$$@Override
    //$$public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    net.minecraft.client.Minecraft.getInstance().font.draw(poseStack, getMessage(), (float) getX(), (float) (getY() + (height - 8) / 2), 0xFFFFFF);
    //$$    field.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#else
    //$$@Override
    //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    net.minecraft.client.Minecraft.getInstance().font.draw(poseStack, getMessage(), (float) this.x, (float) (this.y + (height - 8) / 2), 0xFFFFFF);
    //$$    field.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#endif

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        return field.mouseClicked(x, y, btn);
    }

    //#if MC_VERSION >= 11903
    @Override
    public void setX(int x) {
        int delta = x - getX();
        super.setX(x);
        if (delta != 0) field.setX(field.getX() + delta);
    }

    @Override
    public void setY(int y) {
        int delta = y - getY();
        super.setY(y);
        if (delta != 0) field.setY(field.getY() + delta);
    }
    //#else
    //$$public void setX(int x) {
    //$$    int delta = x - this.x;
    //$$    this.x = x;
    //$$    if (delta != 0) field.x += delta;
    //$$}
    //$$
    //$$public void setY(int y) {
    //$$    int delta = y - this.y;
    //$$    this.y = y;
    //$$    if (delta != 0) field.y += delta;
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
}
