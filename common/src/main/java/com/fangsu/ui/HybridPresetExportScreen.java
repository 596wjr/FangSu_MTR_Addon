package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 「导出预设」屏幕：输入预设名称，导出到游戏目录 hybrid_creator/ 文件夹
 * （subDir 非空时导出到其子目录，如方案预设 hybrid_creator/schemes/）。
 * 文件名做安全过滤（见 {@link HybridCreatorJsonIO#write(CompoundTag, String, String)}）。
 */
public class HybridPresetExportScreen extends Screen {

    private final Screen parent;
    private final CompoundTag tasksTag;
    private final String subDir;
    private EditBox nameField;

    public HybridPresetExportScreen(Screen parent, CompoundTag tasksTag) {
        this(parent, tasksTag, null);
    }

    public HybridPresetExportScreen(Screen parent, CompoundTag tasksTag, String subDir) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.title"));
        this.parent = parent;
        this.tasksTag = tasksTag;
        this.subDir = subDir;
    }

    @Override
    protected void init() {
        nameField = new EditBox(minecraft.font, (width - 200) / 2, 64, 200, 20, ComponentHelper.empty());
        nameField.setMaxLength(48);
        //#if MC_VERSION >= 11903
        nameField.setHint(ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.name_hint"));
        //#else
        //$$ nameField.setSuggestion(ComponentHelper.translatableString("ui.fangsu.hybrid_creator.export_preset.name_hint"));
        //#endif
        addRenderableWidget(nameField);
        addRenderableWidget(ComponentHelper.button((width - 220) / 2, height - 60, 100, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.export"), button -> exportPreset()));
        addRenderableWidget(ComponentHelper.button((width + 20) / 2, height - 60, 100, 20,
                ComponentHelper.translatable("ui.fangsu.block.cancel"), button -> minecraft.setScreen(parent)));
        setInitialFocus(nameField);
    }

    private void exportPreset() {
        try {
            HybridCreatorJsonIO.write(tasksTag, nameField.getValue(), subDir);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(ComponentHelper.translatable(
                        "msg.fangsu.hybrid_creator.export_preset_success", nameField.getValue()), true);
            }
            minecraft.setScreen(parent);
        } catch (java.io.IOException e) {
            com.fangsu.Main.LOGGER.error("[HybridCreator] 导出预设失败", e);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.io_error"), true);
            }
        }
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //#if MC_VERSION < 12003
        renderBackground(graphics);
        //#else
        //$$ renderBackground(graphics, mouseX, mouseY, partialTick);
        //#endif
        super.render(graphics, mouseX, mouseY, partialTick);
        final GraphicContext g = GraphicContext.of(graphics);
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.title").getString(), width / 2, 26, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.hint").getString(), width / 2, 44, 0xFFAAAAAA);
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(poseStack);
    //$$     super.render(poseStack, mouseX, mouseY, partialTick);
    //$$     final GraphicContext g = GraphicContext.of(poseStack);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.title").getString(), width / 2, 26, 0xFFFFFFFF);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset.hint").getString(), width / 2, 44, 0xFFAAAAAA);
    //$$ }
    //#endif
}
