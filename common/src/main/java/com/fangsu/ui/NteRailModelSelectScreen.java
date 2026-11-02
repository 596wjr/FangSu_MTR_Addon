package com.fangsu.ui;

import com.fangsu.customItem.NteRailModelSelection;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** JSON 来源、轨道模型、已选模型三栏，主轨道单选，附属模型独立开关。 */
public class NteRailModelSelectScreen extends BaseSelectionScreen {
    private final Runnable returnToParent;
    private final Consumer<String> save;
    private final Map<String, Component> models;
    private final Map<String, List<NteRailModelCatalog.Model>> sources;
    private String primary;
    private final Set<String> accessories = new LinkedHashSet<>();
    private final Set<String> originalAccessories = new LinkedHashSet<>();
    private boolean saved;

    public NteRailModelSelectScreen(Map<String, Component> models, String initial,
                                    Consumer<String> save, Runnable returnToParent) {
        super(ComponentHelper.translatable("ui.fangsu.nte_rail.title"), 2, Integer.MAX_VALUE);
        this.models = new LinkedHashMap<>(models);
        this.save = save;
        this.returnToParent = returnToParent;
        List<String> keys = NteRailModelSelection.decode(initial);
        primary = keys.get(0);
        if (NteRailModelSelection.isAccessory(primary)) {
            accessories.add(primary);
            primary = "null";
        }
        for (int i = 1; i < keys.size(); i++) {
            String key = keys.get(i);
            if (!key.isEmpty() && !"null".equals(key)) accessories.add(key);
        }
        originalAccessories.addAll(accessories);
        this.models.putIfAbsent("", ComponentHelper.translatable("rail.mtrsteamloco.default"));
        // 特殊隐藏键始终可选，不依赖注册表是否公开它。
        this.models.put("null", ComponentHelper.translatable("ui.fangsu.nte_rail.hidden"));
        // 未安装或被资源包移除的模型仍显示 ID，避免打开界面就丢失选择。
        this.models.putIfAbsent(primary, ComponentHelper.literal(primary));
        for (String key : accessories) this.models.putIfAbsent(key, ComponentHelper.literal(key));
        sources = NteRailModelCatalog.load(this.models);
        titles = List.of(ComponentHelper.translatable("ui.fangsu.nte_rail.sources"),
                ComponentHelper.translatable("ui.fangsu.nte_rail.models"),
                ComponentHelper.translatable("ui.fangsu.common.selected"));
        // 初次进入定位到当前主模型所属文件；后续选择来源与搜索互不影响。
        for (Map.Entry<String, List<NteRailModelCatalog.Model>> source : sources.entrySet()) {
            if (source.getValue().stream().anyMatch(model -> model.key().equals(primary))) {
                selected.set(0, source.getKey());
                break;
            }
        }
        refreshSelected();
    }

    @Override
    public void updateColumn() {
        List<SelectionItem> files = new ArrayList<>();
        List<SelectionItem> choices = new ArrayList<>();
        for (Map.Entry<String, List<NteRailModelCatalog.Model>> source : sources.entrySet()) {
            StringBuilder search = new StringBuilder(source.getKey());
            for (NteRailModelCatalog.Model model : source.getValue()) {
                search.append(' ').append(model.key()).append(' ').append(model.name().getString());
                if (source.getKey().equals(selected.get(0))) choices.add(modelItem(model.key()));
            }
            files.add(new SelectionItem(NteRailModelCatalog.sourceName(source.getKey()), source.getKey(), null, search.toString()));
        }
        items = List.of(files, choices);
    }

    private SelectionItem modelItem(String key) {
        String name = models.get(key).getString();
        String role = ComponentHelper.translatableString(isAccessoryModel(key)
                ? "ui.fangsu.nte_rail.accessory_label" : "ui.fangsu.nte_rail.primary_label");
        return new SelectionItem(name + "\n" + role, key, null, name + " " + key + " " + role);
    }

    private boolean isAccessoryModel(String key) {
        return NteRailModelSelection.isAccessory(key) || originalAccessories.contains(key);
    }

    private void refreshSelected() {
        selectedItems.clear();
        selectedItems.add(modelItem(primary));
        for (String key : accessories) selectedItems.add(modelItem(key));
    }

    @Override
    protected void onItemActivated(int index, SelectionItem item) {
        if (index == 0) {
            super.onItemActivated(index, item);
            return;
        }
        String key = item.value();
        if (index == 1) {
            if (isAccessoryModel(key)) {
                if (!accessories.remove(key)) accessories.add(key);
            } else {
                primary = key;
            }
        } else if (index == 2) {
            if (!accessories.remove(key) && primary.equals(key)) primary = "null";
        }
        refreshSelected();
    }

    @Override
    protected boolean isItemSelected(int index, SelectionItem item) {
        return index == 1 ? primary.equals(item.value()) || accessories.contains(item.value()) : super.isItemSelected(index, item);
    }

    @Override
    public void onClose() {
        if (saved) return;
        saved = true;
        save.accept(NteRailModelSelection.encode(primary, accessories));
        returnToParent.run();
    }
}
