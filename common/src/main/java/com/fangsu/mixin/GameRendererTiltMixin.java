package com.fangsu.mixin;

import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC_VERSION >= 11903
import org.joml.Quaternionf;
//#else
//$$import com.mojang.math.Quaternion;
//#endif

/**
 * MTR3 外轨超高（P5-7）：乘车经过倾斜曲线时，让<b>整个世界画面（含地平线）</b>绕
 * <b>车厢的世界纵轴</b>滚转。
 * <p>
 * <b>问题</b>：P5-1/P5-3/P5-6 之后轨面、车体、风挡都随车体滚转，本类的同伴
 * {@link VehicleRidingClientRollMixin} 让玩家也站到倾斜的地板上；但玩家的<b>视线仍然水平</b>，
 * 于是「明显倾斜的车厢里挂着一条水平的地平线」——这是该特性最后、也是最显眼的一处不协调。
 * <p>
 * <b>做法</b>：在 {@code GameRenderer.renderLevel}
 * （yarn：{@code renderWorld}）里、{@code LevelRenderer.prepareCullFrustum}
 * （yarn：{@code WorldRenderer.setupFrustum}）调用<b>之前</b>，给世界 PoseStack 右乘一个
 * 「绕车厢世界前向轴 {@code f} 旋转 {@code +φ}」的旋转，其中
 * <pre>
 *   φ = 玩家所在车厢的滚转角 × cameraTiltStrength()   （RailTrainRollHelper.getCameraRollDegrees）
 *   f = 该车厢的世界前向轴（= 车身局部 +Z 的负方向）    （RailTrainRollHelper.getCameraTiltAxis）
 * </pre>
 * <b>注意右乘的含义</b>：{@code mulPose} 是右乘，{@code M ← M·R_a(θ)}，而
 * {@code M·R_a(θ) = R_{M·a}(θ)·M}，所以 {@code a} 必须是「{@code M} 所消费的那个空间」
 * （注入点处就是<b>相机坐标系</b>）里的轴。因此本类传的是
 * {@link RailTrainRollHelper#getCameraSpaceTiltAxis()}，即 {@code R_cam⁻¹·f}；
 * 只有「相机恰好朝向车头」时 {@code R_cam⁻¹·f} 才等于 {@code f}，所以直接传世界轴是错的
 * （实测在偏离车头 90°/180° 时偏差 0.156 / 0.313）。
 * <p>
 * <b>为什么角度取 {@code +φ}（不是 MTR4 注释里的 {@code −roll}）</b>：物理模型是「乘客的脑袋
 * 焊在车厢上」，即<b>相机跟着车体用同一个世界旋转</b>。车体的世界旋转实测为
 * {@code M_rolled = M_noroll·Rz_JOML(roll) = R_f(roll)·M_noroll}，所以相机要施加
 * {@code R_f(+roll)}。MTR4 的 {@code −roll} 写的是「绕它当时用的那根轴」的符号；本类先把轴
 * 换算到相机坐标系、再取 {@code +φ}，两者是同一件事的不同表达（探针第 (4) 节把两条路径
 * 都算了：换算后的 {@code +φ} 与物理理想差 1.4e−08，不换算的写法差到 0.313）。
 * <p>
 * <b>为什么是车厢纵轴而不是视线轴</b>：拿「相机后向」当轴得到的是屏幕空间滚转 —— 地平线的
 * 屏幕滚转与玩家朝哪看无关，正侧向看时车内完全歪掉（探针里该候选的车内「上」偏差达 90°）。
 * <p>
 * <b>本类需要本帧相机朝向</b>：由 {@code RenderTrainsMixin} 在
 * {@code TrainRendererBase.setupStaticInfo} 的 TAIL 调用
 * {@link RailTrainRollHelper#captureCameraOrientation(net.minecraft.client.Camera)} 记下；
 * 取不到时本类退回「不旋转」，绝不猜测朝向。
 * <p>
 * <b>数值核对</b>（{@code .tmp_p5_7_probe/P57Probe.java} 第 (4) 节，覆盖
 * 2 个车头朝向 × 5 个坡度 × 24 个相机偏航 × 8 个视线方向 = 1920 个样本）：
 * 与「物理理想」{@code R_f(φ)·M_cam} 的世界矩阵逐元素最大差 {@code 1.4e-08}；
 * 取反号的最大差 {@code 3.1e-01}（≈ 2·|roll|）；用视线轴的最大差同样是屏幕空间滚转量级。
 * <p>
 * <b>严格 no-op</b>：{@link RailTrainRollHelper#getCameraRollDegrees()} 在「未乘车 /
 * 该车厢本帧没有带滚转的轨道 / 滚转为 0 / 配置关闭 / 强度为 0」时返回 {@code 0.0}，
 * 此时本方法<b>在任何矩阵运算之前就 {@code return}</b>，世界 PoseStack 与原生逐位一致
 * （不读矩阵、不建四元数、没有 {@code mulPose}）。{@link RailTrainRollHelper#getCameraTiltAxis()}
 * 只在前者返回非零之后才被调用。
 * <p>
 * <b>版本处理</b>：目标方法在 1.18.2 ~ 1.20.1 全程稳定（{@code javap} 已对两个已缓存 jar 核实），
 * 唯一的差异是 {@code LevelRenderer.prepareCullFrustum} 第三个形参的 {@code Matrix4f} 包名
 * （MC &lt; 11903 是 {@code com.mojang.math.Matrix4f}，≥ 11903 是 {@code org.joml.Matrix4f}）
 * 与 {@code PoseStack.mulPose} 的四元数类型（MC &lt; 11903 是 {@code com.mojang.math.Quaternion}，
 * ≥ 11903 是 {@code org.joml.Quaternionf}）。本类沿用项目既有的
 * {@code //#if MC_VERSION >= 11903} 条件编译（与 {@code LevelRendererMixin} 同一处阈值），
 * 两个分支施加的是同一个旋转。
 * <p>
 * <b>客户端专属</b>：{@code GameRenderer} / {@code PoseStack} 都是客户端类，本 mixin 只注册在
 * {@code fangsu.mixins.json} 的 {@code client} 数组里，服务端不会加载；而
 * {@link RailTrainRollHelper} 里 P5-7 新增的入口只返回 {@code double} / {@code double[]}，
 * 不引用任何客户端类型。
 */
@Mixin(value = GameRenderer.class)
public class GameRendererTiltMixin {

    /**
     * {@code GameRenderer.renderLevel(float,long,PoseStack)} 的完整描述符
     * （{@code javap -p} 对 1.18.2 与 1.20.1 的 merged jar 逐字核实，两个版本完全相同）。
     */
    private static final String RENDER_LEVEL = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V";

    /**
     * {@code LevelRenderer.prepareCullFrustum} 的完整描述符。
     * <p>
     * <b>必须按版本条件编译</b>：1.18.2 / 1.19.2 的第三个形参是
     * {@code com.mojang.math.Matrix4f}，1.19.3 起换成 {@code org.joml.Matrix4f}
     * （{@code javap -p} 对两个已缓存 merged jar 逐字核实）。这条阈值（11903）与项目既有的
     * {@code LevelRendererMixin.java:13-17} 完全一致。
     * <p>
     * 描述符写成常量而不是直接写进 {@code @At}：注解的值必须是编译期常量，而这里需要
     * 条件编译切换 —— 常量声明可以整行 {@code //$$} 掉，注解属性不行。
     */
    //#if MC_VERSION >= 11903
    private static final String PREPARE_CULL_FRUSTUM =
            "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V";
    //#else
    //$$ private static final String PREPARE_CULL_FRUSTUM =
    //$$         "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum("
    //$$                 + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/math/Matrix4f;)V";
    //#endif

    /**
     * 在 {@code prepareCullFrustum} 之前施加镜头滚转。
     * <p>
     * <b>注入点</b>：{@code GameRenderer.renderLevel} 里
     * {@code LevelRenderer.prepareCullFrustum} 恰好<b>一处</b>调用
     * （{@code javap -p -c} 在 1.20.1 上核实：偏移 560；1.18.2 上同名同参数表，仅 {@code Matrix4f}
     * 包名不同）。此时栈里已经只有相机旋转，而任何世界几何都还没被变换 —— 这正是
     * 「整个画面绕车厢纵轴滚转」该插入的位置。
     * <p>
     * <b>{@code method} 与 {@code at.target} 都显式写出</b>（Mixin 0.8.7 对缺 {@code method} 或
     * 缺 {@code target} 的注入注解直接抛 {@code InvalidInjectionException}）；
     * {@code method} 用完整描述符而不是裸名字：{@code GameRenderer} 目前只有一个
     * {@code renderLevel}，但本仓库的纪律是「同名方法一律写全描述符」，一旦将来多一个重载，
     * 裸名字会静默指错。
     * <p>
     * <b>静态性</b>：{@code renderLevel} 是<b>实例</b>方法，因此处理器也是实例方法
     * （{@code require = 0} 不能抑制静态性不匹配导致的致命 {@code InvalidInjectionException}）。
     * <p>
     * <b>{@code remap = false} 是必需的，理由已实测</b>（不是随手加的）：{@code GameRenderer.renderLevel}
     * 是 Fabric API 在自己的 {@code GameRendererMixin} 里<b>注入</b>进 {@code GameRenderer} 的方法，
     * 它因此<b>不在 Minecraft 自己的混淆映射表里</b>。Mixin 注解处理器在
     * {@code remap = true}（默认）时会尝试把这个方法名映射到 intermediary，找不到就抛
     * <pre>
     *   error: Unable to locate obfuscation mapping for @Inject target renderLevel
     * </pre>
     * 这不是「注入点写错」——同一行还会提示 {@code @At} 的
     * {@code LevelRenderer.prepareCullFrustum(...)} 也无法映射，而那个方法在
     * {@code javap} 里是确凿存在的。加 {@code remap = false} 后 AP 全绿、探针的
     * {@code WiringProbe} 仍然在真实类字节上把这条注入点解析到唯一的一处调用。
     * <b>方法名/描述符一个字都没变</b>，因此运行期（named + Fabric 的 intermediary 运行时重映射）
     * 的目标与写 {@code remap = true} 时完全一致 —— 只是不再让 AP 去做那次注定失败的映射查询。
     * <p>
     * {@code require = 0}：将来 MC / Fabric 改动这个调用点时退化为「相机无滚转」
     * （= 修复前的现状）而不是崩游戏，并由 {@link RailTrainRollHelper} 的一次性告警暴露。
     */
    @Inject(
            method = RENDER_LEVEL,
            at = @At(
                    value = "INVOKE",
                    target = PREPARE_CULL_FRUSTUM,
                    shift = At.Shift.BEFORE
            ),
            require = 0,
            remap = false
    )
    private void fangsu$tiltCameraWhenRiding(float tickDelta, long limitTime, PoseStack poseStack, CallbackInfo callbackInfo) {
        // 钩子存活诊断：只要进入本方法就说明注入点解析成功（不论这一帧是否需要滚转）。
        RailTrainRollHelper.probeCameraTiltFrame();
        final double angleDegrees = RailTrainRollHelper.getCameraRollDegrees();
        if (angleDegrees == 0.0D || poseStack == null) {
            // 未乘车 / 无滚转 / 配置关闭 / 强度为 0：一个矩阵运算都不做，与原生逐位一致
            return;
        }
        final double[] axis = RailTrainRollHelper.getCameraSpaceTiltAxis();
        if (axis == null || axis.length != 3) {
            // 没有可用的车厢纵轴 / 没有本帧相机朝向 → 退回原生（不做任何旋转）
            return;
        }
        final double axisX = axis[0];
        final double axisY = axis[1];
        final double axisZ = axis[2];
        if (!Double.isFinite(axisX) || !Double.isFinite(axisY) || !Double.isFinite(axisZ)) {
            // 宁可退回原生画面，也绝不用零长度 / 非有限轴去建四元数（那会污染整个世界矩阵）
            return;
        }
        //#if MC_VERSION >= 11903
        fangsu$applyCarAxisRoll(poseStack, angleDegrees, axisX, axisY, axisZ);
        //#else
        //$$ // 1.18.2 / 1.19.2：PoseStack.mulPose 的形参是 com.mojang.math.Quaternion，
        //$$ // 而 org.joml.Quaternionf 不在那个版本的 classpath 上。手工用 (x,y,z,w) 构造同一个
        //$$ // 轴角四元数 —— 与 JOML 的 rotationAxis 同构（都是右手轴角公式）。
        //$$ poseStack.mulPose(fangsu$axisAngleQuaternion(
        //$$         (float) Math.toRadians(angleDegrees), axisX, axisY, axisZ));
        //#endif
    }

    //#if MC_VERSION >= 11903
    /**
     * 把「世界绕车厢前向轴 {@code f} 旋转 {@code +angleDegrees}」施加到相机栈上。
     * <p>
     * <b>轴</b>：{@link RailTrainRollHelper#getCameraSpaceTiltAxis()}，即
     * {@code R_cam⁻¹·f}（已经换算到相机坐标系；换算与理由见那个方法）。
     * <p>
     * <b>为什么是 {@code +angleDegrees} 而不是 {@code −angleDegrees}</b>：物理模型是「乘客的脑袋
     * 焊在车厢上」，也就是<b>相机跟着车体用同一个世界旋转</b>。车体的世界旋转实测为
     * {@code M_rolled = M_noroll·Rz_JOML(roll) = R_f(roll)·M_noroll}（{@code .tmp_p5_7_probe/P57Probe.java}
     * 第 (4) 节，残差 1.2e−08），所以相机必须施加 {@code R_f(+roll)} 才与之相同。
     * {@code mulPose} 右乘、且轴的换算已经用掉了 {@code M·R_a(θ) = R_{M·a}(θ)·M} 这一恒等式，
     * 因此这里的角度<b>不再取反</b>。
     * <p>
     * 三条独立证据（同一节探针，覆盖 2 个车头朝向 × 5 个坡度 × 24 个相机偏航 × 8 个视线方向
     * = 1920 个样本）：
     * <ol>
     *   <li>本式与「物理理想」{@code R_f(roll)·M_cam} 逐元素最大差 1.4e−08（机器精度）；</li>
     *   <li>取反号时最大差 0.313（约 {@code 2·|roll|}），被明确否掉；</li>
     *   <li>车内「上」方向在屏幕上恒为 ±90°（车内看起来水平），地平线屏幕角
     *       {@code = −roll·cos(look)}。</li>
     * </ol>
     */
    private static void fangsu$applyCarAxisRoll(
            PoseStack poseStack, double angleDegrees, double axisX, double axisY, double axisZ
    ) {
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(angleDegrees),
                (float) axisX,
                (float) axisY,
                (float) axisZ
        ));
        RailTrainRollHelper.markCameraTiltApplied();
    }
    //#endif

    //#if MC_VERSION < 11903
    //$$ /**
    //$$  * 右手轴角四元数 {@code (x,y,z,w)}，与 {@code org.joml.Quaternionf.rotationAxis} 同构。
    //$$  * <p>
    //$$  * <b>为什么不用 {@code org.joml.Quaternionf}</b>：MC 1.18.2 / 1.19.2 的
    //$$  * {@code PoseStack.mulPose} 形参是 {@code com.mojang.math.Quaternion}，而那个版本的
    //$$  * classpath 上并没有 JOML（{@code javap} 核实：{@code org.joml.Quaternionf} 在 1.18.2
    //$$  * 的 merged jar 里不存在），因此这里手工构造同一个四元数。
    //$$  */
    //$$ private static Quaternion fangsu$axisAngleQuaternion(
    //$$         float radians, double axisX, double axisY, double axisZ
    //$$ ) {
    //$$     final float half = radians * 0.5F;
    //$$     final float sin = (float) Math.sin(half);
    //$$     return new Quaternion(
    //$$             (float) axisX * sin,
    //$$             (float) axisY * sin,
    //$$             (float) axisZ * sin,
    //$$             (float) Math.cos(half)
    //$$     );
    //$$ }
    //#endif
}
