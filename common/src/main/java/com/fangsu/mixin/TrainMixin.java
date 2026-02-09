package com.fangsu.mixin;

import com.fangsu.blockEntities.IPlatformDoor;
import com.fangsu.blocks.IBlockPlatform;
import mtr.block.BlockPSDAPGBase;
import mtr.block.BlockPlatform;
import mtr.data.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if FABRIC
//$$ import fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
//#elseif FORGE
//$$ import forge.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
//#endif

import java.util.HashSet;
import java.util.Set;

@Mixin(value = Train.class, remap = false, priority = 1100)
public abstract class TrainMixin {

    /* ==========================================================
     *  反射加载你的平台 Block 接口
     * ========================================================== */

    private static Class<?> IBlockPlatformClass = IBlockPlatform.class;

    /* ==========================================================
     *  Shadow：Train 原生字段 / 方法
     * ========================================================== */

    @Shadow
    protected boolean doorTarget;

    @Shadow
    protected float doorValue;

    @Shadow
    protected abstract boolean skipScanBlocks(
            Level world, double trainX, double trainY, double trainZ
    );

    @Shadow
    protected abstract boolean openDoors(
            Level world, Block block, BlockPos pos, int dwellTicks
    );

    /* ==========================================================
     *  核心注入：scanDoors
     * ========================================================== */

    @Inject(
            method = "scanDoors",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void scanCustomDoors(
            Level world,
            double trainX, double trainY, double trainZ,
            float checkYaw, float pitch,
            double halfSpacing, int dwellTicks,
            CallbackInfoReturnable<Boolean> ci
    ) {
        if (skipScanBlocks(world, trainX, trainY, trainZ)) {
            ci.setReturnValue(false);
            ci.cancel();
            return;
        }

        boolean hasPlatform = false;

        final Vec3 offsetVec = new Vec3(1, 0, 0).yRot(checkYaw).xRot(pitch);
        final Vec3 traverseVec = new Vec3(0, 0, 1).yRot(checkYaw).xRot(pitch);
        //#if ANTE
        Set<BlockPos> OKPos = new HashSet<>();
        //#endif

        boolean isClientSide = world.isClientSide();

        for (int x = 1; x <= 3; x++) {
            for (int y = -2; y <= 3; y++) {
                for (double z = -halfSpacing; z <= halfSpacing; z++) {

                    BlockPos pos = BlockPos.containing(
                            trainX + offsetVec.x * x + traverseVec.x * z,
                            trainY + y,
                            trainZ + offsetVec.z * x + traverseVec.z * z
                    );

                    Block block = world.getBlockState(pos).getBlock();

                    if (block instanceof BlockPlatform || block instanceof BlockPSDAPGBase || IBlockPlatformClass.isInstance(block)) {
                        openDoors(world, block, pos, dwellTicks);
                        hasPlatform = true;
                    } else if (block instanceof IBlockPlatform) {
                        openDoors(world, block, pos, dwellTicks);
                        BlockEntity entity = world.getBlockEntity(pos);
                        if (isClientSide)
                            if (entity instanceof IPlatformDoor be) {
                                be.setDoorTarget(doorTarget);
                                be.setDoorValue(doorValue);
                            }
                        hasPlatform = true;
                    }

                    //#if ANTE
                    //$$ else if (block instanceof BlockEyeCandy) {
                    //$$     if (OKPos.contains(pos)) continue;
                    //$$     int[] dir = new int[]{1, -1};
                    //$$     int[] f = new int[]{1, 0, 0, 1, 0, 0};
                    //$$     if (checkEyeCandy(world, pos, isClientSide)) hasPlatform = true;
                    //$$     for (int i = 0; i < 3; i++) {
                    //$$         for (int j = 0; j < 2; j++) {
                    //$$             for (int k = 1; k <= 40; k++) {
                    //$$                 int v = dir[j] * k;
                    //$$                 BlockPos thisPos = pos.offset(f[i] * v, f[i + 1] * v, f[i + 2] * v);
                    //$$                 if (OKPos.contains(thisPos)) break;
                    //$$                 OKPos.add(thisPos);
                    //$$                 if (checkEyeCandy(world, thisPos, isClientSide)) hasPlatform = true;
                    //$$                 else break;
                    //$$             }
                    //$$         }
                    //$$     }
                    //$$ }
                    //#endif
                }
            }
        }

        ci.setReturnValue(hasPlatform);
        ci.cancel();
    }

    //#if ANTE
    //$$ private boolean checkEyeCandy(Level world, BlockPos pos, boolean isClientSide) {
    //$$ 		final BlockEntity entity = world.getBlockEntity(pos);
    //$$ 		if (entity instanceof BlockEyeCandy.BlockEntityEyeCandy) {
    //$$ 			BlockEyeCandy.BlockEntityEyeCandy e = (BlockEyeCandy.BlockEntityEyeCandy) entity;
    //$$ 			if (e.isPlatform()) {
    //$$ 				if (isClientSide) {
    //$$ 					e.setDoorTarget(doorTarget);
    //$$ 					e.setDoorValue(doorValue);
    //$$ 				}
    //$$ 				return true;
    //$$ 			} else return false;
    //$$ 		} else {
    //$$ 			return false;
    //$$ 		}
    //$$ 	}
    //#endif
}