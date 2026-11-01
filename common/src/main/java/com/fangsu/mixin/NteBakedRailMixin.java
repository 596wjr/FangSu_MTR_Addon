package com.fangsu.mixin;

import com.fangsu.mtr.rail.NteRailTiltHelper;
import mtr.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * NTE/ANTE 轨道烘焙的逐截面滚转；未安装 NTE 时由 {@code @Pseudo} 跳过。
 * 三个目标名覆盖未合包、Fabric、Forge 的打包形式。
 * <p>
 * NTE 0.5.2 在实例方法 {@code lambda$new$1} 中添加矩阵，ANTE 1.1.0
 * 将循环内联进构造器；两个可选钩子分别保留，矩阵类型用 Object 避免依赖 NTE。
 * <p>
 * issue #96/#98：Forge 0.8.5 与新版 Mixin 对构造器 HEAD 的静态性要求不同，
 * 且正式发布的 NTE/ANTE BakedRail 不保证存在 rail 字段，不能 @Shadow 该字段。
 * 在 super() 之后的 getModelKeyForRender(Rail) 调用前记录其参数，原样返回，
 * 只写入方速自己的 @Unique 字段；截面旋转期间建立上下文，finally 清理。
 * 三个调用目标分别覆盖未合包、Fabric、Forge 包名，缺失时允许跳过。
 */
@Pseudo
@Mixin(targets = {
        NteRailTiltHelper.BAKED_RAIL_TARGET_CN,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FABRIC,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FORGE
}, remap = false)
public abstract class NteBakedRailMixin {

    /** 不依赖外部类的字段布局；不设置初始化器，避免构造器注入顺序影响。 */
    @Unique
    private Rail fangsu$bakingRail;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcn/zbx1425/mtrsteamloco/render/rail/RailRenderDispatcher;getModelKeyForRender(Lmtr/data/Rail;)Ljava/lang/String;"),
            index = 0, require = 0, remap = false)
    private Rail fangsu$captureRail(Rail rail) {
        fangsu$bakingRail = rail;
        return rail;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lfabric/cn/zbx1425/mtrsteamloco/render/rail/RailRenderDispatcher;getModelKeyForRender(Lmtr/data/Rail;)Ljava/lang/String;"),
            index = 0, require = 0, remap = false)
    private Rail fangsu$captureFabricRail(Rail rail) {
        fangsu$bakingRail = rail;
        return rail;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lforge/cn/zbx1425/mtrsteamloco/render/rail/RailRenderDispatcher;getModelKeyForRender(Lmtr/data/Rail;)Ljava/lang/String;"),
            index = 0, require = 0, remap = false)
    private Rail fangsu$captureForgeRail(Rail rail) {
        fangsu$bakingRail = rail;
        return rail;
    }

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
        NteRailTiltHelper.beginBake(fangsu$bakingRail);
        try {
            return NteRailTiltHelper.tiltBakedSection(bakedSectionMatrix);
        } finally {
            NteRailTiltHelper.endBake();
        }
    }

    /** ANTE 1.1.0：截面循环内联进构造器，模型键查询时已捕获轨道。 */
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
        NteRailTiltHelper.beginBake(fangsu$bakingRail);
        try {
            return NteRailTiltHelper.tiltBakedSectionInline(bakedSectionMatrix);
        } finally {
            NteRailTiltHelper.endBake();
        }
    }
}
