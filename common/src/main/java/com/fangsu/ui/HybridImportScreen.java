package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.extraConfig.MultiLineTextWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 「粘贴 JSON 导入」屏幕：多行输入框粘贴导出的 JSON 文本，解析成功后写回任务列表。
 * <p>
 * 解析成功回调 {@code onImport}（由调用方负责写 NBT 并刷新列表屏）；解析失败显示提示，
 * 停留在本屏供修改。
 */
public class HybridImportScreen extends Screen {

    private final Screen parent;
    private final Consumer<CompoundTag> onImport;
    private MultiLineTextWidget input;

    public HybridImportScreen(Screen parent, Consumer<CompoundTag> onImport) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.import.title"));
        this.parent = parent;
        this.onImport = onImport;
    }

    @Override
    protected void init() {
        final int boxW = Math.min(420, width - 80);
        // MultiLineTextWidget：1.19.3+ 用原版 MultiLineEditBox，1.18.2 回退到单行输入框包装（兼容旧版）
        input = new MultiLineTextWidget((width - boxW) / 2, 52, boxW, Math.min(150, height - 150), "", null);
        //#if MC_VERSION >= 11903
        input.setCharacterLimit(1024 * 64);
        //#endif
        addRenderableWidget(input);
        addRenderableWidget(ComponentHelper.button((width - 220) / 2, height - 60, 100, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.import"), button -> importText()));
        addRenderableWidget(ComponentHelper.button((width + 20) / 2, height - 60, 100, 20,
                ComponentHelper.translatable("ui.fangsu.block.cancel"), button -> minecraft.setScreen(parent)));
    }

    private void importText() {
        final CompoundTag tasksTag = HybridCreatorJsonIO.parse(input.getValue());
        if (tasksTag == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.invalid_json"), true);
            }
            return;
        }
        onImport.accept(tasksTag);
        minecraft.setScreen(parent);
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
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import.title").getString(), width / 2, 22, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import.hint").getString(), width / 2, 36, 0xFFAAAAAA);
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(poseStack);
    //$$     super.render(poseStack, mouseX, mouseY, partialTick);
    //$$     final GraphicContext g = GraphicContext.of(poseStack);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import.title").getString(), width / 2, 22, 0xFFFFFFFF);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import.hint").getString(), width / 2, 36, 0xFFAAAAAA);
    //$$ }
    //#endif

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+V 由 MultiLineEditBox 自身处理；Esc 返回
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
