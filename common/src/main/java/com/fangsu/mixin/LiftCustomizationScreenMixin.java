package com.fangsu.mixin;

import com.fangsu.mappings.ComponentHelper;
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

    // 鍙繚锟?lift 锟?Shadow锛岀Щ锟?width2
    @Final
    @Shadow(remap = false)
    private LiftClient lift;

    @Unique
    private Button fangsu$buttonModel;

    // 缂撳瓨璁＄畻鍑虹殑瀹藉害锛岄伩鍏嶅弽澶嶅弽锟?
    @Unique
    private int fangsu$cachedWidth = -1;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void onInit(LiftClient lift, CallbackInfo ci) {
        //#if MC_VERSION >= 11903
        fangsu$buttonModel = Button.builder(
                ComponentHelper.translatable("ui.fangsu.block.modelSelect"),
                button -> {
                    CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
                    LiftExtraSupplier extraSupplier = (LiftExtraSupplier) this.lift;
                    ModelSelectScreen screen = new ModelSelectScreen(
                            ComponentHelper.translatable("ui.fangsu.block.modelSelect"),
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
        //#else
        //$$ fangsu$buttonModel = new Button(0, 0, 0, 0,
        //$$         ComponentHelper.translatable("ui.fangsu.block.modelSelect"),
        //$$         button -> {
        //$$             CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
        //$$             LiftExtraSupplier extraSupplier = (LiftExtraSupplier) this.lift;
        //$$             ModelSelectScreen screen = new ModelSelectScreen(
        //$$                     ComponentHelper.translatable("ui.fangsu.block.modelSelect"),
        //$$                     null,
        //$$                     customMtrLifts.getInfoList(),
        //$$                     (a) -> extraSupplier.fangsu$getModelKey(),
        //$$                     (a, b) -> {
        //$$                         extraSupplier.fangsu$setModelKey(b);
        //$$                         Main.debug("selected {}", b);
        //$$                         lift.setExtraData(packet -> PacketTrainDataGuiClient.sendUpdate(PACKET_UPDATE_LIFT, packet));
        //$$                     },
        //$$                     Minecraft.getInstance().screen
        //$$             );
        //$$             Minecraft.getInstance().setScreen(screen);
        //$$         }
        //$$ );
        //#endif
    }

    @Inject(method = "updateControls", at = @At("HEAD"), remap = false)
    private void updateControlsHead(CallbackInfo ci) {
        LiftCustomizationScreen screen = (LiftCustomizationScreen) (Object) this;
        int width = fangsu$getPanelWidth(screen); // 鍔ㄦ€佽幏鍙栧锟?

        IDrawing.setPositionAndWidth(fangsu$buttonModel, 0, SQUARE_SIZE * 11, width);
        screen.addDrawableChild(fangsu$buttonModel);
    }

    /**
     * 鍔ㄦ€佽幏鍙栧睆骞曢潰鏉垮搴︼紝鍏煎 MTR 瀹樻柟鐗堝拰 Unofficial 鐗堬拷?
     * 瀹樻柟鐗堜娇锟?width2 瀛楁锛孶nofficial 鐗堜娇锟?totalWidth 瀛楁锟?
     */
    @Unique
    private int fangsu$getPanelWidth(LiftCustomizationScreen screen) {
        // 濡傛灉宸茬粡缂撳瓨浜嗗搴︼紝鐩存帴杩斿洖
        if (fangsu$cachedWidth > 0) {
            return fangsu$cachedWidth;
        }

        Class<?> clazz = screen.getClass();

        // 1. 灏濊瘯鑾峰彇瀹樻柟鐗堢殑 width2 瀛楁
        try {
            Field field = clazz.getDeclaredField("width2");
            field.setAccessible(true);
            fangsu$cachedWidth = field.getInt(screen);
            return fangsu$cachedWidth;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // 瀛楁涓嶅瓨鍦紝缁х画灏濊瘯
        }

        // 2. 灏濊瘯鑾峰彇 Unofficial 鐗堢殑 totalWidth 瀛楁
        try {
            Field field = clazz.getDeclaredField("totalWidth");
            field.setAccessible(true);
            fangsu$cachedWidth = field.getInt(screen);
            return fangsu$cachedWidth;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // 涔熶笉瀛樺湪
        }

        // 3. 闄嶇骇澶勭悊锛氳繑鍥為粯璁ゅ锟?
        fangsu$cachedWidth = 200;
        Main.debug("鏃犳硶鑾峰彇闈㈡澘瀹藉害锛屼娇鐢ㄩ粯璁わ拷?200");
        return fangsu$cachedWidth;
    }
}