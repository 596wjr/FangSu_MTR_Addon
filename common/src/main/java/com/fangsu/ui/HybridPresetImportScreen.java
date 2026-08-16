package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「导入预设」屏幕：列出游戏目录 hybrid_creator/ 文件夹下全部 JSON 预设（新的在前），
 * 点选一个导入。subDir 非空时列出其子目录（如方案预设 hybrid_creator/schemes/）。
 * 文件多时支持滚轮滚动。
 */
public class HybridPresetImportScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int LIST_TOP = 56;
    private static final int LIST_BOTTOM_MARGIN = 70;

    private final Screen parent;
    private final Consumer<CompoundTag> onImport;
    private final String subDir;
    private final List<String> files = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private int scroll = 0;

    public HybridPresetImportScreen(Screen parent, Consumer<CompoundTag> onImport) {
        this(parent, onImport, null);
    }

    public HybridPresetImportScreen(Screen parent, Consumer<CompoundTag> onImport, String subDir) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.import_preset.title"));
        this.parent = parent;
        this.onImport = onImport;
        this.subDir = subDir;
    }

    @Override
    protected void init() {
        files.clear();
        buttons.clear();
        try {
            files.addAll(HybridCreatorJsonIO.listJsonFiles(subDir));
        } catch (IOException e) {
            com.fangsu.Main.LOGGER.error("[HybridCreator] 读取预设列表失败", e);
        }
        for (int i = 0; i < files.size(); i++) {
            final String fileName = files.get(i);
            final Button button = ComponentHelper.button(40, LIST_TOP + i * ROW_H, Math.min(360, width - 80), 18,
                    ComponentHelper.literal(fileName), btn -> importPreset(fileName));
            buttons.add(button);
            addRenderableWidget(button);
        }
        addRenderableWidget(ComponentHelper.button(width / 2 - 50, height - 40, 100, 20,
                ComponentHelper.translatable("ui.fangsu.block.cancel"), button -> minecraft.setScreen(parent)));
    }

    private void importPreset(String fileName) {
        try {
            final CompoundTag tasksTag = HybridCreatorJsonIO.read(fileName, subDir);
            if (tasksTag == null) {
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.import_fail"), true);
                }
                return;
            }
            onImport.accept(tasksTag);
            // 回调已自行切换屏幕（如弹出确认对话框）：后续流程由回调负责，跳过默认的
            // 成功提示与返回（minecraft.setScreen 立即生效，此处 screen 已不是本屏）
            if (minecraft.screen != this) return;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(ComponentHelper.translatable(
                        "msg.fangsu.hybrid_creator.import_preset_success", fileName), true);
            }
            minecraft.setScreen(parent);
        } catch (IOException e) {
            com.fangsu.Main.LOGGER.error("[HybridCreator] 导入预设失败", e);
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
        g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import_preset.title").getString(), width / 2, 22, 0xFFFFFFFF);
        if (files.isEmpty()) {
            g.drawCenteredString(minecraft.font, ComponentHelper.translatable("msg.fangsu.hybrid_creator.import_fail").getString(), width / 2, height / 2 - 10, 0xFFAAAAAA);
            return;
        }
        // 裁切列表区并应用滚动
        final int bottom = height - LIST_BOTTOM_MARGIN;
        g.enableScissor(20, LIST_TOP, width - 20, bottom);
        final int listHeight = bottom - LIST_TOP;
        final int maxScroll = files.size() * ROW_H - listHeight;
        if (scroll > 0) scroll = 0;
        if (scroll < -maxScroll && maxScroll > 0) scroll = -maxScroll;
        for (int i = 0; i < buttons.size(); i++) {
            final Button button = buttons.get(i);
            final int y = LIST_TOP + i * ROW_H + scroll;
            if (y + ROW_H < LIST_TOP || y > bottom) continue; // 不可见行跳过
            //#if MC_VERSION >= 11903
            button.setY(y);
            //#else
            //$$ button.y = y; // 1.19.2 及以下 AbstractWidget 无 setY，x/y 为 public 字段
            //#endif
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        g.disableScissor();
    }
    //#else
    //$$ @Override
    //$$ public void render(@NotNull com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$     renderBackground(poseStack);
    //$$     super.render(poseStack, mouseX, mouseY, partialTick);
    //$$     final GraphicContext g = GraphicContext.of(poseStack);
    //$$     g.drawCenteredString(minecraft.font, ComponentHelper.translatable("ui.fangsu.hybrid_creator.import_preset.title").getString(), width / 2, 22, 0xFFFFFFFF);
    //$$     if (files.isEmpty()) {
    //$$         g.drawCenteredString(minecraft.font, ComponentHelper.translatable("msg.fangsu.hybrid_creator.import_fail").getString(), width / 2, height / 2 - 10, 0xFFAAAAAA);
    //$$         return;
    //$$     }
    //$$     // 裁切列表区并应用滚动
    //$$     final int bottom = height - LIST_BOTTOM_MARGIN;
    //$$     g.enableScissor(20, LIST_TOP, width - 20, bottom);
    //$$     final int listHeight = bottom - LIST_TOP;
    //$$     final int maxScroll = files.size() * ROW_H - listHeight;
    //$$     if (scroll > 0) scroll = 0;
    //$$     if (scroll < -maxScroll && maxScroll > 0) scroll = -maxScroll;
    //$$     for (int i = 0; i < buttons.size(); i++) {
    //$$         final Button button = buttons.get(i);
    //$$         final int y = LIST_TOP + i * ROW_H + scroll;
    //$$         if (y + ROW_H < LIST_TOP || y > bottom) continue; // 不可见行跳过
    //$$         // 注意：//$$ 块内嵌套 #if 指令行不能带 //$$ 前缀，否则指令不生效（两分支都会输出）
    //#if MC_VERSION >= 11903
    //$$         button.setY(y);
    //#else
    //$$         button.y = y; // 1.19.2 及以下 AbstractWidget 无 setY，x/y 为 public 字段
    //#endif
    //$$         button.render(poseStack, mouseX, mouseY, partialTick);
    //$$     }
    //$$     g.disableScissor();
    //$$ }
    //#endif

    //#if MC_VERSION < 12003
    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (files.isEmpty()) return super.mouseScrolled(x, y, amount);
        final int listHeight = height - LIST_TOP - LIST_BOTTOM_MARGIN;
        final int maxScroll = files.size() * ROW_H - listHeight;
        if (maxScroll <= 0) return super.mouseScrolled(x, y, amount);
        scroll = Math.max(-maxScroll, Math.min(0, scroll + (int) (amount * 16)));
        return true;
    }
    //#else
    //$$ @Override
    //$$ public boolean mouseScrolled(double x, double y, double amount, double horizontalAmount) {
    //$$     if (files.isEmpty()) return super.mouseScrolled(x, y, amount, horizontalAmount);
    //$$     final int listHeight = height - LIST_TOP - LIST_BOTTOM_MARGIN;
    //$$     final int maxScroll = files.size() * ROW_H - listHeight;
    //$$     if (maxScroll <= 0) return super.mouseScrolled(x, y, amount, horizontalAmount);
    //$$     scroll = Math.max(-maxScroll, Math.min(0, scroll + (int) (amount * 16)));
    //$$     return true;
    //$$ }
    //#endif
}
