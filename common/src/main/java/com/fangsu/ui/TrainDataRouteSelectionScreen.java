package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import mtr.client.ClientData;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class TrainDataRouteSelectionScreen extends BaseSelectionScreen {
    private final TrainDataSenderScreen parent;
    private final List<SelectionItem> routes;

    TrainDataRouteSelectionScreen(TrainDataSenderScreen parent, Set<Long> selectedRoutes) {
        super(ComponentHelper.translatable("ui.fangsu.train_data_sender.routes"), 1, Integer.MAX_VALUE);
        this.parent = parent;
        titles = List.of(ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                ComponentHelper.translatable("ui.fangsu.common.selected"));
        Map<Long, String> names = new TreeMap<>();
        ClientData.ROUTES.forEach(route -> names.put(route.id, route.name));
        selectedRoutes.forEach(id -> names.putIfAbsent(id, Long.toString(id)));
        routes = names.entrySet().stream()
                .map(entry -> new SelectionItem(entry.getValue(), Long.toString(entry.getKey()), null))
                .sorted(Comparator.comparing(SelectionItem::text, String.CASE_INSENSITIVE_ORDER)).toList();
        routes.stream().filter(item -> selectedRoutes.contains(Long.parseLong(item.value()))).forEach(selectedItems::add);
    }

    @Override
    public void updateColumn() { items = List.of(routes); }

    @Override
    public void onClose() {
        parent.setRoutes(selectedItems.stream().map(item -> Long.parseLong(item.value())).toList());
        Minecraft.getInstance().setScreen(parent);
    }
}
