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
 * <b>静态性</b>（对 <b>生产运行时</b> {@code sponge-mixin 0.17.3+mixin.0.8.7} 与本地 AP 用的
 * {@code mixin-patched 0.8.5.12} 逐条核实；两版对 {@code @Inject} 的判据<b>不同</b>，以下以运行时为准）：
 * <ul>
 *   <li><b>{@code @Inject} 没有「与目标静态性一致」的要求。</b>
 *       {@code CallbackInjector.sanityCheck} 调的是 {@code checkTargetModifiers(target, false)}
 *       （非严格），只禁止「非静态处理器打静态目标」。真正管 {@code @Inject} 静态性的是
 *       {@code Injector.checkTargetForNode}：<b>目标是构造器</b>且注入点在
 *       {@code <init>} 的委托调用（{@code invokespecial <init>}，即 {@code super()}/{@code this()}）
 *       <b>之前或正好落在其上</b>时，处理器<b>必须 {@code static}</b>，否则在 APPLY 阶段抛
 *       {@code InvalidInjectionException}（"handler before super() invocation must be static"）
 *       —— 这是<b>硬错误</b>，{@code require = 0} 拦不住（{@code require} 只降级「找不到目标」）。</li>
 *   <li>本类 {@code @At("HEAD")} 的节点是构造器第一条指令（{@code aload_0}，索引 0），
 *       {@code super()} 委托调用在索引 1 ⇒ 注入点在委托之前 ⇒
 *       <b>{@code fangsu$beginNteBake} 必须 {@code static}</b>；{@code @At("RETURN")} 落在
 *       {@code return} 上，索引必然大于委托调用 ⇒ 在 {@code super()} 之后 ⇒
 *       <b>静态/非静态都合法，{@code fangsu$endNteBake} 保持非静态</b>
 *       （与目标同为实例成员，在严格版本下也成立）。</li>
 *   <li>{@code @ModifyArg} 是另一套：{@code ModifyArgInjector extends InvokeInjector}，
 *       {@code InvokeInjector.sanityCheck} → {@code checkTarget} 要求「非静态处理器不能打静态目标」，
 *       而本地 AP 的 {@code mixin-patched 0.8.5.12} 更严格（{@code checkTargetModifiers(target, true)}，
 *       静态性必须<b>逐字一致</b>）。目标 {@code lambda$new$1} 是实例方法，所以
 *       {@code @ModifyArg} 处理器<b>保持非静态</b>；它不使用 {@code this}，非静态只是为了让静态性匹配。</li>
 * </ul>
 * <b>教训（beta 测试崩溃报告，2026-10-11）</b>：本类曾经两个 {@code @Inject} 都是非静态，
 * 依据的是本地 AP jar（{@code mixin-patched 0.8.5.12}，{@code @Inject} 恰好是严格一致）的字节码，
 * 于是在 {@code RailRenderDispatcher.registerLightUpdate} 第一次加载 {@code BakedRail} 时
 * <b>整局崩溃</b>（"@At(\"HEAD\") selector @Inject handler before super() invocation must be
 * static"）。{@code CallbackInjector} 的静态性判据<b>不能</b>用 AP jar 推断，必须以运行时
 * （{@code sponge-mixin}）为准；离线探针 {@code WiringProbe} 现在会按注入点位置把这条规则钉死。
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
     * <p>
     * <b>必须是 {@code static}</b>（见类注释「静态性」）：{@code @At("HEAD")} 的节点是构造器第一条指令
     * （{@code aload_0}，索引 0），而 {@code super()} 委托调用在索引 1，注入点<b>落在委托之前</b>，
     * Mixin 的 {@code Injector.checkTargetForNode} 会拒绝非静态处理器。处理器不使用 {@code this}
     * —— 烘焙状态全在 {@link NteRailTiltHelper} 的静态成员里，签名 {@code (Rail, CallbackInfo)}
     * 已经拿到需要的全部信息。
     */
    @Inject(
            method = NteRailTiltHelper.BAKED_RAIL_INIT,
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void fangsu$beginNteBake(Rail rail, CallbackInfo callbackInfo) {
        NteRailTiltHelper.beginBake(rail);
    }

    /**
     * 同一个构造器的 {@code @At("RETURN")}：结束窗口。
     * <p>
     * 目标构造器有<b>两处</b> {@code return}（{@code modelKey.equals("null")} 的早退与正常返回，
     * {@code javap -p -c} 偏移 101 与 60），{@code @At("RETURN")} 覆盖全部返回点，不会留残值。
     * <p>
     * <b>{@code RETURN} 一律在 {@code super()} 之后</b>（委托调用后面才可能有 {@code return}），
     * 因此这里<b>不</b>受「super() 之前必须静态」的约束：静态与非静态处理器都合法
     * （{@code Injector.checkTargetForNode} 在节点位于委托之后时直接返回，不再查静态性）。
     * 保持<b>非静态</b>：它与目标构造器同为实例成员，在严格校验的 Mixin 版本
     * （如本地 AP 用的 {@code mixin-patched 0.8.5.12}，{@code sanityCheck} 走
     * {@code checkTargetModifiers(target, true)}）下也一定成立，是最稳的一侧。
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
     * <b>非静态是 {@code @ModifyArg}/{@code InvokeInjector} 自己的规则</b>（与 {@code @Inject} 不同，
     * 见类注释「静态性」）：{@code InvokeInjector} 要求「非静态处理器不能打静态目标」，本地 AP
     * 的 {@code mixin-patched 0.8.5.12} 更要求静态性逐字一致；目标 {@code lambda$new$1} 是实例方法。
     * 本方法不使用 {@code this}，非静态只是为了让静态性匹配。
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
