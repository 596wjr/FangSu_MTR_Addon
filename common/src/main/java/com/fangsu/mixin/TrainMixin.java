package com.fangsu.mixin;

import com.fangsu.blockEntities.IPlatformDoor;
import com.fangsu.blocks.IBlockPlatform;
import com.fangsu.Main;
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

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Mixin(value = Train.class, remap = false, priority = 100)  // ① 降低优先级，确保后执行
public abstract class TrainMixin {

    // ==================== 检测 Metropolis 模组 ====================
    @Unique
    private static Class<?> fangsu$IBlockPlatformClass = Void.class;
    @Unique
    private static boolean fangsu$foundMetropolis = false;

    // ==================== 检测 ANTE 模组的 BlockEyeCandy ====================
    @Unique
    private static Class<?> fangsu$BlockEyeCandyClass = Void.class;
    @Unique
    private static Class<?> fangsu$BlockEyeCandyEntityClass = Void.class;
    @Unique
    private static boolean fangsu$foundEyeCandy = false;
    @Unique
    private static Method fangsu$isPlatformMethod;
    @Unique
    private static Method fangsu$setDoorTargetMethod;
    @Unique
    private static Method fangsu$setDoorValueMethod;

    static {
        // Metropolis
        try {
            fangsu$IBlockPlatformClass = Class.forName(
                    "team.dovecotmc.metropolis.block.interfaces.IBlockPlatform",
                    false,
                    Main.class.getClassLoader()
            );
            Main.LOGGER.info("Loaded metropolis IBlockPlatformClass");
            fangsu$foundMetropolis = true;
        } catch (Exception ignored) {
        }

        // ANTE
        try {
            // ANTE 1.1.0+ 为双平台合一打包：common 代码类名带 fabric. / forge. 前缀
            // （如 fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy），因此按多种类名依次尝试
            fangsu$BlockEyeCandyClass = fangsu$loadClass(
                    "cn.zbx1425.mtrsteamloco.block.BlockEyeCandy",
                    "fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy",
                    "forge.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy"
            );
            fangsu$BlockEyeCandyEntityClass = fangsu$loadClass(
                    "cn.zbx1425.mtrsteamloco.block.BlockEyeCandy$BlockEntityEyeCandy",
                    "fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy$BlockEntityEyeCandy",
                    "forge.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy$BlockEntityEyeCandy"
            );
            fangsu$isPlatformMethod = fangsu$BlockEyeCandyEntityClass.getMethod("isPlatform");
            fangsu$setDoorTargetMethod = fangsu$BlockEyeCandyEntityClass.getMethod("setDoorTarget", boolean.class);
            fangsu$setDoorValueMethod = fangsu$BlockEyeCandyEntityClass.getMethod("setDoorValue", float.class);
            Main.LOGGER.info("Loaded mtrsteamloco BlockEyeCandy: " + fangsu$BlockEyeCandyClass.getName());
            fangsu$foundEyeCandy = true;
        } catch (Exception ignored) {
        }
    }

    /**
     * 依次尝试加载类，返回第一个加载成功的；全部失败则抛出最后的异常。
     * 用于兼容不同打包方式下类名是否带 fabric. / forge. 前缀。
     */
    @Unique
    private static Class<?> fangsu$loadClass(String... classNames) throws ClassNotFoundException {
        ClassNotFoundException lastException = null;
        for (String className : classNames) {
            try {
                return Class.forName(className, false, Main.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                lastException = e;
            }
        }
        throw lastException != null ? lastException : new ClassNotFoundException();
    }

    /* ==========================================================
     *  Shadow：Train 原生字段 / 方法
     * ========================================================== */
    @Shadow
    protected boolean doorTarget;

    @Shadow
    protected float doorValue;

    @Shadow
    protected abstract boolean openDoors(Level world, Block block, BlockPos pos, int dwellTicks);

    @Shadow(remap = false)
    protected abstract boolean skipScanBlocks(Level world, double trainX, double trainY, double trainZ);

    /**
     * 完全覆盖 scanDoors 逻辑。
     * 优先级设为 100（低于默认 1000），确保在其他 Mixin 之后执行，覆盖其返回值。
     */
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
            return;
        }

        boolean hasPlatform = false;
        final boolean isClientSide = world.isClientSide();

        final Vec3 offsetVec = new Vec3(1, 0, 0).yRot(checkYaw).xRot(pitch);
        final Vec3 traverseVec = new Vec3(0, 0, 1).yRot(checkYaw).xRot(pitch);

        // 用于避免重复检查 BlockEyeCandy 的相邻方块
        Set<BlockPos> checkedPositions = new HashSet<>();

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

                    // ----- 处理原版 / 自定义 / Metropolis 的站台门 -----
                    if (block instanceof IBlockPlatform
                            || block instanceof BlockPlatform
                            || block instanceof BlockPSDAPGBase
                            || (fangsu$foundMetropolis && fangsu$IBlockPlatformClass.isInstance(block))
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
                    // ----- 处理 MTR Steam Loco 的 BlockEyeCandy（兼容） -----
                    else if (fangsu$foundEyeCandy && fangsu$BlockEyeCandyClass.isInstance(block)) {
                        // 直接扫描当前方块
                        if (fangsu$checkEyeCandy(world, pos, isClientSide)) {
                            hasPlatform = true;
                        }
                        // 沿三个轴向（X, Z, Y）各正反方向延伸最多 40 格
                        int[] dir = new int[]{1, -1};
                        int[] f = new int[]{1, 0, 0, 1, 0, 0};
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 2; j++) {
                                for (int k = 1; k <= 40; k++) {
                                    int v = dir[j] * k;
                                    BlockPos checkPos = pos.offset(
                                            f[i] * v,
                                            f[i + 1] * v,
                                            f[i + 2] * v
                                    );
                                    if (checkedPositions.contains(checkPos)) break;
                                    checkedPositions.add(checkPos);
                                    if (fangsu$checkEyeCandy(world, checkPos, isClientSide)) {
                                        hasPlatform = true;
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ci.setReturnValue(hasPlatform);
        ci.cancel();
    }

    /**
     * 检查单个方块是否为 BlockEyeCandy 且 isPlatform() == true，
     * 若是客户端则同步 doorTarget / doorValue。
     */
    @Unique
    private boolean fangsu$checkEyeCandy(Level world, BlockPos pos, boolean isClientSide) {
        if (!fangsu$foundEyeCandy) return false;
        BlockEntity entity = world.getBlockEntity(pos);
        if (entity == null) return false;
        if (!fangsu$BlockEyeCandyEntityClass.isInstance(entity)) return false;

        try {
            boolean isPlatform = (boolean) fangsu$isPlatformMethod.invoke(entity);
            if (isPlatform) {
                if (isClientSide) {
                    fangsu$setDoorTargetMethod.invoke(entity, doorTarget);
                    fangsu$setDoorValueMethod.invoke(entity, doorValue);
                }
                return true;
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        return false;
    }
}