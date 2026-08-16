package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridCreatorJsonIO;
import com.fangsu.data.hybrid.HybridScheme;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 混合方案编辑屏：方案名 + 条目列表（方块名 + 权重输入框 0..1000 + [属性] [删除]）
 * + 底部 [+ 添加方块] [导出]。
 * 所有修改原地改构建级 schemes 列表中的对象（画布格持引用索引，勿替换 list 实例）；
 * 每次修改经 onEdited 回调通知上层刷新方案面板并持久化 NBT。
 * 方案索引悬空（已被删除）时只显示红字提示，不渲染条目。
 */
public class HybridSchemeEditScreen extends BasicConfigScreen {

    /** 构建级混合方案列表（物品 NBT 顶层，所有任务共用） */
    private final List<HybridScheme> schemes;
    /** 方案在 schemes 中的索引；悬空时进入只读提示态 */
    private final int schemeIndex;
    private final Screen parent;
    private final Runnable onEdited;
    private EditBox nameField;

    public HybridSchemeEditScreen(List<HybridScheme> schemes, int schemeIndex, Screen parent, Runnable onEdited) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.edit"));
        this.schemes = schemes;
        this.schemeIndex = schemeIndex;
        this.parent = parent;
        this.onEdited = onEdited;
    }

    private HybridScheme scheme() {
        return schemeIndex >= 0 && schemeIndex < schemes.size() ? schemes.get(schemeIndex) : null;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        final HybridScheme scheme = scheme();
        if (scheme == null) {
            // 方案已被删除：只读红字提示
            addEntry(createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.invalid"), TextLabel.Align.LEFT, 0xFFFF5555, false), layout.y);
            return;
        }
        layout.y += 12;

        // 方案名（responder 即时写回）
        addEntry(createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.name"), TextLabel.Align.LEFT, 0xFFFFFF, false), layout.y);
        nameField = new EditBox(minecraft.font, layout.areaLeft + layout.labelWidth, layout.y - 3, layout.fieldWidth, 16, ComponentHelper.literal(""));
        nameField.setValue(scheme.name == null ? "" : scheme.name);
        //#if MC_VERSION >= 12003
        //$$ nameField.moveCursorToStart(true);
        //#else
        nameField.moveCursorToStart();
        //#endif
        nameField.setResponder(str -> {
            scheme.name = str;
            onEdited.run();
        });
        addRenderableWidget(nameField);
        addEntry(nameField, layout.y);
        layout.y += 26;

        // 条目行
        for (int i = 0; i < scheme.entries.size(); i++) {
            addEntryRow(layout, scheme, i);
        }
        if (scheme.entries.isEmpty()) {
            addEntry(createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.empty_entries"), TextLabel.Align.LEFT, 0xFFAAAAAA, false), layout.y);
            layout.y += 26;
        }

        // 底部操作行：[+ 添加方块]（半宽）+ [导出] [导出预设]（各 1/4）。
        // 导出 = 剪贴板 JSON；导出预设 = 存到游戏目录 hybrid_creator/ 文件夹
        layout.y += 4;
        addEntry(addButton(layout.areaLeft, layout.y, layout.contentWidth / 2 - 4, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.add_entry"),
                btn -> minecraft.setScreen(new HybridBlockPickerScreen(this, state -> {
                    final HybridScheme.SchemeEntry entry = new HybridScheme.SchemeEntry();
                    entry.blockState = state;
                    entry.weight = 1;
                    scheme.entries.add(entry);
                    onEdited.run();
                }))), layout.y);
        addEntry(addButton(layout.areaLeft + layout.contentWidth / 2 + 4, layout.y, layout.contentWidth / 4 - 4, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.export"),
                btn -> exportScheme(scheme)), layout.y);
        addEntry(addButton(layout.areaLeft + layout.contentWidth * 3 / 4 + 4, layout.y, layout.contentWidth / 4 - 4, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.export_preset"),
                btn -> minecraft.setScreen(new HybridPresetExportScreen(this, scheme.toCompoundTag(), HybridCreatorJsonIO.SCHEME_DIR))), layout.y);
    }

    private void addEntryRow(ContentLayout layout, HybridScheme scheme, int index) {
        final HybridScheme.SchemeEntry entry = scheme.entries.get(index);
        final boolean broken = entry.blockState == null;
        // 方块名（超宽截断）
        String blockName = broken
                ? ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.invalid_block").getString()
                : entry.blockState.getBlock().getName().getString();
        if (minecraft.font.width(blockName) > layout.labelWidth - 4) {
            blockName = minecraft.font.plainSubstrByWidth(blockName, layout.labelWidth - 7) + "…";
        }
        addEntry(createTextLabel(layout.areaLeft, layout.y, ComponentHelper.literal(blockName), TextLabel.Align.LEFT, broken ? 0xFFFF5555 : 0xFFFFFF, false), layout.y);
        // 权重输入框 0..1000（0 = 不参与抽选）：responder 解析整数并 clamp 写回
        final EditBox weightField = new EditBox(minecraft.font, layout.areaLeft + layout.labelWidth + 10, layout.y + 5, 60, 16, ComponentHelper.literal(""));
        weightField.setValue(String.valueOf(entry.weight));
        //#if MC_VERSION >= 12003
        //$$ weightField.moveCursorToStart(true);
        //#else
        weightField.moveCursorToStart();
        //#endif
        weightField.setResponder(str -> {
            try {
                final int v = Integer.parseInt(str);
                entry.weight = Math.max(0, Math.min(1000, v));
                onEdited.run();
            } catch (NumberFormatException ignored) {
                // 输入中/非法字符：不写回，保持上一次合法值
            }
        });
        addRenderableWidget(weightField);
        addEntry(weightField, layout.y + 5);
        // [属性]：仅有效方块可进（未知方块显示红字，跳过）
        addEntry(addButton(layout.areaLeft + layout.labelWidth + 80, layout.y, 56, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.properties"),
                btn -> {
                    if (entry.blockState != null) {
                        minecraft.setScreen(new HybridSchemeEntryPropertyScreen(this, entry.blockState, state -> {
                            entry.blockState = state;
                            onEdited.run();
                        }));
                    }
                }), layout.y);
        // [删除]
        addEntry(addButton(layout.areaLeft + layout.labelWidth + 140, layout.y, 56, 20,
                ComponentHelper.translatable("ui.fangsu.hybrid_creator.scheme.delete"),
                btn -> {
                    scheme.entries.remove(index);
                    onEdited.run();
                    requestRebuild();
                }), layout.y);
        layout.y += 26;
    }

    /** 导出方案 JSON 到剪贴板（可粘贴到其他任务/版本的方案导入框） */
    private void exportScheme(HybridScheme scheme) {
        minecraft.keyboardHandler.setClipboard(HybridCreatorJsonIO.schemeToJson(scheme));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.hybrid_creator.scheme.export_success"), true);
        }
    }

    @Override
    public void onClose() {
        onEdited.run();
        minecraft.setScreen(parent);
    }
}
