package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import mtr.data.TrainClient;
import mtr.render.TrainRendererBase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MTR3 外轨超高（P5-6）的<b>风挡 / 挡板滚转</b>通道：{@code TrainClient.simulateCar} 里
 * {@code TrainRendererBase.renderConnection} / {@code renderBarrier} 两处 {@code @Redirect}。
 * <p>
 * <b>它为什么从 {@link TrainClientRollMixin} 里搬出来（P5-8）</b>：
 * NTE / ANTE 的 {@code fabric.cn.zbx1425.mtrsteamloco.mixin.TrainClientMixin} 会用一个<b>同描述符</b>的
 * {@code simulateCar} 方法体合并进 {@code mtr.data.TrainClient}
 * （Mixin 日志原文：{@code simulateCar(...)V merged by ...TrainClientMixin with priority 1000}）。
 * 一旦发生这种合并，本类的两处 {@code @Redirect} 会在 <b>{@code INJECT_PREPARE_LEGACY} 阶段</b>抛
 * {@code InvalidInjectionException}（真实测试者日志：MC 1.20.1 / Fabric Loader 0.19.3 /
 * sponge-mixin 0.17.3+mixin.0.8.7 / ANTE 1.1.0），导致 {@code TrainClient} 整个类加载失败 → 崩游戏。
 * <p>
 * {@code require = 0} <b>兜不住</b>这一类失败：异常来自「注入点（INVOKE 节点）在目标方法里找不到」，
 * 与 {@code require} 无关。因此修法是结构性的：把这两个注入<b>单独放进一个类</b>，
 * 再由 {@link FangSuMixinPlugin}（{@code fangsu.mixins.json} 的 {@code "plugin"}）在
 * 「装了 NTE / ANTE 家族」时<b>整体跳过本类</b>。
 * <p>
 * <b>为什么 {@link TrainClientRollMixin} 的另外两个 {@code @Inject} 不一起搬</b>：Mixin 的
 * {@code @Inject} 对「被合并的目标方法」照常工作（真实日志里 {@code captureCar} / {@code endCar}
 * 在 ANTE 环境下<b>没有任何报错</b>），而且它们是本特性唯一的数据源 ——
 * 车体、轨面、镜头、乘车四条通道的滚转角全靠 {@code captureCar} 算出来，
 * 一旦被门控掉，装了 NTE 时连车体都不会倾斜。所以它们必须<b>无条件下</b>留在原来的 mixin 里。
 * <p>
 * <b>代价与取舍</b>：装了 NTE / ANTE 时风挡与挡板<b>不随车体滚转</b>（少了这一个装饰性通道），
 * 换来「任何情况下都不会因为这两个注入而崩游戏」。这与 P5-6 的既有边界一致 ——
 * {@link RailTrainRollHelper} 里那条一次性告警会把这种情况明确报出来。
 * <p>
 * <b>无超高时的行为必须与 MTR 原生逐位一致</b>：{@link RailTrainRollHelper#rolledConnectionPoints}
 * 返回 {@code null} 时，处理器<b>原样转发原来的 8 个 {@link Vec3} 实例</b>（不是拷贝、不是等价对象），
 * 这一点与搬移之前的实现逐字相同，语义没有任何改动。
 * <p>
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里：{@code TrainClient} 是客户端类。
 */
@Mixin(value = TrainClient.class, remap = false)
public abstract class TrainClientConnectionRollMixin {

    /**
     * {@code TrainClient.simulateCar(...)} 的完整描述符（{@code javap -p} 逐字核实）。
     * <p>
     * 形参：{@code (Level world, int ridingCar, float ticksElapsed, double carX, double carY, double carZ,
     * float carYaw, float carPitch, double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw,
     * float prevCarPitch, boolean doorLeftOpen, boolean doorRightOpen, double realSpacing)}。
     * <p>
     * <b>{@code remap = true} 是必需的</b>（本类级写的是 {@code remap = false}）：描述符里含
     * {@code Lnet/minecraft/...}，运行期会把选择器无条件过一遍 refmap，只有有效 {@code remap} 为 true
     * 的成员才会被注解处理器写进 refmap；否则生产命名空间（Fabric intermediary / Forge SRG）里
     * 这个字符串永远匹配不上，而 {@code require = 0} 会让它<b>静默失效</b>（P5-3 的死钩子教训）。
     */
    private static final String SIMULATE_CAR =
            "simulateCar(Lnet/minecraft/world/level/Level;IFDDDFFDDDFFZZD)V";

    /** {@code TrainRendererBase.renderConnection} 的 13 参数描述符。 */
    private static final String RENDER_CONNECTION =
            "Lmtr/render/TrainRendererBase;renderConnection(Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DDDFF)V";

    /** 同 {@link #RENDER_CONNECTION}，目标换成 {@code renderBarrier}。 */
    private static final String RENDER_BARRIER =
            "Lmtr/render/TrainRendererBase;renderBarrier(Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DDDFF)V";

    /**
     * 风挡随两端车体滚转。
     * <p>
     * {@code simulateCar} 里的 {@code renderConnection} 调用<b>只有一处</b>
     * （{@code javap -p -c} 核实：MTR 3.2.2 偏移 760、MTR 3.6.2.2 偏移 758），因此不需要 {@code ordinal}；
     * 描述符自带 {@code renderConnection} 与 {@code renderBarrier} 的区分。
     * <p>
     * 处理器是<b>实例</b>方法（目标方法是实例方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 强制静态性一致）。第一个形参 = INVOKE 的 receiver。
     */
    @Redirect(
            method = SIMULATE_CAR,
            at = @At(value = "INVOKE", target = RENDER_CONNECTION),
            require = 0,
            remap = true
    )
    private void fangsu$rollConnection(
            TrainRendererBase renderer,
            Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4,
            Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4,
            double x, double y, double z, float yaw, float pitch
    ) {
        // ★ 门控的第二道保险（第一道是 FangSuMixinPlugin 直接跳过本类）：
        // 万一将来某个构建忘了往 mixins.json 注册 plugin，或者 plugin 被别的 mixin 框架旁路，
        // 这里就地放弃注入 —— 把 NTE 环境下的「prepare 阶段硬崩」降级为「风挡不滚转」。
        // 判据与 plugin 完全同源（Class.forName），不会因为本模组自己的类加载顺序而误判。
        if (FangSuMixinPlugin.nteFamilyLoaded()) {
            renderer.renderConnection(
                    prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4,
                    x, y, z, yaw, pitch);
            return;
        }
        final Vec3[] rolled = RailTrainRollHelper.rolledConnectionPoints(
                prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4);
        if (rolled == null) {
            // 无滚转：原样转发原来的 8 个实例，与 MTR 原生逐位一致
            renderer.renderConnection(
                    prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4,
                    x, y, z, yaw, pitch);
            return;
        }
        renderer.renderConnection(
                rolled[0], rolled[1], rolled[2], rolled[3], rolled[4], rolled[5], rolled[6], rolled[7],
                x, y, z, yaw, pitch);
    }

    /** 挡板随两端车体滚转（同 {@link #fangsu$rollConnection}，{@code simulateCar} 里也只有一处调用）。 */
    @Redirect(
            method = SIMULATE_CAR,
            at = @At(value = "INVOKE", target = RENDER_BARRIER),
            require = 0,
            remap = true
    )
    private void fangsu$rollBarrier(
            TrainRendererBase renderer,
            Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4,
            Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4,
            double x, double y, double z, float yaw, float pitch
    ) {
        if (FangSuMixinPlugin.nteFamilyLoaded()) {
            renderer.renderBarrier(
                    prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4,
                    x, y, z, yaw, pitch);
            return;
        }
        final Vec3[] rolled = RailTrainRollHelper.rolledConnectionPoints(
                prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4);
        if (rolled == null) {
            renderer.renderBarrier(
                    prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4,
                    x, y, z, yaw, pitch);
            return;
        }
        renderer.renderBarrier(
                rolled[0], rolled[1], rolled[2], rolled[3], rolled[4], rolled[5], rolled[6], rolled[7],
                x, y, z, yaw, pitch);
    }
}
