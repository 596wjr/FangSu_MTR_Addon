package com.fangsu.mixin;

import com.fangsu.Main;
import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.data.LiftExtraSupplier;
import com.fangsu.ui.ModelSelectScreen;
import mtr.client.IDrawing;
import mtr.data.LiftClient;
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

import java.lang.reflect.Field;

import static mtr.data.IGui.SQUARE_SIZE;
import static mtr.packet.IPacket.PACKET_UPDATE_LIFT;

@Mixin(LiftCustomizationScreen.class)
public class LiftCustomizationScreenMixin {

    // 只保留 lift 的 Shadow，移除 width2
    @Final
    @Shadow(remap = false)
    private LiftClient lift;

    @Unique
    private Button fangsu$buttonModel;

    // 缓存计算出的宽度，避免反复反射
    @Unique
    private int fangsu$cachedWidth = -1;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(LiftClient lift, CallbackInfo ci) {
        fangsu$buttonModel = Button.builder(
                Component.translatable("ui.fangsu.block.modelSelect"),
                button -> {
                    CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
                    LiftExtraSupplier extraSupplier = (LiftExtraSupplier) this.lift;
                    ModelSelectScreen screen = new ModelSelectScreen(
                            Component.translatable("ui.fangsu.block.modelSelect"),
                            null,
                            customMtrLifts.getInfoList(),
                            (a) -> extraSupplier.fangsu$getModelKey(),
                            (a, b) -> {
                                extraSupplier.fangsu$setModelKey(b);
                                Main.debug("selected {}", b);
                                lift.setExtraData(packet -> PacketTrainDataGuiClient.sendUpdate(PACKET_UPDATE_LIFT, packet));
                            },
                            Minecraft.getInstance().screen
                    );
                    Minecraft.getInstance().setScreen(screen);
                }
        ).build();
    }

    @Inject(method = "updateControls", at = @At("HEAD"), remap = false)
    private void updateControlsHead(CallbackInfo ci) {
        LiftCustomizationScreen screen = (LiftCustomizationScreen) (Object) this;
        int width = fangsu$getPanelWidth(screen); // 动态获取宽度

        IDrawing.setPositionAndWidth(fangsu$buttonModel, 0, SQUARE_SIZE * 11, width);
        screen.addDrawableChild(fangsu$buttonModel);
    }

    /**
     * 动态获取屏幕面板宽度，兼容 MTR 官方版和 Unofficial 版。
     * 官方版使用 width2 字段，Unofficial 版使用 totalWidth 字段。
     */
    @Unique
    private int fangsu$getPanelWidth(LiftCustomizationScreen screen) {
        // 如果已经缓存了宽度，直接返回
        if (fangsu$cachedWidth > 0) {
            return fangsu$cachedWidth;
        }

        Class<?> clazz = screen.getClass();

        // 1. 尝试获取官方版的 width2 字段
        try {
            Field field = clazz.getDeclaredField("width2");
            field.setAccessible(true);
            fangsu$cachedWidth = field.getInt(screen);
            return fangsu$cachedWidth;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // 字段不存在，继续尝试
        }

        // 2. 尝试获取 Unofficial 版的 totalWidth 字段
        try {
            Field field = clazz.getDeclaredField("totalWidth");
            field.setAccessible(true);
            fangsu$cachedWidth = field.getInt(screen);
            return fangsu$cachedWidth;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // 也不存在
        }

        // 3. 降级处理：返回默认宽度
        fangsu$cachedWidth = 200;
        Main.debug("无法获取面板宽度，使用默认值 200");
        return fangsu$cachedWidth;
    }
}