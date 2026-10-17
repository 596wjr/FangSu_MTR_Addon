package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import mtr.data.TrainClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 外轨超高（P5-6）：在 {@code mtr.data.TrainClient.simulateCar} 上取出「每节车厢的世界姿态」。
 * <p>
 * <b>为什么选 {@code simulateCar} 作为唯一的数据源</b>：它是每节车厢的唯一入口，且收到的是
 * <b>世界坐标</b>（{@code carX/carY/carZ/carYaw/carPitch}、两端的 {@code prevCarX..prevCarPitch}
 * 与本车厢实长 {@code realSpacing}）。
 * 而在乘车状态下，它交给渲染器的坐标是 {@code carX - viewOffset}（相机相对量）——
 * 拿那组量做轨道匹配必然失败（MTR4 的 BUG 1 正是如此）。因此匹配一律在 HEAD 这里完成。
 * <p>
 * <b>本类现在只剩两个 {@code @Inject}（P5-8 的改动）</b>：原来还带两处 {@code @Redirect}
 * （风挡 / 挡板的 8 个角点随车体滚转），已整体搬到
 * {@link TrainClientConnectionRollMixin}，并由 {@link FangSuMixinPlugin}
 * 按「NTE / ANTE 是否安装」门控。原因见那个类的注释：NTE 的 {@code TrainClientMixin} 会用同描述符的
 * {@code simulateCar} 合并进 {@code TrainClient}，使那两处 {@code @Redirect} 在 prepare 阶段抛
 * {@code InvalidInjectionException} 而崩游戏，而 {@code require = 0} 对此无能为力。
 * <p>
 * <b>这两个 {@code @Inject} 为什么必须无条件留在本类</b>：它们是本特性<b>唯一</b>的数据源 ——
 * 车体、轨面、镜头、乘车四条通道的滚转角全部由 {@link RailTrainRollHelper#captureCar} 算出。
 * 而且 Mixin 的 {@code @Inject} 对「被别的 mixin 合并过的目标方法」照常工作：真实测试者日志
 * （MC 1.20.1 / Fabric Loader 0.19.3 / sponge-mixin 0.17.3+mixin.0.8.7 / ANTE 1.1.0）里
 * 这两个钩子<b>没有任何报错</b>。所以门控它们只会白白丢掉特性，不会带来任何稳定性收益。
 * <p>
 * <b>所有钩子都是 {@code require = 0} + 完整描述符 + 成员级 {@code remap = true}</b>：MTR3 的
 * {@code simulateCar} 只有一个重载，但仍写全描述符 —— 一旦将来多一个同名重载，裸名字就会静默指错
 * （P5-3 的死钩子教训）。{@code remap = true} <b>同样是必需的</b>（本类级写的是 {@code remap = false}）：
 * {@link #SIMULATE_CAR} 的描述符里含 {@code Lnet/minecraft/...}，而运行期会把选择器无条件过一遍
 * refmap；只有有效 {@code remap} 为 true 的成员才会被注解处理器写进 refmap，否则生产命名空间
 * （Fabric intermediary）里这个字符串永远匹配不上，{@code require = 0} 会让它<b>静默失效</b>
 * （2026-10-11 beta 包的实际症状：4 分钟会话只有 1 条 {@code [P5-…]} 日志）。
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
     * 两处 {@code @Inject} 共用它。
     */
    private static final String SIMULATE_CAR =
            "simulateCar(Lnet/minecraft/world/level/Level;IFDDDFFDDDFFZZD)V";

    /**
     * 车厢世界姿态捕获（P5-6 的数据源）。
     * <p>
     * 处理器是<b>实例</b>方法（目标方法是实例方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 强制静态性一致），形参表与目标方法逐字相同，
     * 因此能直接拿到世界坐标。{@code transportMode.railOffset} 也一并传下去：缆车是 -6，
     * 其余是 0，竖向匹配必须扣掉它（{@code javap -c} 核实过这四个常量）。
     */
    @Inject(method = SIMULATE_CAR, at = @At("HEAD"), require = 0, remap = true)
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
    @Inject(method = SIMULATE_CAR, at = @At("RETURN"), require = 0, remap = true)
    private void fangsu$endCarRoll(
            Level world, int ridingCar, float ticksElapsed,
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch,
            boolean doorLeftOpen, boolean doorRightOpen, double realSpacing,
            CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.endCar();
    }
}
