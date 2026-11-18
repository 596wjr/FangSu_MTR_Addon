package com.fangsu.mixin;

import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.StationOpening;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mtr.data.MessagePackHelper;
import mtr.data.Station;
import net.minecraft.network.FriendlyByteBuf;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Mixin(value = Station.class, remap = false)
public abstract class StationOpeningMixin implements StationOpening {
    @Unique private static final String FANGSU_OPENING_MARKER = "\u0000" + StationOpening.KEY;
    @Shadow @Final public Map<String, List<String>> exits;
    @Unique private volatile boolean fangsu$deferred;

    @Override
    public boolean fangsu$isDeferred() {
        return fangsu$deferred;
    }

    @Override
    public void fangsu$setDeferred(boolean deferred) {
        if (fangsu$deferred != deferred) {
            fangsu$deferred = deferred;
            DrawableRoute.clearCache();
        }
    }

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void fangsu$readOpening(Map<String, Value> data, CallbackInfo ci) {
        fangsu$deferred = new MessagePackHelper(data).getBoolean(StationOpening.KEY);
    }

    @Inject(method = "toMessagePack", at = @At("RETURN"))
    private void fangsu$writeOpening(MessagePacker packer, CallbackInfo ci) throws IOException {
        if (fangsu$deferred) packer.packString(StationOpening.KEY).packBoolean(true);
    }

    @Inject(method = "messagePackLength", at = @At("RETURN"), cancellable = true)
    private void fangsu$countOpening(CallbackInfoReturnable<Integer> cir) {
        if (fangsu$deferred) cir.setReturnValue(cir.getReturnValueI() + 1);
    }

    // 以保留的出口记录传输额外字段，保持原版站点数据包的结构。
    @ModifyExpressionValue(method = "writePacket", at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I"))
    private int fangsu$countOpeningMarker(int count) {
        return fangsu$deferred ? count + 1 : count;
    }

    @Inject(method = "writePacket", at = @At("RETURN"))
    private void fangsu$writeOpeningMarker(FriendlyByteBuf packet, CallbackInfo ci) {
        if (fangsu$deferred) {
            packet.writeUtf(FANGSU_OPENING_MARKER);
            packet.writeInt(0);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), remap = true)
    private void fangsu$readOpeningMarker(FriendlyByteBuf packet, CallbackInfo ci) {
        fangsu$setDeferred(exits.remove(FANGSU_OPENING_MARKER) != null);
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void fangsu$updateOpening(String key, FriendlyByteBuf packet, CallbackInfo ci) {
        if (StationOpening.KEY.equals(key)) {
            if (packet.isReadable()) fangsu$setDeferred(packet.readBoolean());
            ci.cancel();
        }
    }
}
