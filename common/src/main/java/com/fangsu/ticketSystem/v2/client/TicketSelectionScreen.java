package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ui.BaseSelectionScreen;
import com.fangsu.ui.ConfigChoiceScreen;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;

/** 复用既有“可选 / 已选”双栏控件，选择时只修改管理草稿。 */
public final class TicketSelectionScreen extends BaseSelectionScreen {
    private final Screen parent;
    private final Consumer<Set<String>> setter;
    public TicketSelectionScreen(Screen parent,String title,List<ConfigChoiceScreen.Choice> choices,Set<String> initial,int maximum,Consumer<Set<String>> setter) {
        super(ComponentHelper.literal(title),1,maximum);this.parent=parent;this.setter=setter;
        titles=List.of(ComponentHelper.literal("可选项目"),ComponentHelper.literal("已选项目（点击移除）"));
        List<SelectionItem> options=choices.stream().map(c->new SelectionItem(c.label(),c.value(),null)).toList();
        items=List.of(options);for(SelectionItem item:options)if(initial.contains(item.value()))selectedItems.add(item);
    }
    @Override public void updateColumn() { }
    @Override public void onClose() {
        Set<String> values=new LinkedHashSet<>();for(SelectionItem item:selectedItems)values.add(item.value());
        setter.accept(values);Minecraft.getInstance().setScreen(parent);
    }
}
