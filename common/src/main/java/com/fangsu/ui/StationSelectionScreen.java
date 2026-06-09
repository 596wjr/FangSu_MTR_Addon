package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.MtrUtil;
import mtr.client.ClientData;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class StationSelectionScreen extends BaseSelectionScreen {
    private final Consumer<List<Long>> setter;
    private final BlockPos pos;
    private final Screen parent;

    public StationSelectionScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect, Screen parent) {
        super(component, 1, maxSelect);
        this.setter = setter;
        this.pos = pos;
        this.titles = new ArrayList<>();
        this.parent = parent;
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selectStn"));
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selected"));
    }

    @Override
    public void updateColumn() {
        var raw = new ArrayList<>(ClientData.STATIONS);
        if (this.items != null)
            this.items.clear();
        else this.items = new ArrayList<>();
        List<SelectionItem> items = new ArrayList<>();

        // 鎸夎窛绂讳粠杩戝埌杩滄帓锟?
        double px = pos.getX(), pz = pos.getZ();
        raw.sort(Comparator.comparingDouble(station -> {
            double sx = (station.corner1.getA() + station.corner2.getA()) / 2.0;
            double sz = (station.corner1.getB() + station.corner2.getB()) / 2.0;
            double dx = sx - px, dz = sz - pz;
            return dx * dx + dz * dz;
        }));

        for (Station station : raw) {
            items.add(new SelectionItem(
                    station.name,
                    Long.toString(station.id),
                    station.color
            ));
        }
        this.items.add(items);
    }

    @Override
    public void onClose() {
        List<Long> v = new ArrayList<>();
        for (SelectionItem item : this.selectedItems) {
            v.add(Long.decode(item.value()));
        }
        this.setter.accept(v);
        Minecraft.getInstance().setScreen(parent);
    }
}
