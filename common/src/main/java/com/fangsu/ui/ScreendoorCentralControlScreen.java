package com.fangsu.ui;

import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 灞忚斀闂ㄩ泦鎺ч厤缃晫锟?
 * 鍖呭惈锛氶棬闅旂寮€鍏炽€侀棬寮€鍚紑鍏筹紙闅旂鎵撳紑鏃跺彲鐢級銆佽捣濮嬪潗鏍囧垪琛ㄧ紪锟?
 */
public class ScreendoorCentralControlScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    private static final int GAP = 5;

    private final BlockEntityScreendoorCentralControl ctrl;
    private boolean isolation;
    private boolean doorOpen;

    // 璧峰鍧愭爣缂栬緫鐩稿叧
    private final List<BlockPos> startPositions = new ArrayList<>();
    private final List<PositionEditRow> positionRows = new ArrayList<>();

    private Button isolationBtn;
    private Button doorOpenBtn;
    private Button addPosBtn;
    private Button scanBtn;
    private Button saveBtn;
    private int startPositionsLabelY;

    public ScreendoorCentralControlScreen(BlockEntityScreendoorCentralControl ctrl) {
        super(ComponentHelper.translatable("ui.fangsu.screendoor.centralControl"));
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

        // 鏍囬
        // (rendered in render() method)

        // ===== 闂ㄩ殧锟?=====
        y += 10;
        //#if MC_VERSION >= 11903
        isolationBtn = Button.builder(getIsolationLabel(), btn -> {
            isolation = !isolation;
            btn.setMessage(getIsolationLabel());
            updateDoorOpenButton();
        }).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        //#else
        //$$ isolationBtn = new Button(panelLeft, y, PANEL_WIDTH, 20, getIsolationLabel(), btn -> { isolation = !isolation; btn.setMessage(getIsolationLabel()); updateDoorOpenButton(); });
        //#endif
        addRenderableWidget(isolationBtn);

        // ===== 闂ㄥ紑鍚紙浠呭湪闅旂鎵撳紑鏃跺彲鐢級 =====
        y += 25;
        //#if MC_VERSION >= 11903
        doorOpenBtn = Button.builder(getDoorOpenLabel(), btn -> {
            doorOpen = !doorOpen;
            btn.setMessage(getDoorOpenLabel());
        }).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        //#else
        //$$ doorOpenBtn = new Button(panelLeft, y, PANEL_WIDTH, 20, getDoorOpenLabel(), btn -> { doorOpen = !doorOpen; btn.setMessage(getDoorOpenLabel()); });
        //#endif
        addRenderableWidget(doorOpenBtn);
        updateDoorOpenButton();

        // ===== 璧峰鍧愭爣鍒楄〃锛堥€氳繃 render 鏂规硶缁樺埗鏂囧瓧锟?=====
        y += 30;
        startPositionsLabelY = y;
        y += 12;
        // 閲嶆柊鏋勫缓鍧愭爣缂栬緫锟?
        positionRows.clear();
        for (int i = 0; i < startPositions.size(); i++) {
            PositionEditRow row = new PositionEditRow(panelLeft, y, i, startPositions.get(i));
            positionRows.add(row);
            y += 22;
        }

        // ===== 娣诲姞鍧愭爣鎸夐挳 =====
        //#if MC_VERSION >= 11903
        addPosBtn = Button.builder(ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.addPos"), btn -> {
            startPositions.add(BlockPos.ZERO); /*#if MC_VERSION >= 11900*/
            rebuildWidgets(); /*#endif*/
        }).bounds(panelLeft, y, PANEL_WIDTH / 2 - GAP / 2, 20).build();
        //#else
        //$$ addPosBtn = new Button(panelLeft, y, PANEL_WIDTH / 2 - GAP / 2, 20, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.addPos"), btn -> { startPositions.add(BlockPos.ZERO); });
        //#endif
        addRenderableWidget(addPosBtn);

        // ===== 閲嶆柊鎵弿鎸夐挳 =====
        //#if MC_VERSION >= 11903
        scanBtn = Button.builder(ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.scan"), btn -> {
            syncPositionsFromRows();
            ctrl.getStartPositions().clear();
            ctrl.getStartPositions().addAll(startPositions);
            ctrl.scanDoors();
            ctrl.syncPositionsToServer();
        }).bounds(panelLeft + PANEL_WIDTH / 2 + GAP / 2, y, PANEL_WIDTH / 2 - GAP / 2, 20).build();
        //#else
        //$$ scanBtn = new Button(panelLeft + PANEL_WIDTH / 2 + GAP / 2, y, PANEL_WIDTH / 2 - GAP / 2, 20, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.scan"), btn -> { syncPositionsFromRows(); ctrl.getStartPositions().clear(); ctrl.getStartPositions().addAll(startPositions); ctrl.scanDoors(); ctrl.syncPositionsToServer(); });
        //#endif
        addRenderableWidget(scanBtn);

        // ===== 淇濆瓨骞堕€€锟?=====
        y += 30;
        //#if MC_VERSION >= 11903
        saveBtn = Button.builder(ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> {
            saveAndClose();
        }).bounds(panelLeft, y, PANEL_WIDTH, 20).build();
        //#else
        //$$ saveBtn = new Button(panelLeft, y, PANEL_WIDTH, 20, ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> { saveAndClose(); });
        //#endif
        addRenderableWidget(saveBtn);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = this.width / 2;
        graphics.drawString(this.font, this.title, centerX - this.font.width(this.title) / 2, 10, 0xFFFFFF, false);
        graphics.drawString(this.font, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.startPositions"),
                centerX - this.font.width(ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.startPositions")) / 2,
                startPositionsLabelY, 0xFFFFFF, false);
        String isolationStatus = isolation
                ? ComponentHelper.translatable("ui.fangsu.common.on").getString()
                : ComponentHelper.translatable("ui.fangsu.common.off").getString();
        graphics.drawString(this.font, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.isolationStatus", isolationStatus),
                centerX - PANEL_WIDTH / 2, 55, 0xAAAAAA, false);
        int doorCount = ctrl.getDoorPositions().size();
        graphics.drawString(this.font, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.doorCount", doorCount),
                centerX - PANEL_WIDTH / 2, this.height - 70, 0xAAAAAA, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(poseStack);
    //$$     int centerX = this.width / 2;
    //$$     this.font.draw(poseStack, this.title, centerX - this.font.width(this.title) / 2f, 10, 0xFFFFFF);
    //$$     this.font.draw(poseStack, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.startPositions"),
    //$$             centerX - this.font.width(ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.startPositions")) / 2f,
    //$$             startPositionsLabelY, 0xFFFFFF);
    //$$     String isolationStatus = isolation
    //$$             ? ComponentHelper.translatable("ui.fangsu.common.on").getString()
    //$$             : ComponentHelper.translatable("ui.fangsu.common.off").getString();
    //$$     this.font.draw(poseStack, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.isolationStatus", isolationStatus),
    //$$             centerX - PANEL_WIDTH / 2f, 55, 0xAAAAAA);
    //$$     int doorCount = ctrl.getDoorPositions().size();
    //$$     this.font.draw(poseStack, ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.doorCount", doorCount),
    //$$             centerX - PANEL_WIDTH / 2f, this.height - 70, 0xAAAAAA);
    //$$     super.render(poseStack, mouseX, mouseY, partialTick);
    //$$ }
    //#endif

    private void updateDoorOpenButton() {
        doorOpenBtn.active = isolation;
        doorOpenBtn.setMessage(getDoorOpenLabel());
    }

    private Component getIsolationLabel() {
        return ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.isolation",
                ComponentHelper.translatable(isolation ? "ui.fangsu.common.on" : "ui.fangsu.common.off"));
    }

    private Component getDoorOpenLabel() {
        return ComponentHelper.translatable("ui.fangsu.screendoor.centralControl.doorOpen",
                ComponentHelper.translatable(doorOpen ? "ui.fangsu.common.on" : "ui.fangsu.common.off"));
    }

    private void syncPositionsFromRows() {
        startPositions.clear();
        for (PositionEditRow row : positionRows) {
            startPositions.add(row.getPos());
        }
    }

    private void saveAndClose() {
        // 浠庣紪杈戣鍚屾鍧愭爣
        syncPositionsFromRows();

        // 鏇存柊鐘舵€佸埌 BE
        ctrl.setIsolation(isolation);
        ctrl.setDoorOpen(doorOpen);

        // 鏇存柊璧峰鍧愭爣
        ctrl.getStartPositions().clear();
        ctrl.getStartPositions().addAll(startPositions);

        // 閫氱煡鏈嶅姟锟?- 鍏堟洿鏂扮姸锟?
        ctrl.syncToServer();
        // 鍐嶆洿鏂板潗锟?
        ctrl.syncPositionsToServer();

        onClose();
    }

    // ======================== 鍧愭爣缂栬緫锟?========================

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

            //#if MC_VERSION >= 11900
            xBox = new EditBox(font, rowLeft, y, fieldWidth, 18, Component.empty());
            //#else
            //$$ xBox = new EditBox(font, rowLeft, y, fieldWidth, 18, ComponentHelper.empty());
            //#endif
            xBox.setValue(String.valueOf(pos.getX()));
            addRenderableWidget(xBox);

            //#if MC_VERSION >= 11900
            yBox = new EditBox(font, rowLeft + fieldWidth + spacing, y, fieldWidth, 18, Component.empty());
            //#else
            //$$ yBox = new EditBox(font, rowLeft + fieldWidth + spacing, y, fieldWidth, 18, ComponentHelper.empty());
            //#endif
            yBox.setValue(String.valueOf(pos.getY()));
            addRenderableWidget(yBox);

            //#if MC_VERSION >= 11900
            zBox = new EditBox(font, rowLeft + (fieldWidth + spacing) * 2, y, fieldWidth, 18, Component.empty());
            //#else
            //$$ zBox = new EditBox(font, rowLeft + (fieldWidth + spacing) * 2, y, fieldWidth, 18, ComponentHelper.empty());
            //#endif
            zBox.setValue(String.valueOf(pos.getZ()));
            addRenderableWidget(zBox);

            //#if MC_VERSION >= 11903
            removeBtn = Button.builder(Component.literal("X"), btn -> {
                startPositions.remove(pos);
                /*#if MC_VERSION >= 11900*/ rebuildWidgets(); /*#endif*/
            }).bounds(rowLeft + (fieldWidth + spacing) * 3, y, 20, 18).build();
            //#else
            //$$ removeBtn = new Button(rowLeft + (fieldWidth + spacing) * 3, y, 20, 18, ComponentHelper.literal("X"), btn -> { startPositions.remove(pos); });
            //#endif
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
