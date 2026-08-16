package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.data.hybrid.HybridScheme;
import com.fangsu.extraConfig.MultiLineTextWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 「粘贴 JSON 导入混合方案」屏幕：多行输入框粘贴方案导出的 JSON 文本，
 * 解析成功后回调 {@code onImport}（由调用方把方案加入任务列表）；解析失败
 * 显示提示（复用 invalid_json 文案），停留在本屏供修改。
 * 结构照 HybridImportScreen，解析函数换成 {@link HybridCreatorJsonIO#parseScheme}。
 */
public class HybridSchemeImportScreen extends Screen {

    private final Screen parent;
    private final Consumer<HybridScheme> onImport;
    private MultiLineTextWidget input;

    public HybridSchemeImportScreen(Screen parent, Consumer<HybridScheme> onImport) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import.title"));
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
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import"), button -> importText()));
        addRenderableWidget(ComponentHelper.button((width + 20) / 2, height - 60, 100, 20,
                ComponentHelper.translatable("ui.fangsu.block.cancel"), button -> minecraft.setScreen(parent)));
    }

    private void importText() {
        final HybridScheme scheme = HybridCreatorJsonIO.parseScheme(input.getValue());
        if (scheme == null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.invalid_json"), true);
            }
            return;
        }
        onImport.accept(scheme);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.scheme.import_success"), true);
        }
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
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import.title").getString(), width / 2, 22, 0xFFFFFFFF);
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import.hint").getString(), width / 2, 36, 0xFFAAAAAA);
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(poseStack);
    //$$     super.render(poseStack, mouseX, mouseY, partialTick);
    //$$     final GraphicContext g = GraphicContext.of(poseStack);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import.title").getString(), width / 2, 22, 0xFFFFFFFF);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.import.hint").getString(), width / 2, 36, 0xFFAAAAAA);
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
