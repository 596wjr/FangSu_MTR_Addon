package com.fangsu.mixin;

import com.fangsu.platform.PlatformOverlayCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原生表面覆盖层同时作用于服务端和客户端的行走与选中形状。 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlatformOverlayShapeMixin {
    @Inject(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void fangsu$overlayCollision(BlockGetter view,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> result){result.setReturnValue(PlatformOverlayCollision.add(view,pos,result.getReturnValue(),false));}
    @Inject(method="getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",at=@At("RETURN"),cancellable=true)
    private void fangsu$overlayOutline(BlockGetter view,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> result){result.setReturnValue(PlatformOverlayCollision.add(view,pos,result.getReturnValue(),true));}
}
