package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.mappings.UtilitiesClient;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 外轨超高（P5-6）：把「车体随轨滚转」插进 MTR 自己的列车模型渲染链。
 * <p>
 * <b>为什么目标是 {@code JonModelTrainRenderer} 而不是 {@link TrainRendererBase}</b>：
 * {@code renderCar(IDDDFFZZ)V} 是 {@code TrainRendererBase} 的 <b>public abstract</b> API
 * （第三方附加模组实现），<b>签名一个字都不能改</b>；而车体的局部坐标系是每个具体渲染器
 * 自己建立的，基类里没有任何公共代码可以挂。MTR3 的列车默认（也是本模组关心的）渲染器就是
 * {@code JonModelTrainRenderer}，它的链是
 * <pre>
 *   translate(x, y, z) → rotateY(π + yaw) → rotateX(π + hasPitch ? pitch : 0) → 模型 + 转向架
 * </pre>
 * 与 MTR4 的 {@code translate → rotateYRadians(yaw+π) → rotateXRadians(pitch+π)} <b>逐项相同</b>，
 * 因此「在 {@code rotateX} 之后 {@code mulPose} 一个绕局部 +Z 的旋转」就是绕车身纵轴滚转，
 * 与 MTR4 的 {@code rotateZDegrees} 语义完全一致。
 * <p>
 * 因此本 mixin 只覆盖 MTR 自己的模型渲染器；第三方附加渲染器（Metropolis / Joban 等）的车体
 * <b>不会</b>倾斜 —— 那是「不改公共 API」的必然代价，已记入 P5-6 报告。轨道带（P5-3）与
 * 中心线（P5-1）不受影响。
 * <p>
 * 两个钩子都是 {@code require = 0} + 完整描述符，并由 {@link RailTrainRollHelper} 一次性诊断兜底。
 */
@Mixin(value = JonModelTrainRenderer.class, remap = false)
public abstract class JonModelTrainRendererRollMixin {

    /** {@code renderCar(int, double, double, double, float, float, boolean, boolean)} 的完整描述符。 */
    private static final String RENDER_CAR = "renderCar(IDDDFFZZ)V";

    /**
     * 取本车厢的滚转角（按车厢序号查 {@link RailTrainRollHelper} 的状态表），放进本次调用的上下文。
     * <p>
     * <b>这里刻意不做轨道匹配</b>：{@code renderCar} 收到的 x/y/z 在乘车时是相机相对量
     * （{@code TrainClient.simulateCar} 里减掉了 {@code viewOffset}），拿它匹配轨道必然失败。
     * 世界坐标只在 {@code simulateCar} 里存在，匹配在那里完成。
     * <p>
     * <b>处理器是实例方法</b>（目标方法是实例方法），{@code this} 就是渲染器实例
     * （= 状态表的键），因此这里把它交给 helper。
     */
    @Inject(method = RENDER_CAR, at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$beginBodyRoll(
            int carIndex, double x, double y, double z, float yaw, float pitch,
            boolean doorLeftOpen, boolean doorRightOpen,
            CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.beginBodyRender((TrainRendererBase) (Object) this, carIndex, yaw);
    }

    /**
     * 在模型链的 {@code rotateX(π + pitch)} 之后追加绕局部 +Z 的滚转。
     * <p>
     * <b>{@code ordinal = 0} 是必须的</b>：{@code renderCar} 里 {@code UtilitiesClient.rotateX(PoseStack,F)V}
     * 恰好有两处（{@code javap -p -c} 核实：偏移 210 = 模型链、偏移 833 = 缆车抓手分支），
     * 不加 {@code ordinal} 会同时命中后者（那会让缆车抓手单独再滚一次）。
     * <p>
     * <b>无滚转时不做任何矩阵运算</b>：{@link RailTrainRollHelper#applyBodyRoll(PoseStack)} 在角度为 0
     * （或状态缺失）时直接返回，因此普通轨道上与 MTR 原生逐位一致。
     */
    @Redirect(
            method = RENDER_CAR,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/mappings/UtilitiesClient;rotateX(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
                    ordinal = 0
            ),
            require = 0,
            remap = false
    )
    private void fangsu$rollBody(PoseStack matrices, float angle) {
        UtilitiesClient.rotateX(matrices, angle);
        RailTrainRollHelper.applyBodyRoll(matrices);
    }
}
