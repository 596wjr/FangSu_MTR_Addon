package com.fangsu.mixin;

import com.fangsu.platform.PlatformOverlays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 屏蔽门等覆盖方块被拆除后恢复顶皮；状态改变不会丢失布局。 */
@Mixin(Level.class)
public abstract class PlatformOverlayStateMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void fangsu$platformChanged(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValue()&&(Object)this instanceof ServerLevel level)PlatformOverlays.get(level).changed(level,pos,state);
    }
}
