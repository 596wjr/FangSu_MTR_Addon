package com.fangsu.mixin;

import com.fangsu.train.DoorSideObserverMtr3;
import mtr.data.TrainClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 开门方向观测的<b>薄委托</b>：把 {@code TrainClient.simulateCar} 收到的
 * {@code doorLeftOpen / doorRightOpen} 原样转给 {@link DoorSideObserverMtr3}。
 * <p>
 * <b>为什么锚在 {@code simulateCar}</b>：它是每节车厢每帧的唯一入口，且参数里就带着
 * 「本帧这一节贴着哪一侧站台开门」的两个布尔值（MTR 已经替我们做完了方块扫描）。
 * 同类的另一个用途（外轨超高）也锚在这里，见 {@link TrainClientRollMixin}。
 * <p>
 * <b>注入风格与 {@link TrainClientRollMixin} 刻意保持一致：完整描述符 + 成员级 {@code remap = true}。</b>
 * 原因见那个类的注释（第 33-39 行）：{@link #SIMULATE_CAR} 的描述符里含
 * {@code Lnet/minecraft/world/level/Level;}，运行期会把选择器无条件过一遍 refmap；
 * 只有<b>有效 {@code remap} 为 true 的成员</b>才会被注解处理器写进 refmap，
 * 否则在 Fabric 生产命名空间（intermediary，{@code Level} 会变成 {@code net/minecraft/class_1937}）里
 * 这个字符串永远匹配不上 —— 而 {@code require = 0} 会让它<b>静默失效</b>
 * （本仓库已踩过这个坑：整个会话只出现 1 条相关日志）。所以
 * <b>裸名字 + {@code remap = false} 的写法在这里是不允许的</b>，必须写全描述符并让 remap 生效。
 * <p>
 * <b>为什么 {@code require = 0}</b>：NTE / ANTE 的 {@code TrainClientMixin} 会把一个同描述符的
 * {@code simulateCar} 方法体合并进 {@code TrainClient}。{@code @Inject} 对合并后的方法照常工作，
 * 但万一将来 MTR 改签名，{@code require = 0} 能让本模组「安静地少一个特性」而不是崩游戏；
 * 失效会由 {@link DoorSideObserverMtr3} 的 1200 tick 看门狗 WARN 兜底。
 * <p>
 * <b>处理器只做一件事</b>：{@code onCarObserved} 在渲染线程执行，只写内存状态、不发包
 * （发包只在客户端 tick 里做）。处理器必须是<b>私有实例方法</b>（目标方法是实例方法，
 * Mixin 的 {@code Injector.checkTargetModifiers} 强制静态性一致）。
 * <p>
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里：{@code TrainClient} 是客户端类。
 */
@Mixin(value = TrainClient.class, remap = false)
public class TrainClientDoorSideMixin {

    /**
     * {@code TrainClient.simulateCar(...)} 的完整描述符（与 {@link TrainClientRollMixin} 里的同名常量逐字一致）。
     * <p>
     * 形参：{@code (Level world, int ridingCar, float ticksElapsed, double carX, double carY, double carZ,
     * float carYaw, float carPitch, double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw,
     * float prevCarPitch, boolean doorLeftOpen, boolean doorRightOpen, double realSpacing)}——
     * 已对照 {@code mtr3/.../mtr/data/TrainClient.java} 逐字核实。
     */
    private static final String SIMULATE_CAR =
            "simulateCar(Lnet/minecraft/world/level/Level;IFDDDFFDDDFFZZD)V";

    /**
     * 把本帧这一节车厢的开门方向转给观测器。
     * <p>
     * 形参表必须与目标方法<b>逐字一致</b>（类型、顺序），否则注入失败；末尾追加
     * {@link CallbackInfo} 是 Mixin 的约定。
     */
    @Inject(method = SIMULATE_CAR, at = @At("HEAD"), require = 0, remap = true)
    private void fangsu$observeDoorSide(
            Level world, int ridingCar, float ticksElapsed,
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch,
            boolean doorLeftOpen, boolean doorRightOpen, double realSpacing,
            CallbackInfo callbackInfo
    ) {
        DoorSideObserverMtr3.onCarObserved(((TrainClient) (Object) this).id, ridingCar, doorLeftOpen, doorRightOpen);
    }
}
