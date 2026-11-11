package com.fangsu.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.*;
//#if MC_VERSION >= 11903
import org.joml.Matrix4f;
//#else
//$$ import com.mojang.math.Matrix4f;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 跨 Fabric / Forge 的世界覆盖层入口，保留原生方块的模型与行为。 */
@Mixin(LevelRenderer.class)
public final class PlatformOverlayWorldMixin {
    @Inject(method="renderLevel",at=@At("TAIL"))
    private void fangsu$platformOverlays(PoseStack poses,float delta,long limit,boolean outline,Camera camera,GameRenderer renderer,LightTexture light,Matrix4f projection,CallbackInfo ci){
        com.fangsu.platform.PlatformOverlayClient.render(poses,delta);
    }
}
