package com.fangsu.ui;

import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.NteRailManager;
import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 道岔配置界面（扳手右键打开）：一个轨道模型选择 + 「重新构建」+「解绑」。
 * <p>
 * <b>复用路径</b>：模型选择直接复用既有的 {@link ModelSelectScreen}（列表 + 详情 + 确认），
 * 本界面只负责把"当前选中的轨道 id"接到 {@link BlockEntityTurnout#setRailModelId(String)} 上，
 * 避免再造一套下拉控件。
 * <p>
 * <b>所有会改服务端状态的动作都靠发包</b>：重新构建 / 解绑各自发一条 C2S，
 * 结果由服务端回包决定（本界面从不直接改绑定）。
 */
public class TurnoutConfigScreen extends Screen {

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 8;
    private static final String DEFAULT_RAIL_ID = BlockEntityTurnout.DEFAULT_RAIL_MODEL;

    private final BlockEntityTurnout turnout;

    private Button modelButton;
    private Button statusButton;

    public TurnoutConfigScreen(BlockEntityTurnout turnout) {
        super(ComponentHelper.translatable("ui.fangsu.turnout.title"));
        this.turnout = turnout;
    }

    @Override
    protected void init() {
        super.init();
        final int cx = this.width / 2;
        boolean threeWay = turnout.getBinding() != null && turnout.getBinding().kind() == com.fangsu.turnout.TurnoutKind.THREE_WAY;
        int y = this.height / 2 - (threeWay ? 60 : 46);

        modelButton = this.addRenderableWidget(ComponentHelper.button(
                cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                modelLabel(), button -> openRailSelect()));
        y += BUTTON_HEIGHT + ROW_GAP;

        // 只读状态行：借一个 inactive 按钮当文本标签，省掉跨版本的 render 签名差异
        statusButton = this.addRenderableWidget(ComponentHelper.button(
                cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                statusLabel(), button -> {
                }));
        statusButton.active = false;
        y += BUTTON_HEIGHT + ROW_GAP;

        if (threeWay) {
            Button signalHint = this.addRenderableWidget(ComponentHelper.button(
                    cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                    ComponentHelper.translatable("ui.fangsu.turnout.three_way_signal"), button -> { }));
            signalHint.active = false;
            y += BUTTON_HEIGHT + ROW_GAP;
        }

        this.addRenderableWidget(ComponentHelper.button(
                cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                ComponentHelper.translatable("ui.fangsu.turnout.rebuild"),
                button -> onRebuild()));
        y += BUTTON_HEIGHT + ROW_GAP;

        this.addRenderableWidget(ComponentHelper.button(
                cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                ComponentHelper.translatable("ui.fangsu.turnout.unbind"),
                button -> onUnbind()));
        y += BUTTON_HEIGHT + ROW_GAP;

        this.addRenderableWidget(ComponentHelper.button(
                cx - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                ComponentHelper.translatable("ui.fangsu.turnout.close"),
                button -> onClose()));
    }

    private void onRebuild() {
        if (turnout == null) return;
        turnout.requestRebuild();
        onClose();
    }

    private void onUnbind() {
        if (turnout == null) return;
        turnout.requestUnbind();
        onClose();
    }

    /** 打开既有的模型选择界面（确认后自动回到本界面）。 */
    private void openRailSelect() {
        if (turnout == null) return;
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
                ComponentHelper.translatable("ui.fangsu.turnout.rail_model"),
                turnout,
                buildOptions(),
                be -> be instanceof BlockEntityTurnout t ? t.getRailModelId() : DEFAULT_RAIL_ID,
                (be, value) -> {
                    if (be instanceof BlockEntityTurnout t) {
                        t.setRailModelId(value);
                    }
                },
                this,
                this::refreshLabels
        ));
    }

    /** 选项 = MTR 自带轨道 + 全部已加载的自定义轨道。 */
    private List<ModelSelectInfo> buildOptions() {
        final List<ModelSelectInfo> options = new ArrayList<>();
        options.add(new RailOption(DEFAULT_RAIL_ID, "ui.fangsu.turnout.default_rail"));
        for (NteRailManager.NteRail rail : NteRailManager.getInstance().getRails()) {
            if (rail == null || rail.id() == null || rail.id().isEmpty() || DEFAULT_RAIL_ID.equals(rail.id())) continue;
            options.add(new RailOption(rail.id(), rail.name() == null || rail.name().isEmpty()
                    ? rail.id() : rail.name()));
        }
        return options;
    }

    private void refreshLabels() {
        if (modelButton != null) modelButton.setMessage(modelLabel());
        if (statusButton != null) statusButton.setMessage(statusLabel());
    }

    private Component modelLabel() {
        final String id = turnout == null ? DEFAULT_RAIL_ID : turnout.getRailModelId();
        return ComponentHelper.translatable("ui.fangsu.turnout.rail_model")
                .append(ComponentHelper.literal(": "))
                .append(displayName(id));
    }

    private Component statusLabel() {
        if (turnout == null) return ComponentHelper.empty();
        if (turnout.isBuilding()) {
            return ComponentHelper.translatable("ui.fangsu.turnout.status_building");
        }
        if (!turnout.hasBinding()) {
            return ComponentHelper.translatable("ui.fangsu.turnout.status_unbound");
        }
        return ComponentHelper.translatable("ui.fangsu.turnout.status_bound")
                .append(ComponentHelper.literal(" · "))
                .append(ComponentHelper.translatable(turnout.getBinding().kind().langKey()));
    }

    /** 轨道 id → 显示名（自定义轨道取 name，找不到就显示 id 本身）。 */
    private Component displayName(@Nullable String id) {
        if (id == null || id.isEmpty() || DEFAULT_RAIL_ID.equals(id)) {
            return ComponentHelper.translatable("ui.fangsu.turnout.default_rail");
        }
        final NteRailManager.NteRail rail = NteRailManager.getInstance().getRail(id);
        if (rail != null && rail.name() != null && !rail.name().isEmpty()) {
            return ComponentHelper.translatable(rail.name());
        }
        return ComponentHelper.literal(id);
    }

    /**
     * {@link ModelSelectScreen} 需要的最小 {@link ModelSelectInfo} 实现：
     * {@code textKey} 既可能是语言键（自带轨道的默认项），也可能是一段字面名字（自定义轨道）。
     */
    private record RailOption(String id, String textKey) implements ModelSelectInfo {

        @Override
        public String getText() {
            return textKey;
        }

        @Override
        public String getContent() {
            return id;
        }

        @Override
        public String getContentText() {
            return textKey;
        }

        @Override
        public JsonObject getDefault() {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
