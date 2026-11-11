package com.fangsu.mixin;

import com.fangsu.platform.PlatformPlacement;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class PlatformOverlayPlacementMixin {
    @Inject(method="updatePlacementContext",at=@At("RETURN"),cancellable=true)
    private void fangsu$underSkin(BlockPlaceContext original,CallbackInfoReturnable<BlockPlaceContext> result){result.setReturnValue(PlatformPlacement.underSkin((BlockItem)(Object)this,result.getReturnValue()));}
}
