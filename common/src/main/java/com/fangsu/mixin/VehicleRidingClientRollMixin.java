package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import mtr.data.VehicleRidingClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * MTR3 外轨超高（P5-7）：让乘车玩家<b>站到倾斜的车厢地板上</b>。
 * <p>
 * <b>问题</b>：P5-1/P5-2/P5-3 之后轨道中心线抬升、轨面滚转，P5-6 之后车体与风挡也滚转，
 * 但 MTR 3.2.2 计算玩家位置的那一步完全不知道滚转：
 * <pre>
 *   VehicleRidingClient.setOffsets(...)  :109
 *     playerOffset = new Vec3(percentageX, riderOffset, 纵向偏移).xRot(pitchAngle).yRot(yaw)
 * </pre>
 * 于是玩家（以及第三人称里别的玩家看到的本体）仍然<b>竖直地站在倾斜的地板上</b>，
 * 脚一半埋进地板、一半悬空。
 * <p>
 * <b>本修复</b>：不试图旋转任何 AABB（MTR3 的车厢内部可行走区域同样是轴对齐的，
 * 而且局部偏移 {@code percentagesX/Z} 也是发给服务端的量，必须原样不动），
 * 而是把「车厢局部偏移 → 世界坐标」这一步的<b>输入</b>补上滚转：在 MTR 的
 * {@code xRot(pitchAngle)} <b>之前</b>，把局部偏移绕车厢局部 {@code +Z}（= 纵轴 = 车头前进轴）
 * 按车体滚转角做右手旋转。数学上等价于「整个车厢地板随车体一起滚转」，
 * 而局部偏移本身、AABB 判定、车门 / 风挡换乘与同步逻辑逐字不变。
 * <p>
 * <b>为什么只钉一个 {@code @Redirect}</b>：{@code setOffsets} 里
 * {@code Vec3.xRot(F)} 只有<b>一处</b>调用点（{@code javap -p -c} 核实：偏移 146；
 * 同方法内 {@code yRot} 另有两处，偏移 117/151，不受影响），而 {@code playerOffset} 这个值
 * 有<b>三个</b>消费者，全都读同一个实例：
 * <ul>
 *   <li>{@code :111 riderPositions.put(uuid, playerOffset.add(x, y, z))} —— 第三人称里别的玩家看到的本体；</li>
 *   <li>{@code :114-116 + :148 clientPlayer.absMoveTo(...)} —— 本地玩家的真实世界位置；</li>
 *   <li>{@code :167-169 offset.add(playerOffset.*)} —— {@code getViewOffset()}，即渲染时相机相对量。</li>
 * </ul>
 * 因此「本体位置」与「视线偏移」<b>必然一起跟随</b>，不存在只改一半的可能
 * （{@code .tmp_p5_7_probe/RidingPlayerTiltProbe.java} 对这三项各自做了数值断言）。
 * <p>
 * <b>角度与轴从哪来</b>：{@link RailTrainRollHelper} —— 与车体（P5-6）、风挡、轨面（P5-3）
 * 用的是<b>同一个</b>滚转角表达式、同一份内核、同一套匹配。因此「轨面往哪边斜、
 * 车体就往哪边斜、玩家就站在往哪边斜的地板上」是构造性保证，而不是三处各写一遍。
 * <p>
 * <b>为什么是 {@code movePlayer} 上的那个 {@code @Inject}</b>：{@code movePlayer} 是每 tick
 * 「计算全部乘车实体位置」的唯一入口，它开头就把 {@code offset} / {@code riderPositions} 清空；
 * 本类在同一个位置把 P5-7 的滚转上下文一起清成 {@code null}，于是「下车 / 停止 / 换车」之后
 * 相机读到的必然是没有滚转 —— 不会残留上一帧的角度。
 * <p>
 * <b>无滚转时严格 no-op</b>：{@link RailTrainRollHelper#applyRidingPlayerRoll} 在上下文为
 * {@code null}（未乘车 / 所在车厢没有超高 / 匹配不上）时，直接调用 MTR 原来的
 * {@code vec.xRot(pitchAngle)}，一个浮点运算都不做，与原生逐位一致。
 * <p>
 * <b>已知局限（不修，与 MTR4 相同）</b>：MC 的玩家实体没有 roll 字段，因此<b>第一人称以外</b>的
 * 「玩家朝向」无法随车体倾斜 —— 相机（{@code GameRendererTiltMixin}）负责第一人称的观感，
 * 而第三人称里那个玩家模型仍然会竖直地站在倾斜的地板上。这是 MC 实体的表达能力限制，
 * 不是本步骤的遗漏。
 * <p>
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里：{@code VehicleRidingClient}
 * 与 {@code LocalPlayer} 都是客户端类，专用服务端不会加载它。
 */
@Mixin(value = VehicleRidingClient.class, remap = false)
public class VehicleRidingClientRollMixin {

    /**
     * {@code VehicleRidingClient.setOffsets(...)} 的完整描述符
     * （{@code javap -p} 逐字核实，17 个形参）。
     * <p>
     * <b>必须写完整描述符</b>：本仓库的纪律是「同名方法一律写全描述符」——MTR3 的 {@code setOffsets}
     * 目前只有一个重载，但一旦将来多一个，裸名字就会由注解处理器自行挑选、静默指错
     * （P5-3 三个死钩子的教训）。
     */
    private static final String SET_OFFSETS =
            "setOffsets(Ljava/util/UUID;DDDFFDIZZZZFFZZLjava/lang/Runnable;)V";

    /**
     * {@code VehicleRidingClient.movePlayer(Consumer)} —— 每 tick 的乘车位置计算入口。
     */
    private static final String MOVE_PLAYER = "movePlayer(Ljava/util/function/Consumer;)V";

    /**
     * 每 tick 开头撤销上一帧的乘车滚转上下文。
     * <p>
     * 处理器是<b>实例</b>方法：目标方法是实例方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 要求静态性一致（{@code require = 0} <b>不能</b>
     * 抑制静态性不匹配导致的 {@code InvalidInjectionException}，那种失败是致命的）。
     */
    @Inject(method = MOVE_PLAYER, at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$beginRidingFrame(Consumer<java.util.UUID> ridingEntityCallback, CallbackInfo callbackInfo) {
        RailTrainRollHelper.beginRidingFrame();
    }

    /**
     * 本地玩家的 {@code setOffsets} 被调用：算出本车厢的滚转角与纵轴（<b>本帧</b>上下文）。
     * <p>
     * 这里用的 x/y/z 是 {@code Train.calculateCar} 给的<b>世界坐标</b>（两端点弦中点 + 1 格），
     * 不是相机相对量，因此可以直接做轨道匹配 —— 与 P5-6 的 {@code captureCar} 同源。
     * 「是不是本地玩家」由 {@link RailTrainRollHelper} 的调用方约定保证：MTR 只会为本地玩家
     * 走 `isClientPlayer` 分支，而本钩子对每个乘车实体都会触发；为了不把别人的车厢姿态
     * 当成自己的，这里只在 {@code uuid} 等于本地玩家 UUID 时写上下文。
     * <p>
     * {@code Minecraft.getInstance().player} 在 {@code setOffsets} 内部已被判定为非空
     * （源码 :101-104 提前返回），因此这里再取一次是安全的；用 {@code player != null} 兜底
     * 是为了不在任何异常情况下抛异常。
     */
    @Inject(method = SET_OFFSETS, at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$captureRidingCar(
            java.util.UUID uuid, double x, double y, double z, float yaw, float pitch, double length, int width,
            boolean doorLeftOpen, boolean doorRightOpen, boolean hasPitchAscending, boolean hasPitchDescending,
            float riderOffset, float riderOffsetDismounting, boolean shouldSetOffset, boolean shouldSetYaw,
            Runnable clientPlayerCallback, CallbackInfo callbackInfo
    ) {
        final net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (clientPlayer == null || uuid == null || !uuid.equals(clientPlayer.getUUID())) {
            return;
        }
        RailTrainRollHelper.markRidingPlayerPosition(x, y, z, yaw, pitch, length);
    }

    /**
     * 把「车厢局部偏移」绕车厢局部 {@code +Z} 旋转车体滚转角，再交给 MTR 原来的 {@code xRot}。
     * <p>
     * <b>目标调用点</b>：{@code Vec3.xRot(F)V} 在 {@code setOffsets} 里<b>唯一</b>
     * （{@code javap -p -c} 的字节码偏移 146，对应的常量池项是
     * {@code Method net/minecraft/world/phys/Vec3.xRot:(F)Lnet/minecraft/world/phys/Vec3;}）。
     * 因此<b>不需要 {@code ordinal}</b>；但 {@code @At} 的 {@code target} 与 {@code method}
     * 仍然都显式写出（Mixin 0.8.7 对缺 {@code method} 的注入注解直接抛
     * {@code InvalidInjectionException}，游戏在 APPLY 阶段即崩）。
     * <p>
     * <b>静态性</b>：目标调用 {@code Vec3.xRot} 是<b>实例</b>方法且 {@code Vec3} 是 public 类，
     * 因此处理器是非静态的、第一个形参就是接收者 {@code Vec3} —— 与
     * {@code JonModelTrainRendererRollMixin} 里 {@code rotateX} 的重定向同一种形状。
     * <p>
     * <b>返回 {@code null} 的处理</b>：{@code setOffsets} 里 {@code new Vec3(...)} 永远非空，
     * 因此接收者不可能为 {@code null}；helper 仍然兜底转发（宁可原样画，也绝不抛异常）。
     * 目标方法的返回类型是 <b>public</b> 类 {@code Vec3}，不存在 MTR4 上
     * 「{@code @ModifyArgs} 触碰包级私有类型导致 {@code IllegalAccessError}」的风险。
     */
    @Redirect(
            method = SET_OFFSETS,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;xRot(F)Lnet/minecraft/world/phys/Vec3;"
            ),
            require = 0,
            remap = false
    )
    private Vec3 fangsu$rollRidingPlayerOffset(Vec3 vec, float pitchAngle) {
        return RailTrainRollHelper.applyRidingPlayerRoll(vec, pitchAngle);
    }
}
