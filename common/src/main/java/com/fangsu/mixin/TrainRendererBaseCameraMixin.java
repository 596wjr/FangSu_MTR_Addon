package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.entity.EntitySeat;
import mtr.render.TrainRendererBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MTR3 外轨超高（P5-7）：记下<b>本帧相机的朝向</b>，供镜头滚转的轴换算使用。
 * <p>
 * <b>为什么相机路径需要它</b>：{@code GameRendererTiltMixin} 在
 * {@code GameRenderer.renderLevel} 的 {@code prepareCullFrustum} 之前往世界 PoseStack 上
 * 右乘一个旋转。{@code mulPose} 是右乘（{@code M ← M·R_a(θ)}），而
 * {@code M·R_a(θ) = R_{M·a}(θ)·M}，所以传入四元数的轴必须是「{@code M} 所消费的那个空间」
 * —— 在那个注入点上就是<b>相机坐标系</b> —— 里的轴。要在那里施加「绕车厢世界前向轴 {@code f}
 * 的世界旋转」，必须传 {@code R_cam⁻¹·f}。缺了这一步，只有「相机恰好朝向车头」时结果才对
 * （探针实测：偏离车头 90°/180° 时偏差 0.156 / 0.313，量级 {@code 2·|roll|}）。
 * <p>
 * <b>为什么钩在 {@code setupStaticInfo} 而不是 {@code renderCar}</b>：
 * <ul>
 *   <li>{@code setupStaticInfo} 是 {@code RenderTrains.render} 每帧调用的<b>第一件事</b>
 *       （源码 {@code RenderTrains.java:507}：{@code TrainRendererBase.setupStaticInfo(matrices,
 *       vertexConsumers, entity, newLastFrameDuration);}），而它自己第一件事就是
 *       {@code camera = client.gameRenderer.getMainCamera();}。在它的 TAIL 读相机，
 *       拿到的就是<b>本帧</b>的相机姿态，且早于任何车厢绘制；</li>
 *   <li>{@code renderCar} 只在<b>视锥内</b>的车厢上调用，把它当数据源会让「玩家面朝车外」时
 *       相机朝向不更新 —— 恰恰是轴换算最需要正确的场合。</li>
 * </ul>
 * <p>
 * <b>为什么取 {@code Minecraft.getInstance().gameRenderer.getMainCamera()} 而不是直接读
 * {@code TrainRendererBase.camera}</b>：后者虽然是同一个实例，但它是 {@code protected static}；
 * 从 mixin 里 {@code @Shadow} 一个 {@code protected static} 字段是可行的，但会把本 mixin
 * 绑死在那个字段名上。{@code setupStaticInfo} 里赋的就是这个 getter 的返回值，取同一个来源
 * 既等价又更稳。
 * <p>
 * <b>目标方法签名</b>：{@code javap -p} 核实为
 * {@code public static void setupStaticInfo(PoseStack, MultiBufferSource, EntitySeat, float)}，
 * 是<b>静态</b>方法，因此处理器也必须是静态的（{@code require = 0} 不能抑制
 * 静态性不匹配导致的致命 {@code InvalidInjectionException}）。
 * <p>
 * <b>为什么单独一个 mixin 而不是塞进 {@code RenderTrainsMixin}</b>：{@code @Mixin} 的
 * {@code method = ...} 选择器是在<b>该 mixin 声明的目标类</b>里解析的。把
 * {@code setupStaticInfo} 写进 {@code @Mixin(RenderTrains.class)} 会被注解处理器报成
 * {@code Cannot find target method ... in mtr.render.RenderTrains}（P5-7 的实现过程中实际踩到过）。
 * <p>
 * {@code require = 0}：将来 MTR 改动这个签名时退化为「没有相机朝向 → 镜头不旋转」
 * （= 修复前的现状）而不是崩游戏，并由 {@link RailTrainRollHelper} 的一次性告警暴露。
 * 本类只注册在 {@code fangsu.mixins.json} 的 {@code client} 数组里。
 */
@Mixin(value = TrainRendererBase.class, remap = false)
public class TrainRendererBaseCameraMixin {

    /**
     * {@code TrainRendererBase.setupStaticInfo} 的完整描述符（{@code javap -p} 逐字核实）。
     */
    private static final String SETUP_STATIC_INFO =
            "setupStaticInfo(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;Lmtr/entity/EntitySeat;F)V";

    /**
     * 在 {@code setupStaticInfo} 结束时记下本帧相机朝向。
     * <p>
     * 处理器是<b>静态</b>方法：目标方法是静态方法，Mixin 的
     * {@code Injector.checkTargetModifiers} 要求静态性一致。
     */
    @Inject(method = SETUP_STATIC_INFO, at = @At("TAIL"), require = 0, remap = false)
    private static void fangsu$captureCameraOrientation(
            PoseStack matrices, MultiBufferSource vertexConsumers, EntitySeat entity, float tickDelta,
            CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.captureCameraOrientation(
                Minecraft.getInstance().gameRenderer.getMainCamera());
    }
}
