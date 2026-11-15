package com.fangsu.mixin;

import com.fangsu.mtr.RouteMapSelection;
import com.fangsu.mtr.ServiceStop;
import com.fangsu.mtr.rail.ServiceRail;
import com.fangsu.network.ServiceRailPackets;
import com.fangsu.ui.ServiceRailScreen;
import mtr.client.ClientData;
import mtr.data.TransportMode;
import mtr.screen.WidgetMap;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        fangsu$forEachMarker((start, end, settings, screenX, screenY) -> {
            //#if MC_VERSION >= 12000
            graphics.drawString(textRenderer, settings.number(), screenX + 3, screenY - 4, 0xFFC0C0C0);
            //#else
            //$$ textRenderer.drawShadow(graphics, settings.number(), screenX + 3, screenY - 4, 0xFFC0C0C0);
            //#endif
        });
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fangsu$editBypass(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !ClientData.hasPermission() || !isMouseOver(mouseX, mouseY)) {
            return;
        }
        fangsu$forEachMarker((start, end, settings, screenX, screenY) -> {
            if (!cir.isCancelled() && Math.abs(mouseX - screenX) <= 10 && Math.abs(mouseY - screenY) <= 7) {
                final var selection = fangsu$selection;
                if (selection != null) {
                    if (!selection.test(new ServiceStop.Bypass(start, end))) return;
                } else {
                    ServiceRailScreen.openTrack(settings, value -> ServiceRailPackets.updateTrack(start, end, value));
                }
                cir.setReturnValue(true);
            }
        });
    }

    @Unique
    private void fangsu$forEachMarker(MarkerConsumer consumer) {
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
            final int screenX = x + (int) Math.round(((start.getX() + end.getX()) / 2D - centerX) * scale + width / 2D);
            final int screenY = y + (int) Math.round(((start.getZ() + end.getZ()) / 2D - centerY) * scale + height / 2D);
            if (screenX >= x && screenX < x + width - 10 && screenY >= y && screenY < y + height) {
                consumer.accept(start, end, settings, screenX, screenY);
            }
        }));
    }

    public interface MarkerConsumer {
        void accept(BlockPos start, BlockPos end, ServiceRail settings, int screenX, int screenY);
    }
}
