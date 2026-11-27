package com.fangsu.mixin;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.StationOpening;
import io.netty.buffer.Unpooled;
import mtr.data.Station;
import mtr.mappings.ScreenMapper;
import mtr.packet.IPacket;
import mtr.packet.PacketTrainDataGuiClient;
import mtr.screen.DashboardScreen;
import mtr.screen.EditStationScreen;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EditStationScreen.class, remap = false)
public abstract class EditStationOpeningMixin extends ScreenMapper {
    @Unique private Station fangsu$station;
    @Unique private Checkbox fangsu$deferredCheckbox;

    protected EditStationOpeningMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fangsu$readStation(Station station, DashboardScreen parent, CallbackInfo ci) {
        fangsu$station = station;
    }

    @ModifyConstant(method = {"init", "changeEditingExit"}, constant = @Constant(intValue = 70))
    private int fangsu$moveExitPanels(int y) {
        return y + 20;
    }

    @ModifyConstant(method = "render", constant = {@Constant(intValue = 50), @Constant(intValue = 56)})
    private int fangsu$moveExitHeadings(int y) {
        return y + 20;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void fangsu$addOpeningCheckbox(CallbackInfo ci) {
        final boolean deferred = fangsu$deferredCheckbox == null
                ? StationOpening.isDeferred(fangsu$station) : fangsu$deferredCheckbox.selected();
        final var label = ComponentHelper.translatable("ui.fangsu.station.deferred");
        fangsu$deferredCheckbox = new Checkbox(6, 44, font.width(label) + 25, 20, label, deferred);
        addDrawableChild(fangsu$deferredCheckbox);
    }

    @Inject(method = "saveData", at = @At("RETURN"))
    private void fangsu$saveOpening(CallbackInfo ci) {
        if (fangsu$deferredCheckbox == null) return;
        final boolean deferred = fangsu$deferredCheckbox.selected();
        final var opening = (StationOpening) (Object) fangsu$station;
        if (deferred == opening.fangsu$isDeferred()) return;
        opening.fangsu$setDeferred(deferred);

        final var packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeLong(fangsu$station.id);
        packet.writeUtf(fangsu$station.transportMode.toString());
        packet.writeUtf(StationOpening.KEY);
        packet.writeBoolean(deferred);
        PacketTrainDataGuiClient.sendUpdate(IPacket.PACKET_UPDATE_STATION, packet);
    }
}
