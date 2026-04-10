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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = Train.class, remap = false, priority = 1100)
public abstract class TrainMixin {

    /* ==========================================================
     *  反射加载你的平台 Block 接口
     * ========================================================== */

    @Unique
    private static final Class<?> fangsu$IBlockPlatformClass = IBlockPlatform.class;

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

        for (int x = 1; x <= 3; x++) {
            for (int y = -2; y <= 3; y++) {
                for (double z = -halfSpacing; z <= halfSpacing; z++) {

                    BlockPos pos = BlockPos.containing(
                            trainX + offsetVec.x * x + traverseVec.x * z,
                            trainY + y,
                            trainZ + offsetVec.z * x + traverseVec.z * z
                    );

                    Block block = world.getBlockState(pos).getBlock();

                    if (block instanceof BlockPlatform || block instanceof BlockPSDAPGBase || fangsu$IBlockPlatformClass.isInstance(block)) {
                        openDoors(world, block, pos, dwellTicks);
                        hasPlatform = true;
                    }
                    if (block instanceof IBlockPlatform) {
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

        ci.setReturnValue(hasPlatform);
        ci.cancel();
    }
}