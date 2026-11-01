package com.fangsu.ui;

import com.fangsu.blockEntities.FunctionalObjBlockEntity;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 与万向节点共用数值输入方式：左列平移，右列旋转，其余控件逐行交替排布。 */
public class ObjBlockConfigScreen extends NumericInputConfigScreen {
    private static final int GAP = 4;
    private static final int COLUMN_GAP = 6;
    private static final int MIN_COLUMN_WIDTH = 60;
    private static final float PANEL_WIDTH_FACTOR = 1.5f;

    private final FunctionalObjBlockEntity be;
    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;
    private List<ConfigEntry<?>> configs;

    public ObjBlockConfigScreen(FunctionalObjBlockEntity be) {
        super(ComponentHelper.translatable("ui.fangsu.block.title"));
        this.be = be;

        if (be != null) {
            this.translateX = be.translateX;
            this.translateY = be.translateY;
            this.translateZ = be.translateZ;
            this.rotateX = (float) Math.toDegrees(be.rotateX);
            this.rotateY = (float) Math.toDegrees(be.rotateY);
            this.rotateZ = (float) Math.toDegrees(be.rotateZ);
            this.configs = be.getConfigs();
        } else {
            this.translateX = this.translateY = this.translateZ = 0f;
            this.rotateX = this.rotateY = this.rotateZ = 0f;
            this.configs = List.of();
        }
    }

    @Override
    protected void buildFixedWidgets() {
        addInputModeToggle(getPanelLeft(), 34, getColumnsWidth(), 20);
        closeButton = addFixedWidget(ComponentHelper.button(getPanelLeft(), height - 30, getColumnsWidth(), 20,
                ComponentHelper.translatable("ui.fangsu.block.close_and_save"), button -> onClose()));
    }

    @Override
    protected void init() {
        if (be != null) configs = be.getConfigs();
        super.init();
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        resetNumericRows();
        if (be == null) {
            addEntry(createTextLabel(getPanelLeft(), layout.y, ComponentHelper.literal("No block entity"),
                    TextLabel.Align.LEFT, 0xFFFFFF, false), layout.y);
            return;
        }
        int y = buildModelButtons(layout.y);
        y = buildTransformRows(y);
        y = buildCollisionButtons(y + GAP);
        buildCustomConfigs(y + GAP);
    }

    private int buildModelButtons(int y) {
        boolean hasMainModel = CustomItems.items.get(be.getMainModelKey()) != null;
        List<SubModelDispInfo> infos = be.getSubModelInfos();
        if (!hasMainModel && (infos == null || infos.isEmpty())) return y;

        y = addHeading(y, "ui.fangsu.block.modelSelect");
        TwoColumnLayout grid = new TwoColumnLayout(y);
        if (hasMainModel) {
            addGridButton(grid, null, ComponentHelper.translatable("ui.fangsu.block.mainModelSelect"), button ->
                    Minecraft.getInstance().setScreen(new ModelSelectScreen(
                            ComponentHelper.translatable("ui.fangsu.block.mainModelSelect"), be,
                            CustomItems.items.get(be.getMainModelKey()), target -> target.mainModel,
                            (target, value) -> target.mainModel = value, this, () -> {
                                be.setDefaultSubModel();
                                be.afterChangeModel();
                            })));
        }
        if (infos != null) {
            for (SubModelDispInfo info : infos) {
                Button.OnPress action;
                if (info instanceof SubModelMethodInfo method) {
                    action = button -> method.getAction().run();
                } else {
                    action = button -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                            info.name(), be, info.infos(), info.initialGetter(), info.setter(), this, be::afterChangeModel));
                }
                addGridButton(grid, null, info.name(), action);
            }
        }
        return grid.bottom() + GAP;
    }

    private int buildTransformRows(int y) {
        int left = getPanelLeft(), right = getRightColumnX(), width = getColumnWidth();
        addEntry(createTextLabel(left + width / 2, y, ComponentHelper.translatable("ui.fangsu.block.translate"),
                TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        addEntry(createTextLabel(right + width / 2, y, ComponentHelper.translatable("ui.fangsu.block.rotate"),
                TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        int leftY = y + 12, rightY = y + 12;
        leftY = addAxisRow(left, leftY, width, ComponentHelper.translatable("ui.fangsu.block.transX"),
                translateX, -1, 1, 0.0625f, value -> translateX = value, () -> translateX, this::sendToServer);
        rightY = addAxisRow(right, rightY, width, ComponentHelper.translatable("ui.fangsu.block.rotX"),
                rotateX, -180, 180, 5, value -> rotateX = value, () -> rotateX, this::sendToServer);
        leftY = addAxisRow(left, leftY, width, ComponentHelper.translatable("ui.fangsu.block.transY"),
                translateY, -1, 1, 0.0625f, value -> translateY = value, () -> translateY, this::sendToServer);
        rightY = addAxisRow(right, rightY, width, ComponentHelper.translatable("ui.fangsu.block.rotY"),
                rotateY, -180, 180, 5, value -> rotateY = value, () -> rotateY, this::sendToServer);
        leftY = addAxisRow(left, leftY, width, ComponentHelper.translatable("ui.fangsu.block.transZ"),
                translateZ, -1, 1, 0.0625f, value -> translateZ = value, () -> translateZ, this::sendToServer);
        rightY = addAxisRow(right, rightY, width, ComponentHelper.translatable("ui.fangsu.block.rotZ"),
                rotateZ, -180, 180, 5, value -> rotateZ = value, () -> rotateZ, this::sendToServer);
        return Math.max(leftY, rightY);
    }

    private int buildCollisionButtons(int y) {
        TwoColumnLayout grid = new TwoColumnLayout(y);
        addGridButton(grid, ComponentHelper.translatable("ui.fangsu.collision.mode"),
                ComponentHelper.translatable("ui.fangsu.collision." + be.collisionMode()), button -> {
                    String mode = switch (be.collisionMode()) {
                        case "auto" -> "simple";
                        case "simple" -> "manual";
                        default -> "auto";
                    };
                    be.setExtraConfig(FunctionalObjBlockEntity.COLLISION_MODE, mode);
                    be.sendUpdateC2S();
                    requestRebuild();
                });
        double currentStep = be.collisionStep();
        Component precision = currentStep == 0 ? ComponentHelper.translatable("ui.fangsu.collision.inherit")
                : ComponentHelper.literal(Double.toString(currentStep));
        addGridButton(grid, ComponentHelper.translatable("ui.fangsu.collision.step"), precision, button -> {
            double next = currentStep == 0 ? 0.25 : currentStep == 0.25 ? 0.1 : currentStep == 0.1 ? 0.05 : 0;
            be.setExtraConfig(FunctionalObjBlockEntity.COLLISION_STEP, Double.toString(next));
            be.sendUpdateC2S();
            requestRebuild();
        });
        addGridButton(grid, null, ComponentHelper.translatable("ui.fangsu.collision.rebuild"), button -> {
            if ("simple".equals(be.collisionMode())) be.setExtraConfig(FunctionalObjBlockEntity.COLLISION_MODE, "manual");
            be.rebuildCollision();
            requestRebuild();
        });
        return grid.bottom();
    }

    private void buildCustomConfigs(int y) {
        if (configs == null || configs.isEmpty()) return;
        y = addHeading(y, "ui.fangsu.block.extras");
        TwoColumnLayout grid = new TwoColumnLayout(y);
        for (ConfigEntry<?> config : configs) {
            config.load(be);
            config.setChangeListener(entry -> {
                if (entry.isSaveOnChange()) {
                    entry.save(be);
                    be.sendUpdateC2S();
                    requestRebuild();
                }
            });
            if (!config.isVisible()) continue;
            int widgetY = addWrappedText(grid.x(), grid.y(), getColumnWidth(), config.title, 0xFFFFFF);
            ConfigWidget widget = config.createWidget(grid.x(), widgetY, 0, getColumnWidth());
            addRenderableWidget(widget);
            addEntry(widget, widgetY);
            grid.next(widgetY + widget.getHeight());
        }
    }

    private int addHeading(int y, String key) {
        addEntry(createTextLabel(getPanelLeft() + getColumnsWidth() / 2, y, ComponentHelper.translatable(key),
                TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        return y + 12;
    }

    private void addGridButton(TwoColumnLayout grid, Component heading, Component label, Button.OnPress action) {
        int y = grid.y();
        if (heading != null) y = addWrappedText(grid.x(), y, getColumnWidth(), heading, 0xFFFFFF);
        addEntry(addButton(grid.x(), y, getColumnWidth(), 20, label, action), y);
        grid.next(y + 20);
    }

    /** 从左到右、再换行；使用本行较高控件的底边，避免不同高度的配置项重叠。 */
    private final class TwoColumnLayout {
        private int rowY, rowBottom, column;
        private TwoColumnLayout(int y) { rowY = rowBottom = y; }
        private int x() { return column == 0 ? getPanelLeft() : getRightColumnX(); }
        private int y() { return rowY; }
        private void next(int bottom) {
            rowBottom = Math.max(rowBottom, bottom);
            if (++column == 2) {
                rowY = rowBottom + GAP;
                rowBottom = rowY;
                column = 0;
            }
        }
        private int bottom() { return column == 0 ? rowY : rowBottom + GAP; }
    }

    @Override
    protected int getPanelLeft() { return GAP; }

    @Override
    protected int getPanelRight() {
        int originalWidth = this.width / 5 - GAP * 2;
        int panelWidth = Math.max(Math.round(originalWidth * PANEL_WIDTH_FACTOR), MIN_COLUMN_WIDTH * 2 + COLUMN_GAP);
        return getPanelLeft() + panelWidth;
    }

    @Override
    protected int getPanelTop() { return 30; }

    @Override
    protected int getPanelBottom() { return height - 30; }

    @Override
    protected int getContentTop() { return 58; }

    @Override
    protected int getContentBottom() { return height - 42; }

    @Override
    protected int getContentLeft() { return getPanelLeft(); }

    @Override
    protected int getContentRight() { return getPanelRight(); }

    private int getColumnWidth() { return (getPanelRight() - getPanelLeft() - COLUMN_GAP) / 2; }
    private int getRightColumnX() { return getPanelLeft() + getColumnWidth() + COLUMN_GAP; }
    private int getColumnsWidth() { return getColumnWidth() * 2 + COLUMN_GAP; }

    @Override
    protected void renderPanelBackground(GraphicContext graphics) {
        graphics.fill(0, 0, getPanelRight() + GAP, height, 0xFF000000);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext context = GraphicContext.of(graphics);
    //#else
    //$$ @Override
    //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     GraphicContext context = GraphicContext.of(poseStack);
    //#endif
        int titleX = (getPanelLeft() + getPanelRight()) / 2 - font.width(title.getString()) / 2;
        context.drawString(font, title, titleX, 2, 0xFFFFFF, false);
        super.render(context.asMinecraft(), mouseX, mouseY, partialTick);
    }

    private void sendToServer() {
        if (be == null) return;
        be.translateX = translateX;
        be.translateY = translateY;
        be.translateZ = translateZ;
        be.rotateX = (float) Math.toRadians(rotateX);
        be.rotateY = (float) Math.toRadians(rotateY);
        be.rotateZ = (float) Math.toRadians(rotateZ);
        be.sendUpdateC2S();
    }

    @Override
    public void onClose() {
        commitAllNumericFields();
        if (be != null) {
            if (configs != null) {
                for (ConfigEntry<?> config : configs) config.save(be);
            }
            sendToServer();
        }
        super.onClose();
    }
}
