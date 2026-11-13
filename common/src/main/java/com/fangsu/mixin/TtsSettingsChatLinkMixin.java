package com.fangsu.mixin;

import com.fangsu.events.JoinInMessage;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class TtsSettingsChatLinkMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void fangsu$openTtsSettings(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (JoinInMessage.handleClick(style)) cir.setReturnValue(true);
    }
}
