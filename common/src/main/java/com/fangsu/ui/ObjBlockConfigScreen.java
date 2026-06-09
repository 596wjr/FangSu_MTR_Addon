package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.customItem.CustomItems;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigWidget;
import com.fangsu.extraConfig.SliderWidget;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class ObjBlockConfigScreen extends BasicConfigScreen {

    private static final int GAP = 4;

    private final BaseObjBlockEntity be;

    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;

    private List<ConfigEntry<?>> configs;
    private boolean useSliderInput = true;

    public ObjBlockConfigScreen(BaseObjBlockEntity be) {
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
        int left = getPanelLeft();
        int btnWidth = getPanelWidth();

        //#if MC_VERSION >= 12000
        Button toggleInputButton = Button.builder(getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }).bounds(left, 34, btnWidth, 20).build();
        //#else
        //$$ Button toggleInputButton = new Button(left, 34, btnWidth, 20, getInputToggleLabel(), btn -> { useSliderInput = !useSliderInput; requestRebuild(); });
        //#endif
        addFixedWidget(toggleInputButton);

        //#if MC_VERSION >= 12000
        closeButton = addFixedWidget(Button.builder(ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> {
            sendToServer();
            onClose();
        }).bounds(left, this.height - 30, btnWidth, 20).build());
        //#else
        //$$ closeButton = addFixedWidget(new Button(left, this.height - 30, btnWidth, 20, ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> { sendToServer(); onClose(); }));
        //#endif
    }

    @Override
    protected void init() {
        // 姣忔鍒濆鍖栨椂閲嶆柊鑾峰彇閰嶇疆鍒楄〃锛堜緥濡備粠妯″瀷閫夋嫨鐣岄潰杩斿洖鍚庨厤缃」浼氬彉鍖栵級
        if (be != null) {
            configs = be.getConfigs();
        }
        super.init();
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int y = layout.y;
        int panelCenterX = (getPanelLeft() + getPanelRight()) / 2;
        if (be == null) {
            //#if MC_VERSION >= 12000
            addEntry(createTextLabel(panelCenterX, y, Component.literal("No block entity"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            //#else
            //$$ addEntry(createTextLabel(panelCenterX, y, ComponentHelper.literal("No block entity"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            //#endif
            return;
        }

        int colLeft = getPanelLeft();
        int colWidth = getPanelWidth();

        // ---- 妯″瀷閫夋嫨 ----
        addEntry(createTextLabel(panelCenterX, y, ComponentHelper.translatable("ui.fangsu.block.modelSelect"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;
        addEntry(addButton(colLeft, y, colWidth, 24, ComponentHelper.translatable("ui.fangsu.block.mainModelSelect"),
                (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                        ComponentHelper.translatable("ui.fangsu.block.mainModelSelect"),
                        this.be,
                        CustomItems.items.get(this.be.getMainModelKey()),
                        (target) -> target.mainModel,
                        (target, v) -> target.mainModel = v, this,
                        () -> {
                            be.setDefaultSubModel();
                            be.afterChangeModel();
                        }
                ))
        ), y);
        y += 28;

        if (be.getSubModelInfos() != null) {
            List<SubModelDispInfo> infos = be.getSubModelInfos();
            for (SubModelDispInfo info : infos) {
                Button.OnPress c;
                if (info instanceof SubModelMethodInfo m) {
                    c = (b) -> m.getAction().run();
                } else
                    c = (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                            info.name(), this.be, info.infos(), info.initialGetter(), info.setter(), this,
                            be::afterChangeModel
                    ));
                addEntry(addButton(colLeft, y, colWidth, 24, info.name(), c), y);
                y += 28;
            }
        }

        // ---- 骞崇Щ锛堢揣鍑戜袱琛屽竷灞€锟?----
        addEntry(createTextLabel(panelCenterX, y, ComponentHelper.translatable("ui.fangsu.block.translate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 10;
        if (useSliderInput) {
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transX"), translateX, -1, 1, 0.0625f,
                    v -> translateX = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transY"), translateY, -1, 1, 0.0625f,
                    v -> translateY = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transZ"), translateZ, -1, 1, 0.0625f,
                    v -> translateZ = v, this::sendToServer, true);
        } else {
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transX"), translateX, -1, 1, 0.0625f,
                    v -> translateX = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transY"), translateY, -1, 1, 0.0625f,
                    v -> translateY = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.transZ"), translateZ, -1, 1, 0.0625f,
                    v -> translateZ = v, this::sendToServer, true);
        }

        // ---- 鏃嬭浆锛堢揣鍑戜袱琛屽竷灞€锟?----
        addEntry(createTextLabel(panelCenterX, y, ComponentHelper.translatable("ui.fangsu.block.rotate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 10;
        if (useSliderInput) {
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotX"), rotateX, -180, 180, 5,
                    v -> rotateX = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotY"), rotateY, -180, 180, 5,
                    v -> rotateY = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotZ"), rotateZ, -180, 180, 5,
                    v -> rotateZ = v, this::sendToServer, true);
        } else {
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotX"), rotateX, -180, 180, 5,
                    v -> rotateX = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotY"), rotateY, -180, 180, 5,
                    v -> rotateY = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    ComponentHelper.translatable("ui.fangsu.block.rotZ"), rotateZ, -180, 180, 5,
                    v -> rotateZ = v, this::sendToServer, true);
        }

        // ---- 棰濆閰嶇疆 ----
        if (configs != null && !configs.isEmpty()) {
            addEntry(createTextLabel(panelCenterX, y, ComponentHelper.translatable("ui.fangsu.block.extras"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            y += 10;
            for (ConfigEntry<?> c : configs) {
                c.load(be);
                c.setChangeListener(entry -> {
                    if (entry.isSaveOnChange()) {
                        entry.save(be);
                        be.sendUpdateC2S();
                        requestRebuild();
                    }
                });
                if (!c.isVisible()) {
                    continue;
                }
                // 绗竴琛岋細鍚嶇О锛屽乏瀵归綈锛岀煯楂樺害
                addEntry(createTextLabel(colLeft, y, c.title, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
                y += 10;
                // 绗簩琛岋細鎺т欢锛屾甯搁珮搴︼紙labelWidth=0 鍘婚櫎宸︿晶鏂囨湰锛屽彧鐢ㄤ笂鏂瑰悕绉版爣绛撅級
                ConfigWidget w = c.createWidget(colLeft, y, 0, colWidth);
                addRenderableWidget(w);
                addEntry(w, y);
                y += w.getHeight() + 6;
            }
        }
    }

    @Override
    protected int getPanelLeft() {
        return GAP;
    }

    @Override
    protected int getPanelRight() {
        return this.width / 5 - GAP;
    }

    @Override
    protected int getPanelTop() {
        return 30;
    }

    @Override
    protected int getPanelBottom() {
        return this.height - 30;
    }

    @Override
    protected int getContentTop() {
        return 58; // 鏍囬+鍒囨崲鎸夐挳涔嬩笅
    }

    @Override
    protected int getContentBottom() {
        return this.height - 42; // 淇濆瓨鎸夐挳涔嬩笂
    }

    @Override
    protected int getContentLeft() {
        return getPanelLeft();
    }

    @Override
    protected int getContentRight() {
        return getPanelRight();
    }

    @Override
    protected void renderPanelBackground(GraphicContext g) {
        // 灞忓箷锟?1/5 濉厖绾粦鑳屾櫙锛堜粠鏈€宸︿晶寮€濮嬶級
        int bgRight = this.width / 5;
        g.fill(0, 0, bgRight, this.height, 0xFF000000);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(graphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //#endif
        // 鏍囬 - 缁樺埗鍦ㄩ粦鑹茶儗鏅笂
        int titleX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(this.title.getString()) / 2;
        int titleY = 2;
        g.drawString(this.font, this.title, titleX, titleY, 0xFFFFFF, false);

        super.render(g.asMinecraft(), mouseX, mouseY, partialTick);
    }

    private Component getInputToggleLabel() {
        return ComponentHelper.translatable(useSliderInput
                ? "ui.fangsu.block.toggle_input"
                : "ui.fangsu.block.toggle_slider");
    }

    private int getPanelWidth() {
        return getPanelRight() - getPanelLeft();
    }

    /**
     * 绱у噾涓よ甯冨眬锛氫笂涓€琛岀煯鏍囩锛屼笅涓€琛屾粦锟?
     *
     * @param compact 锟?true 鏃惰楂樻洿绱у噾锛堝钩锟?鏃嬭浆鐢級
     */
    private int addCompactTwoRow(int areaLeft, int y, int rowWidth,
                                 Component label, float value,
                                 float min, float max, float step,
                                 Consumer<Float> setter, Runnable onChanged,
                                 boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 绗竴琛岋細鍚嶇О锛屽乏瀵归綈锛岀煯楂樺害
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 绗簩琛岋細婊戝潡锛屾甯搁珮锟?
        int sliderWidth = rowWidth;
        if (sliderWidth < 60) sliderWidth = 60;
        SliderWidget slider = new SliderWidget(areaLeft, y, sliderWidth, 20,
                //#if MC_VERSION >= 12000
                Component.empty(), value, min, max, step,
                //#else
                //$$ ComponentHelper.empty(), value, min, max, step,
                //#endif
                v -> {
                    setter.accept(v);
                    onChanged.run();
                });
        this.addRenderableWidget(slider);
        addEntry(slider, y);
        return y + 20 + (compact ? 2 : 4);
    }

    /**
     * 绱у噾涓よ甯冨眬锛氫笂涓€琛岀煯鏍囩锛屼笅涓€琛岃緭鍏ユ
     */
    private int addAxisInputTwoRow(int areaLeft, int y, int rowWidth,
                                   Component label, float value,
                                   float min, float max, float step,
                                   Consumer<Float> setter, Runnable onChanged,
                                   boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 绗竴琛岋細鍚嶇О锛屽乏瀵归綈锛岀煯楂樺害
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 绗簩琛岋細杈撳叆锟?
        int inputWidth = Math.min(rowWidth, 80);
        //#if MC_VERSION >= 12000
        EditBox box = new EditBox(this.font, areaLeft, y, inputWidth, 20, Component.empty());
        //#else
        //$$ EditBox box = new EditBox(this.font, areaLeft, y, inputWidth, 20, ComponentHelper.empty());
        //#endif
        box.setValue(formatValue(value));
        box.setResponder(text -> {
            Float v = parseFloat(text);
            if (v == null) return;
            float snapped = snap(v, min, max, step);
            setter.accept(snapped);
            onChanged.run();
        });
        this.addRenderableWidget(box);
        addEntry(box, y);
        return y + 20 + (compact ? 2 : 4);
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
        if (be != null) {
            if (configs != null) {
                for (ConfigEntry<?> c : configs) {
                    c.save(be);
                }
            }
            be.sendUpdateC2S();
        }
        super.onClose();
    }
}
