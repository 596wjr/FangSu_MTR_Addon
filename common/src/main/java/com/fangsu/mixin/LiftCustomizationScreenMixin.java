package com.fangsu.mixin;

import com.fangsu.Main;
import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.data.LiftExtraSupplier;
import com.fangsu.ui.ModelSelectScreen;
import mtr.client.IDrawing;
import mtr.data.LiftClient;
import mtr.mappings.Text;
import mtr.packet.PacketTrainDataGuiClient;
import mtr.screen.LiftCustomizationScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static mtr.data.IGui.SQUARE_SIZE;
import static mtr.packet.IPacket.PACKET_UPDATE_LIFT;

@Mixin(LiftCustomizationScreen.class)
public class LiftCustomizationScreenMixin {
    @Final
    @Shadow(remap = false)
    private int width2;

    @Final
    @Shadow(remap = false)
    private LiftClient lift;

    @Unique
    private Button fangsu$buttonModel;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(LiftClient lift, CallbackInfo ci) {
        // 初始化按钮，点击事件留空
        fangsu$buttonModel = net.minecraft.client.gui.components.Button.builder(
                Component.translatable("ui.fangsu.block.modelSelect"),
                button -> {
                    CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
                    LiftExtraSupplier extraSupplier = (LiftExtraSupplier) this.lift;
                    ModelSelectScreen screen = new ModelSelectScreen(
                            Component.translatable("ui.fangsu.block.modelSelect"),
                            null, customMtrLifts.getInfoList(), (a) -> extraSupplier.fangsu$getModelKey(),
                            (a, b) -> {
                                extraSupplier.fangsu$setModelKey(b);
                                Main.debug("selected {}", b);
                                lift.setExtraData(packet -> PacketTrainDataGuiClient.sendUpdate(PACKET_UPDATE_LIFT, packet));
                            }, Minecraft.getInstance().screen
                    );
                    Minecraft.getInstance().setScreen(screen);
                }
        ).build();
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lmtr/screen/LiftCustomizationScreen;updateControls()V"), remap = false)
    private void onInit(CallbackInfo ci) {
        LiftCustomizationScreen screen = (LiftCustomizationScreen) (Object) this;

        IDrawing.setPositionAndWidth(fangsu$buttonModel, 0, SQUARE_SIZE * 11, width2);

        screen.addDrawableChild(fangsu$buttonModel);
    }


}