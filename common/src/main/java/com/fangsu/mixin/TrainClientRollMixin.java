package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import mtr.data.TrainClient;
import mtr.render.TrainRendererBase;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 外轨超高（P5-6）：在 {@code mtr.data.TrainClient.simulateCar} 上取出「每节车厢的世界姿态」，
 * 并把风挡 / 挡板的 8 个角点绕各自车厢的纵轴滚转。
 * <p>
 * <b>为什么选 {@code simulateCar} 作为唯一的数据源</b>：它是每节车厢的唯一入口，且收到的是
 * <b>世界坐标</b>（{@code carX/carY/carZ/carYaw/carPitch}、两端的 {@code prevCarX..prevCarPitch}
 * 与本车厢实长 {@code realSpacing}）。
 * 而在乘车状态下，它交给渲染器的坐标是 {@code carX - viewOffset}（相机相对量）——
 * 拿那组量做轨道匹配必然失败（MTR4 的 BUG 1 正是如此）。因此匹配一律在 HEAD 这里完成。
 * <p>
 * <b>为什么 {@code renderConnection} / {@code renderBarrier} 用 {@code @Redirect} 而不是 {@code @ModifyArgs}</b>：
 * <ul>
 *   <li>这两个方法是 {@link TrainRendererBase} 的 <b>public abstract</b> API，签名一个字都不能改；</li>
 *   <li>调用点全部参数都是 <b>public</b> 类型（{@link Vec3} 与基本类型），所以 {@code @ModifyArgs} 在
 *       这里其实也不会踩 MTR4 那个 {@code IllegalAccessError}（那边是包级私有的
 *       {@code RenderVehicles$IndexedConsumer}）；但 {@code @ModifyArgs} 会在<b>运行时</b>生成
 *       {@code org.spongepowered.asm.synthetic.args.Args$N} 访问器类，而 {@code @Redirect} 不生成任何类 ——
 *       本特性只改「传进去的 8 个角点」，没有任何理由引入那层生成代码；</li>
 *   <li>{@code @Redirect} 的处理器自己完成等价调用，因此「一个实参都不改」时可以逐字转发原实例
 *       （{@link RailTrainRollHelper#rolledConnectionPoints} 返回 {@code null} 时），
 *       无超高路径与 MTR 原生逐位一致。</li>
 * </ul>
 * <p>
 * <b>所有钩子都是 {@code require = 0} + 完整描述符</b>：MTR3 的 {@code simulateCar} 只有一个重载，
 * 但仍写全描述符 —— 一旦将来多一个同名重载，裸名字就会静默指错（P5-3 的死钩子教训）。
 * 每一个可能静默失效的钩子都由 {@link RailTrainRollHelper} 的一次性诊断兜底。
 * <p>
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里：{@code TrainClient} 是客户端类。
 */
@Mixin(value = TrainClient.class, remap = false)
public abstract class TrainClientRollMixin {

    /**
     * {@code TrainClient.simulateCar(...)} 的完整描述符（{@code javap -p} 逐字核实）。
     * <p>
     * 形参：{@code (Level world, int ridingCar, float ticksElapsed, double carX, double carY, double carZ,
     * float carYaw, float carPitch, double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw,
     * float prevCarPitch, boolean doorLeftOpen, boolean doorRightOpen, double realSpacing)}。
     * 两处 {@code @Inject} 与两处 {@code @Redirect} 共用它。
     */
    private static final String SIMULATE_CAR =
            "simulateCar(Lnet/minecraft/world/level/Level;IFDDDFFDDDFFZZD)V";

    /** {@code TrainRendererBase.renderConnection} / {@code renderBarrier} 的 13 参数描述符（两者相同）。 */
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
     * 车厢世界姿态捕获（P5-6 的数据源）。
     * <p>
     * 处理器是<b>实例</b>方法（目标方法是实例方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 强制静态性一致），形参表与目标方法逐字相同，
     * 因此能直接拿到世界坐标。{@code transportMode.railOffset} 也一并传下去：缆车是 -6，
     * 其余是 0，竖向匹配必须扣掉它（{@code javap -c} 核实过这四个常量）。
     */
    @Inject(method = SIMULATE_CAR, at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$captureCarRoll(
            Level world, int ridingCar, float ticksElapsed,
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch,
            boolean doorLeftOpen, boolean doorRightOpen, double realSpacing,
            CallbackInfo callbackInfo
    ) {
        final TrainClient train = (TrainClient) (Object) this;
        RailTrainRollHelper.captureCar(
                train.trainRenderer, ridingCar,
                carX, carY, carZ, carYaw, carPitch,
                realSpacing,
                train.transportMode == null ? 0 : train.transportMode.railOffset
        );
    }

    /**
     * 结束本车厢的同步窗口。
     * <p>
     * 它只清 {@link RailTrainRollHelper} 里那个 ThreadLocal 上下文；失效无害（每次 HEAD 都会整体覆盖），
     * 因此 {@link RailTrainRollHelper} 不为它单独发告警，只由探针断言「清得干净」。
     */
    @Inject(method = SIMULATE_CAR, at = @At("RETURN"), require = 0, remap = false)
    private void fangsu$endCarRoll(
            Level world, int ridingCar, float ticksElapsed,
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch,
            boolean doorLeftOpen, boolean doorRightOpen, double realSpacing,
            CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.endCar();
    }

    /**
     * 风挡随两端车体滚转。
     * <p>
     * {@code simulateCar} 里的 {@code renderConnection} 调用<b>只有一处</b>
     * （{@code javap -p -c} 核实：偏移 760），因此不需要 {@code ordinal}；
     * 描述符自带 {@code renderConnection} 与 {@code renderBarrier} 的区分。
     */
    @Redirect(
            method = SIMULATE_CAR,
            at = @At(value = "INVOKE", target = RENDER_CONNECTION),
            require = 0,
            remap = false
    )
    private void fangsu$rollConnection(
            TrainRendererBase renderer,
            Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4,
            Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4,
            double x, double y, double z, float yaw, float pitch
    ) {
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

    /** 挡板随两端车体滚转（同 {@link #fangsu$rollConnection}，{@code simulateCar} 里也只有一处调用，偏移 805）。 */
    @Redirect(
            method = SIMULATE_CAR,
            at = @At(value = "INVOKE", target = RENDER_BARRIER),
            require = 0,
            remap = false
    )
    private void fangsu$rollBarrier(
            TrainRendererBase renderer,
            Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4,
            Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4,
            double x, double y, double z, float yaw, float pitch
    ) {
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
