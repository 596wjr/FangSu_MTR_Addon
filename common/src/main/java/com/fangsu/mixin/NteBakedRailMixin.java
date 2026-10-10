package com.fangsu.mixin;

import com.fangsu.mtr.rail.NteRailTiltHelper;
import mtr.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NTE（{@code mtrsteamloco}）轨道渲染路径的外轨超高（P5-4 / 路径 B）。
 * <p>
 * <b>NTE 非方速前置，因此使用字符串 {@code targets} + {@code @Pseudo}：</b>未安装 NTE 时
 * 目标类不存在，Mixin 静默跳过该 mixin，方速照常启动。这与本仓库既有的
 * {@code MetropolisTurnstileMixin} 是同一套写法，注册在同一个 {@code fangsu.mixins.json} 里。
 * <ul>
 *   <li>目标类名必须写<b>三遍</b>：forgix 打出来的 NTE jar 里
 *       {@code cn.zbx1425.mtrsteamloco…} / {@code fabric.cn.zbx1425.mtrsteamloco…} /
 *       {@code forge.cn.zbx1425.mtrsteamloco…} 是三个不同的类（二进制名带前缀）。</li>
 *   <li>{@code remap = false}：NTE 的类名不在 MTR/MC 命名空间里，没有 refmap 可映射。</li>
 *   <li>每个注入器都写<b>显式 {@code method}</b> 且 {@code require = 0}
 *       —— 将来 NTE 改名只降级为「轨面不倾斜」，不会崩游戏；失败由
 *       {@link NteRailTiltHelper} 的一次性诊断报出来（本仓库在 P5-3 上正是靠这条纪律
 *       才发现三个钩子从未生效）。</li>
 * </ul>
 * <b>为什么注入点是「{@code ArrayList.add(Object)} 的 {@code @ModifyArg}」</b>：
 * 本 mixin 编译时 NTE <b>不在类路径上</b>（它不是构建依赖），所以处理器方法的签名里
 * <b>不能出现任何 NTE 类型</b>；{@code BakedRail.lambda$new$1} 里那次 {@code ArrayList.add}
 * 的实参在字节码里就是 {@code Ljava/lang/Object;}，处理器写成 {@code (Object)Object}
 * 与目标逐字符相同，不需要 {@code @Coerce}、不涉及泛型。
 * {@code BakedRail.getLookAtMat}（返回 {@code sowcer.math.Matrix4f}）无法这样写：
 * 要么在签名里出现 NTE 类型，要么依赖 {@code CallbackInfoReturnable} 的泛型实参被处理器接受
 * —— 两者都比这里脆弱。矩阵本身由 {@link NteRailTiltHelper} 用运行时反射调用
 * {@code rotateZ(float)} 完成旋转。
 * <p>
 * <b>静态性</b>（对 {@code mixin-patched 0.8.5.12} 的字节码逐条核实）：
 * {@code CallbackInjector.inject()} 用 {@code checkTargetModifiers(target, true)}，
 * 静态性必须与目标一致；目标 {@code <init>} 是实例方法，所以两个 {@code @Inject} 处理器都是
 * <b>非静态</b>。{@code @ModifyArg} <b>也是严格一致</b>：{@code ModifyArgInjector extends
 * InvokeInjector}，而 {@code InvokeInjector.inject()} 用的是
 * {@code checkTargetModifiers(target, true)}（只有 {@code RedirectInjector} 自己覆写成
 * {@code false}）—— 目标 {@code lambda$new$1} 是实例方法，所以 {@code @ModifyArg} 处理器
 * <b>必须非静态</b>，写成静态会在 NTE 在场时抛 {@code InvalidInjectionException}
 * （{@code require = 0} 拦不住这类异常）。它不使用 {@code this}，非静态只是为了让静态性匹配。
 * <p>
 * <b>与路径 A（{@link com.fangsu.mixin.RenderTrainsMixin} / P5-3）不冲突</b>：NTE 只在
 * {@code ClientConfig.getRailRenderLevel() >= 2} 且
 * {@code RailRenderDispatcher.registerRail(rail)} 返回 true 时取消 MTR 的轨面绘制；
 * 那时路径 A 的 {@code IDrawing.drawTexture} 重定向一次都不会被调用，反之亦然。
 * 本 mixin 只修改 NTE 已经烘焙好的矩阵，不触碰那个取消判断。
 */
@Pseudo
@Mixin(targets = {
        NteRailTiltHelper.BAKED_RAIL_TARGET_CN,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FABRIC,
        NteRailTiltHelper.BAKED_RAIL_TARGET_FORGE
}, remap = false)
public abstract class NteBakedRailMixin {

    /**
     * {@code BakedRail.<init>(mtr.data.Rail)} 的 {@code @At("HEAD")}：建立「正在烘焙哪条轨道」的窗口。
     * <p>
     * 描述符已用 {@code javap -p -s} 核实为 {@code (Lmtr/data/Rail;)V}；<b>写完整描述符</b>
     * 而不是裸 {@code "<init>"}，否则 NTE 多一个构造器重载时目标会不受控。
     */
    @Inject(
            method = NteRailTiltHelper.BAKED_RAIL_INIT,
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void fangsu$beginNteBake(Rail rail, CallbackInfo callbackInfo) {
        NteRailTiltHelper.beginBake(rail);
    }

    /**
     * 同一个构造器的 {@code @At("RETURN")}：结束窗口。
     * <p>
     * 目标构造器有<b>两处</b> {@code return}（{@code modelKey.equals("null")} 的早退与正常返回，
     * {@code javap -p -c} 偏移 101 与 60），{@code @At("RETURN")} 覆盖全部返回点，不会留残值。
     */
    @Inject(
            method = NteRailTiltHelper.BAKED_RAIL_INIT,
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void fangsu$endNteBake(Rail rail, CallbackInfo callbackInfo) {
        NteRailTiltHelper.endBake();
    }

    /**
     * 逐截面旋转：把 NTE 刚造出来、即将存进 {@code coveredChunks} 的那个 4×4 截面矩阵
     * 按内核的滚转角就地旋转。
     * <p>
     * 注入点已用 {@code javap -p -c} 核实：{@code lambda$new$1} 里<b>只有一处</b>
     * {@code invokevirtual java/util/ArrayList.add:(Ljava/lang/Object;)Z}（偏移 93）。
     * {@code ordinal = 0} 把它钉死在那一处 —— 将来 NTE 在同一个 lambda 里再加一处 {@code add}，
     * 也不会被重复旋转。
     * <p>
     * <b>无滚转时返回同一个对象引用</b>：{@code add} 收到的还是 NTE 原来那个矩阵，
     * 字节完全相同（见 {@link NteRailTiltHelper#tiltBakedSection}）。
     * <p>
     * 两个 chunk 实现（{@code InstancedRailChunk} / {@code MeshBuildingRailChunk}）消费的是
     * <b>同一批</b> {@code coveredChunks} 矩阵，所以这一个注入点同时覆盖实例化与 mesh 两条路径。
     * <p>
     * <b>非静态是因为静态性必须与目标一致</b>（见类注释），本方法不使用 {@code this}。
     */
    @ModifyArg(
            method = NteRailTiltHelper.BAKED_RAIL_SECTION,
            at = @At(
                    value = "INVOKE",
                    target = NteRailTiltHelper.SECTION_ADD_TARGET,
                    ordinal = NteRailTiltHelper.SECTION_ADD_ORDINAL
            ),
            index = 0,
            require = 0,
            remap = false
    )
    private Object fangsu$tiltNteBakedSection(Object bakedSectionMatrix) {
        return NteRailTiltHelper.tiltBakedSection(bakedSectionMatrix);
    }
}
