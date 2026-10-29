package com.fangsu.mixin;

import com.fangsu.customItem.NteRailMultiModelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 复用 NTE 原有网格/实例化分块，仅将附属拆成各自的 BakedRail。 */
@Pseudo
@Mixin(targets = {
        "cn.zbx1425.mtrsteamloco.render.rail.RailRenderDispatcher",
        "fabric.cn.zbx1425.mtrsteamloco.render.rail.RailRenderDispatcher",
        "forge.cn.zbx1425.mtrsteamloco.render.rail.RailRenderDispatcher"
}, remap = false)
public class NteRailLayersMixin {
    @Unique
    private final NteRailMultiModelBridge.Layers fangsu$layers = new NteRailMultiModelBridge.Layers();

    @Inject(method = "getModelKeyForRender", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void fangsu$bakingLayer(@Coerce Object rail, CallbackInfoReturnable<String> cir) {
        String key = NteRailMultiModelBridge.bakingKey();
        if (key != null) cir.setReturnValue(key);
    }

    @Inject(method = "getModelKeyForRender", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void fangsu$primaryLayer(@Coerce Object rail, CallbackInfoReturnable<String> cir) {
        if (NteRailMultiModelBridge.bakingKey() == null) {
            String raw = NteRailMultiModelBridge.rawKey(rail);
            if (com.fangsu.customItem.NteRailModelSelection.isCombined(raw)) {
                cir.setReturnValue(NteRailMultiModelBridge.resolvePrimary(rail, cir.getReturnValue()));
            }
        }
    }

    @Inject(method = "registerRail", at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$refreshLayers(@Coerce Object rail, CallbackInfoReturnable<Boolean> cir) {
        fangsu$layers.refresh(this, rail);
    }

    @Inject(method = "addRail", at = @At("RETURN"), require = 0, remap = false)
    private void fangsu$addLayers(@Coerce Object rail, CallbackInfo ci) {
        fangsu$layers.add(this, rail);
    }

    @Inject(method = "removeRail", at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$removeLayers(@Coerce Object rail, CallbackInfo ci) {
        fangsu$layers.remove(this, rail);
    }

    @Inject(method = "clearRail", at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$clearLayers(CallbackInfo ci) {
        // 构造器可能在 Mixin 字段初始化之前调用 clearRail。
        if (fangsu$layers != null) fangsu$layers.clear(this);
    }
}
