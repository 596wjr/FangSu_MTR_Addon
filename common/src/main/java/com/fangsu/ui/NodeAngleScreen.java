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
 * </ul>
 * 本界面为纯客户端界面，最终状态通过 {@link BlockEntityMultiDirectionNode#bind(double)}
 * 同步到服务端（服务端 {@code readC2S} 后负责按新角度重建已连接轨道）。
 */
public class NodeAngleScreen extends Screen {

    private final BlockEntityMultiDirectionNode node;
    private double angle;
    private EditBox angleInput;

    public NodeAngleScreen(BlockEntityMultiDirectionNode node) {
        super(ComponentHelper.translatable("ui.fangsu.multi_direction_node.title"));
        this.node = node;
        this.angle = RailAngleExtra.normalizeNodeDegrees(node.getDirectionDegrees());
    }

    @Override
    protected void init() {
        super.init();

        final int centerX = this.width / 2;
        final int yBase = this.height / 2 - 60;

        angleInput = new EditBox(this.font, centerX - 80, yBase - 10, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.angle"));
        angleInput.setValue(String.format(Locale.ROOT, "%.2f", angle));
        this.addRenderableWidget(angleInput);

        this.addRenderableWidget(ComponentHelper.button(centerX - 80, yBase + 20, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.m22"), b -> step(-22.5D)));
        this.addRenderableWidget(ComponentHelper.button(centerX + 4, yBase + 20, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.p22"), b -> step(22.5D)));
        this.addRenderableWidget(ComponentHelper.button(centerX - 80, yBase + 48, 160, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.bind_and_save"), b -> saveAndClose()));

        // 解绑：仅在“已绑定且未连接轨道”时可用（已连接时方向被轨道几何约束）
        final Button buttonUnbind = ComponentHelper.button(centerX - 80, yBase + 76, 77, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.unbind"), b -> {
                    node.unbind();
                    angle = RailAngleExtra.normalizeNodeDegrees(node.getDirectionDegrees());
                    rebuildWidgets();
                });
        buttonUnbind.active = node.isDirectionBonded() && !node.isConnected();
        this.addRenderableWidget(buttonUnbind);

        // 状态显示（只读按钮，避免跨版本自绘文本）
        final Button buttonStatus = ComponentHelper.button(centerX + 4, yBase + 76, 77, 20, statusText(), b -> {
        });
        buttonStatus.active = false;
        this.addRenderableWidget(buttonStatus);

        // 当前连接的轨道类型（只读）；MTR3 无轨道形状 / 样式编辑 API，仅作信息展示
        final Rail rail = findConnectedRail();
        final Component railLabel = rail == null
                ? ComponentHelper.translatable("ui.fangsu.multi_direction_node.no_rail")
                : ComponentHelper.literal(rail.railType.name());
        final Button buttonRail = ComponentHelper.button(centerX - 80, yBase + 104, 160, 20, railLabel, b -> {
        });
        buttonRail.active = false;
        this.addRenderableWidget(buttonRail);
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

    private void saveAndClose() {
        try {
            angle = RailAngleExtra.normalizeNodeDegrees(Double.parseDouble(angleInput.getValue()));
        } catch (NumberFormatException ignored) {
            // 输入非法时保留上一次角度
        }
        // bind 内部：客户端会立即本地生效并发送 C2S（服务端 readC2S 后按新角度重建已连接轨道）
        node.bind(angle);
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
