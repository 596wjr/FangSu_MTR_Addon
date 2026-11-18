package com.fangsu.mixin;

import com.fangsu.ui.DashboardRoutePanel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import mtr.screen.DashboardList;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DashboardList.class, remap = false)
public abstract class DashboardRouteListMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //#if MC_VERSION >= 12000
    private void fangsu$renderRoutes(net.minecraft.client.gui.GuiGraphics graphics, Font font, CallbackInfo ci) {
    //#else
    //$$ private void fangsu$renderRoutes(com.mojang.blaze3d.vertex.PoseStack graphics, Font font, CallbackInfo ci) {
    //#endif
        final var client = Minecraft.getInstance();
        if (!(client.screen instanceof DashboardRoutePanel.Host host)) return;
        final var window = client.getWindow();
        final int mouseX = (int) (client.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        final int mouseY = (int) (client.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        final var context = GraphicContext.of(graphics);
        final var canvas = new DashboardRoutePanel.Canvas() {
            public void fill(int left, int top, int right, int bottom, int color) {
                context.fill(left, top, right, bottom, color);
            }

            public void text(Component label, int x, int y, int color) {
                context.drawString(font, label, x, y, color, false);
            }

            public void clip(int left, int top, int right, int bottom, Runnable draw) {
                context.enableScissor(left, top, right, bottom);
                try {
                    draw.run();
                } finally {
                    context.disableScissor();
                }
            }

            public void icon(DashboardRoutePanel.Icon icon, int x, int y, boolean hovered) {
                final var texture = new net.minecraft.resources.ResourceLocation("mtr", "textures/gui/icon_" + icon.textureName() + ".png");
                context.blit(texture, x, y, 0, hovered ? 20 : 0, 20, 20, 20, 40);
            }
        };
        if (host.fangsu$drawRoutePanel(canvas, mouseX, mouseY)) ci.cancel();
    }
}
