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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Train.class, remap = false, priority = 596)
public abstract class TrainMixin {
    /* ==========================================================
     *  Shadow：Train 原生字段 / 方法
     * ========================================================== */

    @Shadow
    protected boolean doorTarget;

    @Shadow
    protected float doorValue;

    @Shadow
    protected abstract boolean openDoors(
            Level world, Block block, BlockPos pos, int dwellTicks
    );

    @Shadow(remap = false)
    protected abstract boolean skipScanBlocks(Level world, double trainX, double trainY, double trainZ);

    //    @Inject(
//            method = "scanDoors",
//            at = @At("RETURN"),
//            cancellable = true,
//            remap = false
//    )
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
//        boolean original = ci.getReturnValue();

        if (skipScanBlocks(world, trainX, trainY, trainZ)) {
            ci.setReturnValue(false);
            return;
        }

        boolean hasPlatform = false;

        final Vec3 offsetVec = new Vec3(1, 0, 0).yRot(checkYaw).xRot(pitch);
        final Vec3 traverseVec = new Vec3(0, 0, 1).yRot(checkYaw).xRot(pitch);

        for (int x = 1; x <= 3; x++) {
            for (int y = -2; y <= 3; y++) {
                for (double z = -halfSpacing; z <= halfSpacing; z++) {

                    //#if MC_VERSION >= 12000
                    BlockPos pos = BlockPos.containing(
                            trainX + offsetVec.x * x + traverseVec.x * z,
                            trainY + y,
                            trainZ + offsetVec.z * x + traverseVec.z * z
                    );
                    //#else
                    //$$ BlockPos pos = new BlockPos(
                    //$$     (int) Math.floor(trainX + offsetVec.x * x + traverseVec.x * z),
                    //$$     (int) Math.floor(trainY + y),
                    //$$     (int) Math.floor(trainZ + offsetVec.z * x + traverseVec.z * z)
                    //$$ );
                    //#endif

                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof IBlockPlatform
                            || block instanceof BlockPlatform || block instanceof BlockPSDAPGBase
                    ) {
                        openDoors(world, block, pos, dwellTicks);
                        BlockEntity entity = world.getBlockEntity(pos);
                        hasPlatform = true;
                        if (entity instanceof IPlatformDoor be) {
                            if (be.isLocked())
                                hasPlatform = false;
                            else {
                                be.setDoorTarget(doorTarget);
                                be.setDoorValue(doorValue);
                            }
                        }
                    }
                }
            }
        }

        ci.setReturnValue(
//                original ||
                hasPlatform);
        ci.cancel();
    }
}