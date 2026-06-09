package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.MtrUtil;
import mtr.data.Platform;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PlatformSelectionScreen extends BaseSelectionScreen {
    private final Consumer<List<Long>> setter;
    private final BlockPos pos;
    private final Screen parent;

    public PlatformSelectionScreen(Component component, List<Long> defaultValue, Consumer<List<Long>> setter, BlockPos pos, int maxSelect, Screen parent) {
        super(component, 1, maxSelect);
        this.setter = setter;
        this.pos = pos;
        this.titles = new ArrayList<>();
        this.parent = parent;
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selectPlat"));
        titles.add(ComponentHelper.translatable("ui.fangsu.common.selected"));
    }

    @Override
    public void updateColumn() {
        Station station = MtrUtil.getStationAt(MtrUtil.getCenterVector3f(pos));
        if (station != null) {
            if (this.items != null)
                this.items.clear();
            else this.items = new ArrayList<>();
            Set<SelectionItem> itemSet = new HashSet<>();
            List<Platform> platforms = MtrUtil.getPlatformByStation(station);
            if (platforms != null)
                for (Platform p : platforms) {
                    String dest = MtrUtil.getDestinationByPlatform(p);
                    itemSet.add(new SelectionItem(
                            p.name + " -> " + dest,
                            String.valueOf(p.id),
                            null
                    ));
                }
            List<SelectionItem> items = new ArrayList<>(itemSet);
            this.items.add(items);
        }
    }

    @Override
    public void onClose() {
        List<Long> v = new ArrayList<Long>();
        for (SelectionItem item : this.selectedItems) {
            v.add(Long.decode(item.value()));
        }
        this.setter.accept(v);
        Minecraft.getInstance().setScreen(parent);
    }
}
