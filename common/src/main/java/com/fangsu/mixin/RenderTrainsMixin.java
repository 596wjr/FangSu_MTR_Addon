package com.fangsu.mixin;

import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.data.LiftExtraSupplier;
import com.fangsu.mtr.ModernTexturedLift;
import com.fangsu.mtr.rail.RailTiltRenderHelper;
import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.fangsu.render.lift.CustomLiftModel;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.client.ClientData;
import mtr.data.Lift;
import mtr.data.LiftClient;
import mtr.data.Rail;
import mtr.entity.EntitySeat;
import mtr.mappings.UtilitiesClient;
import mtr.render.RenderTrains;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.*;
import java.util.function.BiConsumer;

import static mtr.data.IGui.SMALL_OFFSET;

@Mixin(value = RenderTrains.class, remap = false)
public class RenderTrainsMixin {
    @Shadow
    public static void renderLiftDisplay(PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos, String floorNumber, Lift.LiftDirection liftDirection, float maxWidth, float height) {
    }

    @Inject(
            method = "lambda$render$6",
            cancellable = true,
            at = @At("HEAD")
    )
    private static void renderLift(Level world, PoseStack matrices, MultiBufferSource vertexConsumers, float newLastFrameDuration, LiftClient lift, CallbackInfo ci) {
        lift.tickClient(world, (x, y, z, frontDoorValue, backDoorValue) -> {
            final BlockPos posAverage = TrainRendererBase.applyAverageTransform(lift.getViewOffset(), x, y, z);
            if (posAverage == null) {
                return;
            }

            matrices.translate(x, y, z);
            UtilitiesClient.rotateXDegrees(matrices, 180);
            UtilitiesClient.rotateYDegrees(matrices, 180 + lift.facing.toYRot());
            final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));

            // 自定义拼装电梯：真正切换几何模型；否则回退到默认几何（仅换贴图）
            final CustomMtrLifts.AssembledLiftSelectInfo assembled =
                    CustomMtrLifts.getInstance().getAssembledLiftSelectInfo(((LiftExtraSupplier) lift).fangsu$getModelKey());
            if (assembled != null) {
                final Matrix4f pose = new Matrix4f(matrices.last().pose());
                final BufferSourceProxy proxy = new BufferSourceProxy(vertexConsumers);
                final com.fangsu.render.lift.LiftModelAssembler.LiftConditionContext cond =
                        new com.fangsu.render.lift.LiftModelAssembler.LiftConditionContext(
                                lift.getLiftDirection() == Lift.LiftDirection.UP,
                                lift.getLiftDirection() == Lift.LiftDirection.DOWN,
                                lift.getLiftDirection() == Lift.LiftDirection.NONE);
                final com.fangsu.render.lift.CustomLiftModel customLift =
                        CustomLiftModel.get(assembled.getProperties(), assembled.getModel(), assembled.getTexture());
                customLift.renderWithSize(proxy, pose, light, frontDoorValue, lift.liftWidth, lift.liftDepth, lift.liftHeight, cond);
                proxy.commit();

                // 自定义 DISPLAY / LIGHT 部位：文字楼层号 / 上下行箭头
                if (!customLift.getDisplays().isEmpty()) {
                    final String floorText = ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0];
                    final MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
                    for (com.fangsu.render.lift.LiftModelAssembler.DisplayInfo disp : customLift.getDisplays()) {
                        matrices.pushPose();
                        matrices.translate(disp.position.x / 16F, disp.position.y / 16F, disp.position.z / 16F);
                        if (disp.light) {
                            final boolean up = "GOING_UP".equalsIgnoreCase(disp.condition);
                            mtr.client.IDrawing.drawTexture(matrices,
                                    vertexConsumers.getBuffer(mtr.render.MoreRenderLayers.getLight(
                                            new net.minecraft.resources.ResourceLocation("mtr:textures/block/sign/lift_arrow.png"), true)),
                                    -0.1875F / 6, 0, 0.1875F / 3, 0.1875F / 3, 0,
                                    up ? 0 : 1, 1, up ? 1 : 0, net.minecraft.core.Direction.UP,
                                    0xFF0000, mtr.data.IGui.MAX_LIGHT_GLOWING);
                        } else if ("FLOOR".equalsIgnoreCase(disp.displayType)) {
                            mtr.client.IDrawing.drawStringWithFont(matrices, net.minecraft.client.Minecraft.getInstance().font, immediate,
                                    floorText, mtr.data.IGui.HorizontalAlignment.CENTER, mtr.data.IGui.VerticalAlignment.BOTTOM,
                                    0, 0.3125F, 0.1875F, -1, 18F / 0.1875F, 0xFF0000, false, mtr.data.IGui.MAX_LIGHT_GLOWING, null);
                        }
                        matrices.popPose();
                    }
                    immediate.endBatch();
                }
            } else {
                new ModernTexturedLift(lift, lift.liftHeight, lift.liftWidth, lift.liftDepth, lift.isDoubleSided).render(matrices, vertexConsumers, lift, light, frontDoorValue, backDoorValue);
            }

            for (int i = 0; i < (lift.isDoubleSided ? 2 : 1); i++) {
                UtilitiesClient.rotateYDegrees(matrices, 180);
                matrices.pushPose();
                matrices.translate(0.875F, -1.5, lift.liftDepth / 2F - 0.25 - SMALL_OFFSET);
//                renderLiftDisplay(matrices, vertexConsumers, posAverage, ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0], lift.getLiftDirection(), 0.1875F, 0.3125F);
                fangsu$renderLiftDisplayWithColor(matrices, vertexConsumers, posAverage,
                        ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0],
                        lift.getLiftDirection(), 0.1875F, 0.3125F, lift);
                matrices.popPose();
            }

            matrices.popPose();
        }, newLastFrameDuration);
        ci.cancel();
    }

    /**
     * 通过反射调用 YMTR 的 renderLiftDisplay，优先适配带 DisplayColor 参数的新版本，
     * 若反射失败则自动回退到旧版无颜色参数的方法。
     */
    @Unique
    private static void fangsu$renderLiftDisplayWithColor(PoseStack matrices, MultiBufferSource vertexConsumers,
                                                          BlockPos pos, String floorNumber,
                                                          Lift.LiftDirection liftDirection,
                                                          float maxWidth, float height, LiftClient lift) {
        try {
            // 1. 尝试反射获取 Lift 实例上的 displayColor 字段
            Field displayColorField = lift.getClass().getField("displayColor");
            Object displayColor = displayColorField.get(lift);

            // 2. 尝试获取带 DisplayColor 参数的新方法
            // 注意：DisplayColor 是 Lift 的内部枚举，直接用 displayColor 的类来定位参数类型
            Method newMethod = TrainRendererBase.class.getMethod("renderLiftDisplay",
                    PoseStack.class, MultiBufferSource.class, BlockPos.class, String.class,
                    Lift.LiftDirection.class, displayColor.getClass(), float.class, float.class);

            // 3. 调用新方法
            newMethod.invoke(null, matrices, vertexConsumers, pos, floorNumber,
                    liftDirection, displayColor, maxWidth, height);

        } catch (NoSuchFieldException | NoSuchMethodException e) {
            // 如果不存在 displayColor 字段或新方法，说明是旧版 MTR 或旧版 YMTR，回退到旧方法
            renderLiftDisplay(matrices, vertexConsumers, pos, floorNumber, liftDirection, maxWidth, height);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // 反射调用出错（例如权限问题或方法内部抛出异常），同样回退并打印日志
            e.printStackTrace();
            renderLiftDisplay(matrices, vertexConsumers, pos, floorNumber, liftDirection, maxWidth, height);
        }
    }

    // ==================== P5-3：外轨超高（轨道横断面滚转，路径 A） ====================
    //
    // 全部钩子都写出显式 method，全部 require = 0（MTR3 换版本时退化为「不倾斜」而不是崩游戏），
    // 全部由 RailTiltRenderHelper 的一次性诊断兜底（见那边的 verifyTiltHooksAlive /
    // verifyScheduleHookAlive）。
    //
    //  ① @ModifyVariable(index = 1) 捕获「当前正在渲染的轨道」（Rail 在槽位 1）
    //  ② @Inject(RETURN)            结束同步窗口
    //  ⑤ scheduleRender 的 @Redirect ×2    只包装轨面 / 单行道箭头那两个排队消费者
    //                                       （注入点是 lambda$renderRailStandard$17 —— 两个
    //                                        scheduleRender 调用写在那里，$15/$16 是被排队的消费者）
    //  ⑥ drawTexture 的 @Redirect ×4       旋转四角点（每个 lambda 两次调用、正反面各一次）
    //
    // ★ 相位钩子（③ Rail.render 的 @Inject、④ Rail.renderSegment 的 @ModifyVariable）
    //   以及 tStart1/tEnd1/tStart2/tEnd2 的四个 @Shadow <b>已经搬到 RailGeometryMixin</b>：
    //   它们的目标类是 {@code mtr.data.Rail}，写在这个 {@code @Mixin(RenderTrains.class)}
    //   的类里时，{@code method = "render"} 被注解处理器解析到 RenderTrains 自己的 render
    //   重载上（于是不报错），{@code method = "renderSegment"} 因为处理器完全不校验
    //   {@code @ModifyVariable.method} 而毫无提示，四个 @Shadow 字段则报
    //   「Cannot find target for @Shadow field in mtr.render.RenderTrains」。
    //   判据与描述符见 RailTiltRenderHelper 里的相位钩子目标常量。
    //
    // ★ NTE（mtrsteamloco）存在时这些钩子仍是活的，但 MTR 的轨面压根不会被调用：
    //   NTE 在 renderRailStandard 上 @Inject 并 CallbackInfo.cancel()，改用自己的
    //   RailRenderDispatcher 画轨道。因此**路径 A 装了 NTE 就是不生效，这是预期行为**，
    //   要等 P5-4（路径 B）在 NTE 的 BakedRail 上另开一条。

    /**
     * 捕获「当前正在渲染的轨道」：10 参数 {@code renderRailStandard} 的第 2 个参数（{@code Rail}，
     * 槽位 1）。
     * <p>
     * {@code argsOnly = true, index = 1} —— 参数表已用 {@code javap -p -c} 核实为
     * {@code (Level, Rail, float, boolean, float, String, F,F,F,F)}。
     * MTR4 上一版曾把它写成 {@code index = 0}（那是世界对象），处理器类型不匹配导致注入点
     * <b>一次都不会触发</b>，而 {@code require = 0} 把失败藏成静默 —— 于是出现
     * 「轨道整体被抬高但完全不倾斜」，排查了好几轮。这里保持 {@code index = 1} 并在 helper 里
     * 做一次性诊断。
     * <p>
     * <b>{@code method} 用完整描述符而不是裸名字</b>：{@code RenderTrains} 里
     * {@code renderRailStandard} 有<b>两个</b>重载（5 参数与 10 参数，{@code javap -p} 已核实），
     * 裸名字让处理器自己挑一个，解析结果不受控。描述符见
     * {@link RailTiltRenderHelper#RENDER_RAIL_STANDARD_10_DESCRIPTOR}。
     * <p>
     * 处理器只把原值原样返回（{@code @ModifyVariable} 语义上可以改写参数，这里刻意不改）。
     */
    @ModifyVariable(
            method = RailTiltRenderHelper.RENDER_RAIL_STANDARD_10_DESCRIPTOR,
            at = @At("HEAD"),
            argsOnly = true,
            index = 1,
            require = 0,
            remap = false
    )
    private static Rail fangsu$captureRailSection(Rail rail) {
        return RailTiltRenderHelper.captureSectionRail(rail);
    }

    /** 结束同步窗口（见 {@link #fangsu$captureRailSection} 的说明）。 */
    @Inject(
            method = RailTiltRenderHelper.RENDER_RAIL_STANDARD_10_DESCRIPTOR,
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private static void fangsu$endRailSection(CallbackInfo callbackInfo) {
        RailTiltRenderHelper.endRailSection();
    }

    /**
     * 包装单行道箭头（{@code lambda$renderRailStandard$15}）的延迟绘制消费者。
     * <p>
     * <b>注入点是 {@code lambda$renderRailStandard$17}（宿主 {@code RenderRail} 回调），
     * 不是 {@code $15}。</b>两个 {@code scheduleRender} 调用都写在这个回调里（源码 :479 / :487），
     * 而 {@code $15}/{@code $16} 是<b>被排队</b>的消费者，体内只有 {@code IDrawing.drawTexture}。
     * P5-3 曾把这两个 {@code @Redirect} 挂在 {@code $15}/{@code $16} 上 —— 注入点为零、
     * {@code require = 0} 静默通过，{@link RailTiltRenderHelper#wrapQuadConsumer} 一次都没跑过。
     * {@code ordinal} 按字节码顺序区分：{@code 0} = 箭头（{@code if} 分支），
     * {@code 1} = 轨面（{@code else} 分支）。
     * <p>
     * {@code @At} 已把重定向锁死在 {@code $17} 里那两个调用点上，因此信号
     * （{@code renderSignalsStandard} 走 {@code lambda$renderSignalsStandard$18}、不用
     * {@code scheduleRender}）、别处的 4 参数排队天然被排除。
     * <b>不在同步窗口内、或轨道没有滚转时，helper 返回原消费者实例</b>，
     * 排队与执行与 MTR 原生逐位一致。
     * <p>
     * {@code method} 必须显式写出：Mixin 0.8.7 的 {@code InjectionInfo.parseSelectors} 对
     * 「既无 {@code method} 又无 {@code target}」的 {@code @Redirect} 直接抛
     * {@code InvalidInjectionException}，游戏在 APPLY 阶段即崩；{@code @At} 只在已选定的方法内定位。
     * synthetic lambda 只能写名字（注解处理器解析不了 synthetic 描述符）。
     */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_SCHEDULE_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/render/RenderTrains;scheduleRender(Lnet/minecraft/resources/ResourceLocation;ZLmtr/render/RenderTrains$QueuedRenderLayer;Ljava/util/function/BiConsumer;)V",
                    ordinal = RailTiltRenderHelper.RAIL_ARROW_SCHEDULE_ORDINAL
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$scheduleRolledArrowQuad(
            ResourceLocation resourceLocation, boolean priority,
            RenderTrains.QueuedRenderLayer queuedRenderLayer,
            BiConsumer<PoseStack, VertexConsumer> consumer
    ) {
        RenderTrains.scheduleRender(resourceLocation, priority, queuedRenderLayer,
                RailTiltRenderHelper.wrapQuadConsumer(consumer));
    }

    /** 同上，轨面（{@code $17} 里第 2 个 {@code scheduleRender}，{@code ordinal = 1}）。 */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_SCHEDULE_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/render/RenderTrains;scheduleRender(Lnet/minecraft/resources/ResourceLocation;ZLmtr/render/RenderTrains$QueuedRenderLayer;Ljava/util/function/BiConsumer;)V",
                    ordinal = RailTiltRenderHelper.RAIL_QUAD_SCHEDULE_ORDINAL
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$scheduleRolledRailQuad(
            ResourceLocation resourceLocation, boolean priority,
            RenderTrains.QueuedRenderLayer queuedRenderLayer,
            BiConsumer<PoseStack, VertexConsumer> consumer
    ) {
        RenderTrains.scheduleRender(resourceLocation, priority, queuedRenderLayer,
                RailTiltRenderHelper.wrapQuadConsumer(consumer));
    }

    /**
     * 单行道箭头（{@code lambda$renderRailStandard$15}）的第 1 次 {@code drawTexture}。
     * <p>
     * 目标重载是 {@code IDrawing.drawTexture(PoseStack,VertexConsumer,F×16,Direction,II)}
     * —— 那 16 个 float = 4 个角点 ×(x,y,z) 加 u1,v1,u2,v2。轨面与箭头用的是<b>同一个</b>
     * {@code invokestatic}（同一个常量池项），只有所在 lambda 不同，所以两个 lambda 各挂一个
     * 同名重定向。<b>只有处于带滚转轨道的绘制窗口内才变换</b>，其余调用（含信号、普通轨道、
     * ghost 预览）原参数转发；旋转是刚体变换，保持角点顺序（=绕序），
     * u/v、light、color、{@code Direction} 全部原样透传。
     */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_ARROW_DRAW_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/client/IDrawing;drawTexture(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFLnet/minecraft/core/Direction;II)V"
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$drawRolledArrowQuadFirst(
            PoseStack matrices, VertexConsumer vertexConsumer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2,
            Direction facing, int color, int light
    ) {
        fangsu$drawRailQuad(matrices, vertexConsumer, false, true,
                x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
    }

    /**
     * 单行道箭头（{@code lambda$renderRailStandard$15}）的第 2 次 {@code drawTexture}
     * （同一工艺面、绕序相反的背面）。
     * <p>
     * 第 2 次的槽位是第 1 次角点身份的置换 {@code {2,1,4,3}}（连同 {@code +SMALL_OFFSET} 的
     * 交替位置一起交换），helper 会先把它归一成标准角点顺序再旋转，否则两次旋转的横向归类不同、
     * 正反面不再重合（z-fighting / 法线反向）。
     */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_ARROW_DRAW_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/client/IDrawing;drawTexture(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFLnet/minecraft/core/Direction;II)V",
                    ordinal = 1
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$drawRolledArrowQuadSecond(
            PoseStack matrices, VertexConsumer vertexConsumer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2,
            Direction facing, int color, int light
    ) {
        fangsu$drawRailQuad(matrices, vertexConsumer, true, true,
                x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
    }

    /** 轨面（{@code lambda$renderRailStandard$16}）的第 1 次 {@code drawTexture}。 */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_QUAD_DRAW_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/client/IDrawing;drawTexture(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFLnet/minecraft/core/Direction;II)V"
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$drawRolledRailQuadFirst(
            PoseStack matrices, VertexConsumer vertexConsumer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2,
            Direction facing, int color, int light
    ) {
        fangsu$drawRailQuad(matrices, vertexConsumer, false, false,
                x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
    }

    /** 轨面（{@code lambda$renderRailStandard$16}）的第 2 次 {@code drawTexture}（背面）。 */
    @Redirect(
            method = RailTiltRenderHelper.RAIL_QUAD_DRAW_TARGET,
            at = @At(
                    value = "INVOKE",
                    target = "Lmtr/client/IDrawing;drawTexture(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFLnet/minecraft/core/Direction;II)V",
                    ordinal = 1
            ),
            require = 0,
            remap = false
    )
    private static void fangsu$drawRolledRailQuadSecond(
            PoseStack matrices, VertexConsumer vertexConsumer,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2,
            Direction facing, int color, int light
    ) {
        fangsu$drawRailQuad(matrices, vertexConsumer, true, false,
                x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2, facing, color, light);
    }

    /**
     * 四个 {@code drawTexture} 重定向共用的转发体：先把角点身份归一（第 2 次绘制的槽位是
     * 第 1 次的置换 {@code {2,1,4,3}}），再交给 helper 旋转或原样转发。
     * <p>
     * {@code @Unique}：这是本 mixin 自己的方法，目标类里没有同名成员。
     */
    @Unique
    private static void fangsu$drawRailQuad(
            PoseStack matrices, VertexConsumer vertexConsumer,
            boolean secondPass, boolean arrow,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2,
            Direction facing, int color, int light
    ) {
        RailTiltRenderHelper.drawTiltedQuad(matrices, vertexConsumer,
                arrow ? RailTiltRenderHelper.normalizeArrowQuad(secondPass,
                        x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2)
                        : RailTiltRenderHelper.normalizeQuad(secondPass,
                        x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2),
                facing, color, light);
    }

    // ==================== P5-6：列车车体 / 风挡滚转的帧边界 ====================
    //
    // 车体滚转（RailTrainRollHelper）需要一个「每帧一次」的时机来重建「带滚转轨道」的候选表，
    // 并作为一次性诊断的慢时钟。MTR3 的每帧入口就是这个静态 render。
    //
    // ★ 这个钩子与 P5-3 的截面捕获钩子是两个独立关注点：
    //   * P5-3 的三个钩子决定「轨道带画成什么形状」，装了 NTE 时整条路径被 NTE cancel 掉；
    //   * 本钩子只服务「列车车体 / 风挡」，装了 NTE 时依然生效（NTE 只接管轨道带）。
    //   两者失败互不牵连，各自有各自的一次性日志。

    /**
     * 每帧开头：把「带滚转轨道」的候选表置脏，并推进 {@link RailTrainRollHelper} 的诊断时钟。
     * <p>
     * <b>必须写完整描述符</b>：{@code RenderTrains} 里 {@code render} 有<b>两个</b>重载
     * （{@code javap -p} 核实：静态的 4 参数版 {@code (EntitySeat,float,PoseStack,MultiBufferSource)V}
     * 与实例的 6 参数版 {@code (EntitySeat,float,float,PoseStack,MultiBufferSource,int)V}），
     * 裸名字由注解处理器自行挑选，解析结果不受控。
     * <p>
     * 处理器是<b>静态</b>方法：目标方法是静态方法，Mixin 强制静态性一致。
     * {@code require = 0}：将来 MTR 改名只会退化为「候选表按 100 ms TTL 刷新」而不是崩游戏，
     * 并由 helper 的一次性告警报出来。
     */
    @Inject(
            method = "render(Lmtr/entity/EntitySeat;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private static void fangsu$beginTrainRollFrame(
            EntitySeat entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, CallbackInfo callbackInfo
    ) {
        RailTrainRollHelper.beginRenderFrame();
    }

}
