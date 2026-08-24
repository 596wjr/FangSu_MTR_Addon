package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 出入口选择屏：列出所选车站的出入口（如 A1 / A2 / B1），支持多选。
 */
public class ExitSelectionScreen extends BaseSelectionScreen {
    private final Consumer<List<String>> setter;
    private final Station station;
    private final Screen parent;

    public ExitSelectionScreen(Component component, List<String> defaultValue, Consumer<List<String>> setter, Station station, int maxSelect, Screen parent) {
        super(component, 1, maxSelect);
        this.setter = setter;
        this.station = station;
        this.parent = parent;
        this.titles = new ArrayList<>();
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selectExit"));
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selected"));
    }

    @Override
    public void updateColumn() {
        if (this.items != null) this.items.clear();
        else this.items = new ArrayList<>();
        List<SelectionItem> items = new ArrayList<>();
        if (station != null && station.exits != null) {
            List<String> parents = new ArrayList<>(station.exits.keySet());
            parents.sort(Comparator.naturalOrder());
            for (String parent : parents) {
                List<String> dests = station.exits.get(parent);
                String text = parent;
                if (dests != null && !dests.isEmpty()) {
                    text = parent + " · " + String.join("/", dests);
                }
                items.add(new SelectionItem(text, parent, null));
            }
        }
        this.items.add(items);
    }

    @Override
    public void onClose() {
        List<String> v = new ArrayList<>();
        for (SelectionItem item : this.selectedItems) {
            v.add(item.value());
        }
        this.setter.accept(v);
        Minecraft.getInstance().setScreen(parent);
    }
}
