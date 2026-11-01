package com.fangsu.mixin;

import com.fangsu.mtr.rail.NteRailTiltHelper;
import mtr.data.Rail;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NTE/ANTE 轨道烘焙的逐截面滚转；未安装 NTE 时由 {@code @Pseudo} 跳过。
 * 三个目标名覆盖未合包、Fabric、Forge 的打包形式。
 * <p>
 * NTE 0.5.2 在实例方法 {@code lambda$new$1} 中添加矩阵，ANTE 1.1.0
 * 将循环内联进构造器；两个可选钩子分别保留，矩阵类型用 Object 避免依赖 NTE。
 * <p>
 * issue #96：不能在构造器 HEAD 用静态 @Inject 建立上下文，Forge Mixin 0.8.5
 * 要求处理器与构造器静态性一致，而新版 Mixin 又要求 super() 前的处理器为静态。
 * 因此只在 add 前读取已经初始化的 rail 字段，旋转期间建立上下文，finally 清理；
 * 两个 @ModifyArg 都是实例处理器，且注入位置均在 super() 和 rail 赋值之后。
 * require = 0 只允许对应版本不存在该注入点，不能兜住静态性错误。
 */
@Pseudo
@Mixin(targets = {
        NteRailTiltHelper.BAKED_RAIL_TARGET_CN,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FABRIC,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FORGE
}, remap = false)
public abstract class NteBakedRailMixin {

    @Shadow(remap = false)
    @Final
    public Rail rail;

    /** NTE 0.5.2：截面循环位于构造器的实例 lambda 中。 */
    @ModifyArg(
            method = NteRailTiltHelper.BAKED_RAIL_SECTION,
            at = @At(
                    value = "INVOKE",
                    target = NteRailTiltHelper.SECTION_ADD_TARGET,
                    ordinal = NteRailTiltHelper.SECTION_ADD_ORDINAL
            ),
            index = 0,
            require = 0,
            remap = true
    )
    private Object fangsu$tiltNteBakedSection(Object bakedSectionMatrix) {
        NteRailTiltHelper.beginBake(rail);
        try {
            return NteRailTiltHelper.tiltBakedSection(bakedSectionMatrix);
        } finally {
            NteRailTiltHelper.endBake();
        }
    }

    /** ANTE 1.1.0：截面循环内联进构造器，rail 已经赋值。 */
    @ModifyArg(
            method = NteRailTiltHelper.BAKED_RAIL_SECTION_INLINE,
            at = @At(
                    value = "INVOKE",
                    target = NteRailTiltHelper.SECTION_ADD_TARGET,
                    ordinal = NteRailTiltHelper.SECTION_ADD_ORDINAL
            ),
            index = 0,
            require = 0,
            remap = true
    )
    private Object fangsu$tiltNteBakedSectionInline(Object bakedSectionMatrix) {
        NteRailTiltHelper.beginBake(rail);
        try {
            return NteRailTiltHelper.tiltBakedSectionInline(bakedSectionMatrix);
        } finally {
            NteRailTiltHelper.endBake();
        }
    }
}
