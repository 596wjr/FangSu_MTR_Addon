package com.fangsu.ui;

import com.fangsu.blockEntities.FunctionalObjBlockEntity;
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

    private final FunctionalObjBlockEntity be;

    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;

    private List<ConfigEntry<?>> configs;
    private boolean useSliderInput = true;

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
        int left = getPanelLeft();
        int btnWidth = getPanelWidth();

        //#if MC_VERSION >= 11903
        Button toggleInputButton = Button.builder(getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }).bounds(left, 34, btnWidth, 20).build();
        //#else
        //$$ Button toggleInputButton = new Button(left, 34, btnWidth, 20, getInputToggleLabel(), btn -> { useSliderInput = !useSliderInput; requestRebuild(); });
        //#endif
        addFixedWidget(toggleInputButton);

        //#if MC_VERSION >= 11903
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
        // 濮ｅ繑顐奸崚婵嗩潗閸栨牗妞傞柌宥嗘煀閼惧嘲褰囬柊宥囩枂閸掓銆冮敍鍫滅伐婵″倷绮犲Ο鈥崇€烽柅澶嬪閻ｅ矂娼版潻鏂挎礀閸氬酣鍘ょ純顕€銆嶆导姘綁閸栨牭绱?
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

        // ---- 濡€崇€烽柅澶嬪 ----
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

        // ---- 楠炲磭些閿涘牏鎻ｉ崙鎴滆⒈鐞涘苯绔风仦鈧敓?----
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

        // ---- 閺冨娴嗛敍鍫㈡彛閸戞垳琚辩悰灞界鐏炩偓閿?----
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

        // ---- 妫版繂顦婚柊宥囩枂 ----
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
                // 缁楊兛绔寸悰宀嬬窗閸氬秶袨閿涘苯涔忕€靛綊缍堥敍宀€鐓妯哄
                addEntry(createTextLabel(colLeft, y, c.title, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
                y += 10;
                // 缁楊兛绨╃悰宀嬬窗閹貉傛閿涘本顒滅敮鎼佺彯鎼达讣绱檒abelWidth=0 閸樺娅庡锔挎櫠閺傚洦婀伴敍灞藉涧閻劋绗傞弬鐟版倳缁夌増鐖ｇ粵鎾呯礆
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
        return 58; // 閺嶅洭顣?閸掑洦宕查幐澶愭尦娑斿绗?
    }

    @Override
    protected int getContentBottom() {
        return this.height - 42; // 娣囨繂鐡ㄩ幐澶愭尦娑斿绗?
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
        // 鐏炲繐绠烽敓?1/5 婵夘偄鍘栫痪顖炵拨閼冲本娅欓敍鍫滅矤閺堚偓瀹革缚鏅跺鈧慨瀣剁礆
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
        // 閺嶅洭顣?- 缂佹ê鍩楅崷銊╃拨閼硅尪鍎楅弲顖欑瑐
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
     * 缁毖冨櫨娑撱倛顢戠敮鍐ㄧ湰閿涙矮绗傛稉鈧悰宀€鐓弽鍥╊劮閿涘奔绗呮稉鈧悰灞剧拨閿?
     *
     * @param compact 閿?true 閺冩儼顢戞妯绘纯缁毖冨櫨閿涘牆閽╅敓?閺冨娴嗛悽顭掔礆
     */
    private int addCompactTwoRow(int areaLeft, int y, int rowWidth,
                                 Component label, float value,
                                 float min, float max, float step,
                                 Consumer<Float> setter, Runnable onChanged,
                                 boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 缁楊兛绔寸悰宀嬬窗閸氬秶袨閿涘苯涔忕€靛綊缍堥敍宀€鐓妯哄
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 缁楊兛绨╃悰宀嬬窗濠婃垵娼￠敍灞绢劀鐢悂鐝敓?
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
     * 缁毖冨櫨娑撱倛顢戠敮鍐ㄧ湰閿涙矮绗傛稉鈧悰宀€鐓弽鍥╊劮閿涘奔绗呮稉鈧悰宀冪翻閸忋儲顢?
     */
    private int addAxisInputTwoRow(int areaLeft, int y, int rowWidth,
                                   Component label, float value,
                                   float min, float max, float step,
                                   Consumer<Float> setter, Runnable onChanged,
                                   boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 缁楊兛绔寸悰宀嬬窗閸氬秶袨閿涘苯涔忕€靛綊缍堥敍宀€鐓妯哄
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 缁楊兛绨╃悰宀嬬窗鏉堟挸鍙嗛敓?
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

