package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 混合方案条目的方块属性循环编辑（照 HybridCreatorPropertyScreen，去掉替换模式开关）。
 * 编辑结果通过 onSaved 回调写回方案条目（BlockState 不可变，setValue 产生新实例）。
 */
public class HybridSchemeEntryPropertyScreen extends BasicConfigScreen {

    private final Screen parent;
    private final Consumer<BlockState> onSaved;
    /** 编辑中的状态（不可变对象，setValue 产生新实例） */
    private BlockState baseState;

    public HybridSchemeEntryPropertyScreen(Screen parent, BlockState state, Consumer<BlockState> onSaved) {
        super(state.getBlock().getName());
        this.parent = parent;
        this.baseState = state;
        this.onSaved = onSaved;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        layout.y += 12;

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
        onSaved.accept(baseState);
        minecraft.setScreen(parent);
    }
}
