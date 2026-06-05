package com.fangsu.ui;

import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 屏蔽门集控配置界面
 * 包含：门隔离开关、门开启开关（隔离打开时可用）、起始坐标列表编辑
 */
public class ScreendoorCentralControlScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    private static final int GAP = 5;

    private final BlockEntityScreendoorCentralControl ctrl;
    private boolean isolation;
    private boolean doorOpen;

    // 起始坐标编辑相关
    private final List<BlockPos> startPositions = new ArrayList<>();
    private final List<PositionEditRow> positionRows = new ArrayList<>();

    private Button isolationBtn;
    private Button doorOpenBtn;
    private Button addPosBtn;
    private Button scanBtn;
    private Button saveBtn;
    private int startPositionsLabelY;

    public ScreendoorCentralControlScreen(BlockEntityScreendoorCentralControl ctrl) {
        super(Component.translatable("ui.fangsu.screendoor.centralControl"));
        this.ctrl = ctrl;
        this.isolation = ctrl.isIsolation();
        this.doorOpen = ctrl.isDoorOpen();
        this.startPositions.addAll(ctrl.getStartPositions());
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;

        int y = 30;

        // 标题
        // (rendered in render() method)

        // ===== 门隔离 =====
        y += 10;
        isolationBtn = Button.builder(
                getIsolationLabel(),
                btn -> {
                    isolation = !isolation;
                    btn.setMessage(getIsolationLabel());
                    updateDoorOpenButton();
                }
        ).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        addRenderableWidget(isolationBtn);

        // ===== 门开启（仅在隔离打开时可用） =====
        y += 25;
        doorOpenBtn = Button.builder(
                getDoorOpenLabel(),
                btn -> {
                    doorOpen = !doorOpen;
                    btn.setMessage(getDoorOpenLabel());
                }
        ).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        addRenderableWidget(doorOpenBtn);
        updateDoorOpenButton();

        // ===== 起始坐标列表（通过 render 方法绘制文字） =====
        y += 30;
        startPositionsLabelY = y;
        y += 12;
        // 重新构建坐标编辑行
        positionRows.clear();
        for (int i = 0; i < startPositions.size(); i++) {
            PositionEditRow row = new PositionEditRow(panelLeft, y, i, startPositions.get(i));
            positionRows.add(row);
            y += 22;
        }

        // ===== 添加坐标按钮 =====
        addPosBtn = Button.builder(
                Component.translatable("ui.fangsu.screendoor.centralControl.addPos"),
                btn -> {
                    startPositions.add(BlockPos.ZERO);
                    rebuildWidgets();
                }
        ).bounds(panelLeft, y, PANEL_WIDTH / 2 - GAP / 2, 20).build();
        addRenderableWidget(addPosBtn);

        // ===== 重新扫描按钮 =====
        scanBtn = Button.builder(
                Component.translatable("ui.fangsu.screendoor.centralControl.scan"),
                btn -> {
                    // 更新坐标并扫描
                    syncPositionsFromRows();
                    ctrl.getStartPositions().clear();
                    ctrl.getStartPositions().addAll(startPositions);
                    ctrl.scanDoors();
                    ctrl.syncPositionsToServer();
                }
        ).bounds(panelLeft + PANEL_WIDTH / 2 + GAP / 2, y, PANEL_WIDTH / 2 - GAP / 2, 20).build();
        addRenderableWidget(scanBtn);

        // ===== 保存并退出 =====
        y += 30;
        saveBtn = Button.builder(
                Component.translatable("ui.fangsu.block.close_and_save"),
                btn -> {
                    saveAndClose();
                }
        ).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        addRenderableWidget(saveBtn);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        renderBackground(graphics);

        int centerX = this.width / 2;

        // 标题
        graphics.drawString(
                this.font,
                this.title,
                centerX - this.font.width(this.title) / 2,
                10,
                0xFFFFFF,
                false
        );

        // 起始坐标列表标题
        graphics.drawString(
                this.font,
                Component.translatable("ui.fangsu.screendoor.centralControl.startPositions"),
                centerX - this.font.width(Component.translatable("ui.fangsu.screendoor.centralControl.startPositions")) / 2,
                startPositionsLabelY,
                0xFFFFFF,
                false
        );

        // 隔离状态指示
        String isolationStatus = isolation
                ? Component.translatable("ui.fangsu.common.on").getString()
                : Component.translatable("ui.fangsu.common.off").getString();
        graphics.drawString(
                this.font,
                Component.translatable("ui.fangsu.screendoor.centralControl.isolationStatus", isolationStatus),
                centerX - PANEL_WIDTH / 2,
                55,
                0xAAAAAA,
                false
        );

        // 扫描到的屏蔽门数量
        int doorCount = ctrl.getDoorPositions().size();
        graphics.drawString(
                this.font,
                Component.translatable("ui.fangsu.screendoor.centralControl.doorCount", doorCount),
                centerX - PANEL_WIDTH / 2,
                this.height - 70,
                0xAAAAAA,
                false
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateDoorOpenButton() {
        doorOpenBtn.active = isolation;
        doorOpenBtn.setMessage(getDoorOpenLabel());
    }

    private Component getIsolationLabel() {
        return Component.translatable("ui.fangsu.screendoor.centralControl.isolation",
                Component.translatable(isolation ? "ui.fangsu.common.on" : "ui.fangsu.common.off"));
    }

    private Component getDoorOpenLabel() {
        return Component.translatable("ui.fangsu.screendoor.centralControl.doorOpen",
                Component.translatable(doorOpen ? "ui.fangsu.common.on" : "ui.fangsu.common.off"));
    }

    private void syncPositionsFromRows() {
        startPositions.clear();
        for (PositionEditRow row : positionRows) {
            startPositions.add(row.getPos());
        }
    }

    private void saveAndClose() {
        // 从编辑行同步坐标
        syncPositionsFromRows();

        // 更新状态到 BE
        ctrl.setIsolation(isolation);
        ctrl.setDoorOpen(doorOpen);

        // 更新起始坐标
        ctrl.getStartPositions().clear();
        ctrl.getStartPositions().addAll(startPositions);

        // 通知服务端 - 先更新状态
        ctrl.syncToServer();
        // 再更新坐标
        ctrl.syncPositionsToServer();

        onClose();
    }

    // ======================== 坐标编辑行 ========================

    private class PositionEditRow {
        private final EditBox xBox, yBox, zBox;
        private final Button removeBtn;
        private final int index;

        PositionEditRow(int panelLeft, int y, int index, BlockPos pos) {
            this.index = index;

            int fieldWidth = 50;
            int spacing = 3;
            int totalWidth = fieldWidth * 3 + spacing * 2 + 20 + spacing;

            int rowLeft = panelLeft + (PANEL_WIDTH - totalWidth) / 2;

            xBox = new EditBox(font, rowLeft, y, fieldWidth, 18, Component.empty());
            xBox.setValue(String.valueOf(pos.getX()));
            addRenderableWidget(xBox);

            yBox = new EditBox(font, rowLeft + fieldWidth + spacing, y, fieldWidth, 18, Component.empty());
            yBox.setValue(String.valueOf(pos.getY()));
            addRenderableWidget(yBox);

            zBox = new EditBox(font, rowLeft + (fieldWidth + spacing) * 2, y, fieldWidth, 18, Component.empty());
            zBox.setValue(String.valueOf(pos.getZ()));
            addRenderableWidget(zBox);

            removeBtn = Button.builder(
                    Component.literal("X"),
                    btn -> {
                        startPositions.remove(index);
                        rebuildWidgets();
                    }
            ).bounds(rowLeft + (fieldWidth + spacing) * 3, y, 20, 18).build();
            addRenderableWidget(removeBtn);
        }

        BlockPos getPos() {
            try {
                int x = parseIntSafe(xBox.getValue());
                int y = parseIntSafe(yBox.getValue());
                int z = parseIntSafe(zBox.getValue());
                return new BlockPos(x, y, z);
            } catch (Exception e) {
                return BlockPos.ZERO;
            }
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
