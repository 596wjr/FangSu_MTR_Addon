package com.fangsu.mixin;

import com.fangsu.customItem.NteRailMultiModelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NTE 非必装依赖，三种打包前缀均通过字符串目标可选注入。 */
@Pseudo
@Mixin(targets = {
        "cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen",
        "fabric.cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen",
        "forge.cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen",
        "cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen$SelectScreen",
        "fabric.cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen$SelectScreen",
        "forge.cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen$SelectScreen"
}, remap = false)
public class NteRailSelectionScreenMixin {
    @Inject(method = "loadPage", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void fangsu$selectLayers(CallbackInfo ci) {
        if (NteRailMultiModelBridge.openSelection(this)) ci.cancel();
    }

    @Inject(method = "loadMainPage", at = @At("RETURN"), require = 0, remap = false)
    private void fangsu$selectionLabel(CallbackInfo ci) {
        NteRailMultiModelBridge.refreshLabels(this);
    }
}
