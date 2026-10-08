package com.fangsu.ui;

import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.util.NodeConnector;
import mtr.data.Rail;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 万向节点角度配置界面。
 * <p>
 * 自 NTE mtrsteamloco {@code gui/DirectNodeScreen.java} 移植（MIT License, Copyright (c) 2022-present Zbx1425），
 * 界面结构参照 MTR4 版 {@code com.fangsu.ui.NodeAngleScreen}：角度输入框 + ±22.5° 步进 +
 * “绑定并保存” + “解绑”。
 * <p>
 * 与蓝本的差异（详见交付报告）：
 * <ul>
 *   <li>不做即时绑定：步进按钮只改输入框文本，点“绑定并保存”才写回节点（与 MTR4 版一致），
 *       避免每次点击都触发一次方块数据同步</li>
 *   <li>去掉了 ANTE 的 ClothConfig 参数面板与滑动条/文本框切换（FangSu MTR3 版无该配置体系）</li>
 *   <li>MTR3 3.2.2 没有 MTR4 的 {@code Rail.Shape}/样式编辑 API，故不提供轨道形状 / 样式编辑按钮；
 *       改为显示当前连接到的轨道类型（只读），未连接时显示“无”</li>
 *   <li><b>P5-2</b>：新增俯仰 / 翻滚 / 半轨距三个输入框。这是本子阶段唯一的<b>作者入口</b> ——
 *       没有它就无法在游戏里验证「翻滚让轨道中心线抬高」。三个数字与 MTR4 版共用同一套硬边界
 *       （{@link BlockEntityMultiDirectionNode#clampPitch}/{@code clampRoll}/{@code clampHalfGauge}），
 *       滑块/步进这类“拖动方便”的窄区间属于后续的完整配置界面（MTR4 的
 *       {@code MultiDirectionNodeConfigScreen}），此处只用输入框，避免引入未经测试的控件矩阵。</li>
 * </ul>
 * 本界面为纯客户端界面，最终状态通过
 * {@link BlockEntityMultiDirectionNode#setNodeAngles(double, double)} 等写入本地方块实体
 * （写入即发 BE_SYNC），随后 {@link BlockEntityMultiDirectionNode#refreshConnectedRailsIfNeeded()}
 * 发一次 {@code NODE_REFRESH_RAIL} 让服务端原地重建轨道几何。
 */
public class NodeAngleScreen extends Screen {

    private final BlockEntityMultiDirectionNode node;
    private double angle;
    private double pitch;
    private double roll;
    private double halfGauge;
    private EditBox angleInput;
    private EditBox pitchInput;
    private EditBox rollInput;
    private EditBox halfGaugeInput;

    public NodeAngleScreen(BlockEntityMultiDirectionNode node) {
        super(ComponentHelper.translatable("ui.fangsu.multi_direction_node.title"));
        this.node = node;
        this.angle = RailAngleExtra.normalizeNodeDegrees(node.getDirectionDegrees());
        this.pitch = node.getPitchDegrees();
        this.roll = node.getRollDegrees();
        this.halfGauge = node.getRollOffsetM();
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2;
        final int yBase = this.height / 2 - 100;

        // ---- 方向 ----
        angleInput = new EditBox(this.font, centerX - 80, yBase - 10, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.angle"));
        angleInput.setValue(String.format(Locale.ROOT, "%.2f", angle));
        this.addRenderableWidget(angleInput);

        this.addRenderableWidget(ComponentHelper.button(centerX - 80, yBase + 20, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.m22"), b -> step(-22.5D)));
        this.addRenderableWidget(ComponentHelper.button(centerX + 4, yBase + 20, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.p22"), b -> step(22.5D)));

        // ---- 俯仰 / 翻滚 / 半轨距（P5-2）----
        // 俯仰：节点处轨道切线的竖向坡角，正 = 沿节点方向前进时上坡（钳制 ±MAX_PITCH_DEG）
        pitchInput = new EditBox(this.font, centerX - 80, yBase + 48, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.pitch"));
        pitchInput.setValue(String.format(Locale.ROOT, "%.2f", pitch));
        this.addRenderableWidget(pitchInput);

        // 翻滚：绕节点前进轴旋转，正 = 前进方向右手侧抬高（钳制 ±MAX_ROLL_DEG）。
        // 这是让轨道中心线抬高「半轨距·|sin(翻滚)|」的那个量。
        rollInput = new EditBox(this.font, centerX - 80, yBase + 76, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.roll"));
        rollInput.setValue(String.format(Locale.ROOT, "%.2f", roll));
        this.addRenderableWidget(rollInput);

        // 半轨距（米）：外轨超高的抬升系数，默认 1435mm/2 = 0.7175（钳制 [0.25, 2.0]）
        halfGaugeInput = new EditBox(this.font, centerX - 80, yBase + 104, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.halfGauge"));
        halfGaugeInput.setValue(String.format(Locale.ROOT, "%.4f", halfGauge));
        this.addRenderableWidget(halfGaugeInput);

        // 外轨超高开关（只门控翻滚对几何的贡献；俯仰不受影响）
        final Button buttonSuperelevation = ComponentHelper.button(centerX - 80, yBase + 132, 160, 20,
                superelevationText(), b -> {
                    node.setSuperelevation(!node.isSuperelevationEnabled());
                    rebuildWidgets();
                });
        this.addRenderableWidget(buttonSuperelevation);

        this.addRenderableWidget(ComponentHelper.button(centerX - 80, yBase + 160, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.bind_and_save"), b -> saveAndClose()));

        // 解绑：仅在“已绑定且未连接轨道”时可用（已连接时方向被轨道几何约束）
        final Button buttonUnbind = ComponentHelper.button(centerX - 80, yBase + 188, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.unbind"), b -> {
                    node.unbind();
                    angle = RailAngleExtra.normalizeNodeDegrees(node.getDirectionDegrees());
                    rebuildWidgets();
                });
        buttonUnbind.active = node.isDirectionBonded() && !node.isConnected();
        this.addRenderableWidget(buttonUnbind);

        // 状态显示（只读按钮，避免跨版本自绘文本）
        final Button buttonStatus = ComponentHelper.button(centerX + 4, yBase + 188, 77, 20, statusText(), b -> {
        });
        buttonStatus.active = false;
        this.addRenderableWidget(buttonStatus);

        // 当前连接的轨道类型（只读）；MTR3 无轨道形状 / 样式编辑 API，仅作信息展示
        final Rail rail = findConnectedRail();
        final Component railLabel = rail == null
                ? ComponentHelper.translatable("ui.fangsu.multi_direction_node.no_rail")
                : ComponentHelper.literal(rail.railType.name());
        final Button buttonRail = ComponentHelper.button(centerX - 80, yBase + 216, 160, 20, railLabel, b -> {
        });
        buttonRail.active = false;
        this.addRenderableWidget(buttonRail);
    }

    /** 外轨超高开关按钮的文案（开 / 关）。键名与 MTR4 版一致。 */
    private Component superelevationText() {
        return ComponentHelper.translatable("ui.fangsu.multi_direction_node.superelevation",
                ComponentHelper.translatable(node.isSuperelevationEnabled()
                        ? "ui.fangsu.multi_direction_node.superelevationOn"
                        : "ui.fangsu.multi_direction_node.superelevationOff"));
    }

    /**
     * 客户端读取连接到本节点的第一条轨道（MTR 客户端数据未同步时返回 null）。
     */
    @Nullable
    private Rail findConnectedRail() {
        try {
            return NodeConnector.findFirstConnectedRail(node.getBlockPos());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Component statusText() {
        final Component bound = ComponentHelper.translatable(node.isDirectionBonded()
                ? "ui.fangsu.multi_direction_node.bound"
                : "ui.fangsu.multi_direction_node.unbound");
        final Component connected = ComponentHelper.translatable(node.isConnected()
                ? "ui.fangsu.multi_direction_node.connected"
                : "ui.fangsu.multi_direction_node.not_connected");
        return ComponentHelper.empty().append(bound).append(ComponentHelper.literal(" / ")).append(connected);
    }

    private void step(double delta) {
        angle = RailAngleExtra.normalizeNodeDegrees(angle + delta);
        angleInput.setValue(String.format(Locale.ROOT, "%.2f", angle));
    }

    /**
     * 把输入框里的文本解析成 double；非法输入保留原值（不抛、不清零）。
     */
    private static double parseOrKeep(EditBox box, double fallback) {
        try {
            return Double.parseDouble(box.getValue().trim());
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }

    private void saveAndClose() {
        angle = RailAngleExtra.normalizeNodeDegrees(parseOrKeep(angleInput, angle));
        // 俯仰 / 翻滚 / 半轨距交由方块实体的钳制函数处理（NaN / 无穷 / 超界在这里被规范化）
        pitch = parseOrKeep(pitchInput, pitch);
        roll = parseOrKeep(rollInput, roll);
        halfGauge = parseOrKeep(halfGaugeInput, halfGauge);
        // 写入顺序无要求（writeC2S 是全量载荷），但半轨距与开关必须先写，
        // 这样 setAnglesAndSync 最后一次 BE_SYNC 就能带齐全部改动。
        node.setRollOffsetM(halfGauge);
        node.bind(angle);
        node.setAnglesAndSync(pitch, roll);
        // 姿态已进入本地方块实体 → 请求服务端原地重建轨道几何（俯仰 / 翻滚 / 平移都改变了几何）。
        // 服务端 readC2S 也会按新姿态重建一次；两条路径幂等，重复重建得到相同几何。
        node.refreshConnectedRailsIfNeeded();
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
