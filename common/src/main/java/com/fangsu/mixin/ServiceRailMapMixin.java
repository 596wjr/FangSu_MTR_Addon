package com.fangsu.mixin;

import com.fangsu.mtr.RouteMapSelection;
import com.fangsu.mtr.ServiceStop;
import com.fangsu.mtr.rail.BypassMapMarker;
import com.fangsu.mtr.rail.ServiceRail;
import com.fangsu.network.ServiceRailPackets;
import com.fangsu.ui.ServiceRailScreen;
import com.fangsu.utils.GraphicContext;
import mtr.client.ClientData;
import mtr.data.TransportMode;
import mtr.screen.WidgetMap;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(value = WidgetMap.class, remap = false)
public abstract class ServiceRailMapMixin implements RouteMapSelection {
    @Unique private java.util.function.Predicate<ServiceStop.Bypass> fangsu$selection;

    @Override
    public void fangsu$selectBypass(java.util.function.Predicate<ServiceStop.Bypass> selection) {
        fangsu$selection = selection;
    }
    @Shadow private int x;
    @Shadow private int y;
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private double scale;
    @Shadow private double centerX;
    @Shadow private double centerY;
    @Shadow private boolean showStations;
    @Shadow @Final private Font textRenderer;
    @Shadow @Final private TransportMode transportMode;
    @Shadow public abstract boolean isMouseOver(double x, double y);

    @Inject(method = "render", at = @At("RETURN"))
    //#if MC_VERSION >= 12000
    private void fangsu$renderBypass(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    //#else
    //$$ private void fangsu$renderBypass(com.mojang.blaze3d.vertex.PoseStack graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
    //#endif
        final var context = GraphicContext.of(graphics);
        fangsu$forEachMarker(marker -> {
            context.fill(Math.max(x, marker.left()), Math.max(y, marker.top()),
                    Math.min(x + width, marker.right()), Math.min(y + height, marker.bottom()), 0xFFFFFFFF);
            if (scale >= 8) {
                context.enableScissor(x, y, x + width, y + height);
                try {
                    context.drawCenteredString(textRenderer, marker.settings().number(), (marker.left() + marker.right()) / 2,
                            (marker.top() + marker.bottom() - textRenderer.lineHeight) / 2, 0xFFFFFFFF);
                } finally {
                    context.disableScissor();
                }
            }
        });
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fangsu$editBypass(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !ClientData.hasPermission() || !isMouseOver(mouseX, mouseY)) {
            return;
        }
        fangsu$forEachMarker(marker -> {
            if (!cir.isCancelled() && marker.contains(mouseX, mouseY)) {
                final var start = marker.start();
                final var end = marker.end();
                final var selection = fangsu$selection;
                if (selection != null) {
                    if (!selection.test(new ServiceStop.Bypass(start, end))) return;
                } else {
                    ServiceRailScreen.openTrack(marker.settings(), value -> ServiceRailPackets.updateTrack(start, end, value));
                }
                cir.setReturnValue(true);
            }
        });
    }

    @Unique
    private void fangsu$forEachMarker(Consumer<BypassMapMarker> consumer) {
        if (!showStations || transportMode != TransportMode.TRAIN) {
            return;
        }
        ClientData.RAILS.forEach((start, connections) -> connections.forEach((end, rail) -> {
            if (start.asLong() >= end.asLong()) {
                return;
            }
            final var settings = ServiceRail.get(rail);
            if (settings == null || settings.kind() != ServiceRail.Kind.BYPASS) {
                return;
            }
            final double blockX = ((long) start.getX() + end.getX()) / 2;
            final double blockZ = ((long) start.getZ() + end.getZ()) / 2;
            final int left = (int) Math.floor(x + (blockX - centerX) * scale + width / 2D);
            final int top = (int) Math.floor(y + (blockZ - centerY) * scale + height / 2D);
            final int right = Math.max(left + 1, (int) Math.floor(x + (blockX + 1 - centerX) * scale + width / 2D));
            final int bottom = Math.max(top + 1, (int) Math.floor(y + (blockZ + 1 - centerY) * scale + height / 2D));
            if (right > x && left < x + width && bottom > y && top < y + height) {
                consumer.accept(new BypassMapMarker(start, end, settings, left, top, right, bottom));
            }
        }));
    }
}
