package com.fangsu.mixin;

import com.fangsu.Main;
import com.llamalad7.mixinextras.sugar.Local;
import mtr.data.LiftClient;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = LiftClient.class, remap = false)
public class LiftClientMixin extends LiftMixin {

    @Unique
    private static final String MODEL_KEY = "fangsu_model_key";

    @Inject(
            method = "setExtraData(Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private void writeExtraData(Consumer<FriendlyByteBuf> sendPacket, CallbackInfo ci, @Local FriendlyByteBuf packet) {
        packet.writeUtf(MODEL_KEY);
        packet.writeUtf(fangsu$getModelKey());
        Main.debug("saving(client) {}", fangsu$getModelKey());
    }
}
