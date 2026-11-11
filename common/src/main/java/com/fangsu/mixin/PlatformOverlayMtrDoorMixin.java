package com.fangsu.mixin;

import com.fangsu.platform.PlatformMtrDoorPlacement;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 保留 MTR3 原有多格门构建流程，仅把可覆盖顶皮纳入可用位置。 */
@Mixin(value=mtr.item.ItemPSDAPGBase.class,remap=false)
public abstract class PlatformOverlayMtrDoorMixin {
    @Inject(method="blocksNotReplaceable",at=@At("HEAD"),cancellable=true,remap=false)
    private static void fangsu$coverable(UseOnContext context,int width,int height,Block blacklist,CallbackInfoReturnable<Boolean> result){
        Boolean blocked=PlatformMtrDoorPlacement.blocked(context,width,height,blacklist,true);if(blocked!=null)result.setReturnValue(blocked);
    }
}
