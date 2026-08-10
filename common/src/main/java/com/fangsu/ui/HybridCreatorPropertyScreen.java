package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * 单格方块属性循环编辑（照 ANTE newPropertyScreen 移植到 BasicConfigScreen）。
 * <p>
 * 顶部为「替换模式」开关按钮，下方为方块的全部属性（台阶的 half 属性自然包含其中，
 * 用户在此切换上半砖/下半砖），点击按钮循环取值。关闭时写回格子并同步 NBT。
 */
public class HybridCreatorPropertyScreen extends BasicConfigScreen {

    private final HybridSliceTaskScreen parent;
    private final HybridSliceTaskScreen.Square square;
    /** 编辑中的状态（不可变对象，setValue 产生新实例） */
    private net.minecraft.world.level.block.state.BlockState baseState;
    private boolean replacement;

    public HybridCreatorPropertyScreen(HybridSliceTaskScreen parent, HybridSliceTaskScreen.Square square) {
        super(square.state.getBlock().getName());
        this.parent = parent;
        this.square = square;
        this.baseState = square.state;
        this.replacement = square.replacement;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        layout.y += 12;

        // 替换模式开关
        final TextLabel replLabel = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.replacement"), TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(replLabel, layout.y);
        addEntry(addButton(layout.areaLeft + layout.labelWidth, layout.y, layout.fieldWidth, 20, ComponentHelper.literal(String.valueOf(replacement)), btn -> {
            replacement = !replacement;
            requestRebuild();
        }), layout.y);
        layout.y += 26;

        // 属性循环。注意：通配符 Property<?> 只能作为参数单次传给泛型 helper——
        // javac 对同一表达式的每次方法调用分别捕获，直接内联使用会因捕获变量
        // 不一致而编译失败（ANTE newButtonCycleListEntry 同款模式）
        for (Property<?> property : baseState.getBlock().getStateDefinition().getProperties()) {
            addPropertyEntry(layout, property);
        }
    }

    private <T extends Comparable<T>> void addPropertyEntry(ContentLayout layout, Property<T> property) {
        final List<String> values = new ArrayList<>();
        for (T value : property.getPossibleValues()) {
            values.add(property.getName(value));
        }
        final TextLabel label = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.literal(property.getName()), TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(label, layout.y);
        // 读取当前值用 baseState.getValue(property)：1.20.1 的 Property 没有 getValue(BlockState)
        addEntry(addButton(layout.areaLeft + layout.labelWidth, layout.y, layout.fieldWidth, 20, ComponentHelper.literal(property.getName(baseState.getValue(property))), btn -> {
            final int next = (values.indexOf(property.getName(baseState.getValue(property))) + 1) % values.size();
            baseState = baseState.setValue(property, property.getValue(values.get(next)).get());
            requestRebuild();
        }), layout.y);
        layout.y += 26;
    }

    @Override
    public void onClose() {
        square.state = baseState;
        square.replacement = replacement;
        parent.onPropertySaved(square);
        minecraft.setScreen(parent);
    }
}
