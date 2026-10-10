package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.fangsu.train.FunctionalTrainRenderer;
import mtr.render.TrainRendererBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 外轨超高（P5-6）：让本模组自己的<b>功能车渲染器</b>
 * {@link FunctionalTrainRenderer} 也能查到本车厢的滚转角。
 * <p>
 * <b>它是什么形状（读了源码，不是猜的）</b>：{@code FunctionalTrainRenderer} 是一个
 * <b>装饰器</b> —— {@code com.fangsu.train.FunctionalCustomTrains:66} 用
 * {@code new FunctionalTrainRenderer(lcdInfo, prevTrainProp.renderer)} 把它包在原来那个渲染器外面，
 * 于是 {@code TrainClient.trainRenderer} 就是<b>这个装饰器实例</b>。它的三个方法是：
 * <pre>
 *   renderCar        (FunctionalTrainRenderer.java:144)
 *       → if (baseRenderer != null) baseRenderer.renderCar(carIndex, x, y, z, yaw, pitch, …)   // 车体交给被包住的那个
 *       → 然后自己 push/translate/rotate，画 LCD 覆盖层
 *   renderConnection (FunctionalTrainRenderer.java:209) → baseRenderer.renderConnection(…)       // 纯转发
 *   renderBarrier    (FunctionalTrainRenderer.java:215) → baseRenderer.renderBarrier(…)          // 纯转发
 * </pre>
 * 即<b>形态 (a)：委托</b>。所以车体几何仍由被包住的 {@code JonModelTrainRenderer} 画，而
 * {@code JonModelTrainRendererRollMixin} 的 {@code rotateX} 重定向<b>照样会触发</b>。
 * <p>
 * <b>那为什么功能车原来不会倾斜</b>：不是几何问题，是<b>状态表的键对不上</b>。
 * {@link RailTrainRollHelper#captureCar} 是在 {@code TrainClient.simulateCar} 里用
 * {@code train.trainRenderer} 登记状态的 —— 那是<b>装饰器</b>实例；而
 * {@code JonModelTrainRendererRollMixin} 的 {@code renderCar} HEAD 处理器拿到的是
 * <b>被包住的那个</b> {@code JonModelTrainRenderer} 实例。两个实例不同 ⟹
 * {@code stateOf(renderer, carIndex)} 返回 {@code null} ⟹ yaw 一致性校验直接失败 ⟹ 滚转角 0。
 * <p>
 * <b>修法</b>：在做转发之前，用<b>装饰器自己的</b>实例（也就是登记时用的那个键）调用一次
 * {@link RailTrainRollHelper#beginBodyRender}，把滚转角放进本次调用的上下文。
 * 于是被包住的那个渲染器在 {@code rotateX} 上消费到的是<b>同一个角度</b>，与轨面、与普通车体
 * 完全不可能不一致（同一个 {@code RailTrainRollHelper}、同一格状态）。
 * 装饰器自己的 LCD 覆盖层也在同一个局部系里，因此在它自己的链上再补一次
 * {@link RailTrainRollHelper#applyBodyRoll}（见 {@code FunctionalTrainRenderer} 的改动）。
 * <p>
 * <b>被包住的不是 JonModelTrainRenderer 时会怎样</b>：例如自定义车包提供自己的
 * {@code TrainRendererBase} 实现 —— 那时 {@code baseRenderer.renderCar} 走的是别人的链，
 * 我们<b>不可能</b>在不改 {@code TrainRendererBase} 公共抽象签名的前提下插进它的局部系
 * （P5-6 的硬约束）。此时只有 LCD 覆盖层会倾斜，车体不会。这是既有的、已记录的边界，
 * 不因为本 mixin 而改变；{@code require = 0} 保证它在任何情况下都不会崩游戏。
 * <p>
 * <b>为什么三个方法都钉住描述符</b>：{@code renderCar(IDDDFFZZ)V} 与
 * {@code TrainRendererBase} 的其它重载形状不同，但本仓库的纪律是「同名方法一律写完整描述符」
 * —— 目标类一旦多一个同名重载，裸名字就会静默指错（P5-3 的死钩子教训）。
 * <p>
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里：它依赖 {@code TrainClient}
 * 与 {@code LightTexture} 这些客户端类，服务端即使加载了 {@code FunctionalTrainRenderer} 的类元数据
 * 也不会走到这里。
 */
@Mixin(value = FunctionalTrainRenderer.class, remap = false)
public abstract class FunctionalTrainRendererRollMixin {

    /** {@code TrainRendererBase.renderCar(int, double, double, double, float, float, boolean, boolean)} 的描述符。 */
    private static final String RENDER_CAR = "renderCar(IDDDFFZZ)V";

    /**
     * 在转发给被包住的渲染器<b>之前</b>登记本车厢的滚转角。
     * <p>
     * 处理器是<b>实例</b>方法：目标方法是实例方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 强制静态性一致；{@code this} 就是装饰器实例
     * （= {@link RailTrainRollHelper} 状态表的键）。
     */
    @Inject(method = RENDER_CAR, at = @At("HEAD"), require = 0, remap = false)
    private void fangsu$beginBodyRollForDecorator(
            int carIndex, double x, double y, double z, float yaw, float pitch,
            boolean doorLeftOpen, boolean doorRightOpen,
            CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.beginBodyRender((TrainRendererBase) (Object) this, carIndex, yaw);
    }
}
