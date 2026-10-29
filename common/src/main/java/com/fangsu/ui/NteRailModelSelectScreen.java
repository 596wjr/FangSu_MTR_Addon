package com.fangsu.ui;

import com.fangsu.customItem.NteRailModelSelection;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** 主轨道单选，附属独立开关；关闭时一次性交回 NTE 原来的保存回调。 */
public class NteRailModelSelectScreen extends BasicConfigScreen {
    private final Runnable returnToParent;
    private final Consumer<String> save;
    private final Map<String, Component> models;
    private String primary;
    private final Set<String> accessories = new LinkedHashSet<>();
    private boolean saved;

    public NteRailModelSelectScreen(Map<String, Component> models, String initial,
                                    Consumer<String> save, Runnable returnToParent) {
        super(ComponentHelper.translatable("ui.fangsu.nte_rail.title"));
        this.models = models;
        this.save = save;
        this.returnToParent = returnToParent;
        List<String> keys = NteRailModelSelection.decode(initial);
        primary = keys.get(0);
        if (NteRailModelSelection.isAccessory(primary)) {
            accessories.add(primary);
            primary = "null";
        }
        for (int i = 1; i < keys.size(); i++) accessories.add(keys.get(i));
        // 未安装或被资源包移除的模型仍显示 ID，避免打开界面就丢失选择。
        models.putIfAbsent(primary, ComponentHelper.literal(primary));
        for (String key : accessories) models.putIfAbsent(key, ComponentHelper.literal(key));
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        addHeading(layout, "ui.fangsu.nte_rail.primary");
        for (Map.Entry<String, Component> entry : models.entrySet()) {
            if (NteRailModelSelection.isAccessory(entry.getKey())) continue;
            String key = entry.getKey();
            addEntry(addButton(layout.areaLeft, layout.y, layout.areaRight - layout.areaLeft, 20,
                    ComponentHelper.literal(key.equals(primary) ? "[✓] " : "[ ] ").append(entry.getValue()), btn -> {
                        primary = key;
                        requestRebuild();
                    }), layout.y);
            layout.y += 24;
        }
        layout.y += 8;
        addHeading(layout, "ui.fangsu.nte_rail.accessories");
        for (Map.Entry<String, Component> entry : models.entrySet()) {
            if (!NteRailModelSelection.isAccessory(entry.getKey())) continue;
            String key = entry.getKey();
            addEntry(addButton(layout.areaLeft, layout.y, layout.areaRight - layout.areaLeft, 20,
                    ComponentHelper.literal(accessories.contains(key) ? "[✓] " : "[ ] ").append(entry.getValue()), btn -> {
                        if (!accessories.remove(key)) accessories.add(key);
                        requestRebuild();
                    }), layout.y);
            layout.y += 24;
        }
    }

    private void addHeading(ContentLayout layout, String key) {
        addEntry(createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable(key),
                TextLabel.Align.LEFT, 0x303030, true), layout.y);
        layout.y += 22;
    }

    @Override
    public void onClose() {
        if (saved) return;
        saved = true;
        save.accept(NteRailModelSelection.encode(primary, accessories));
        returnToParent.run();
    }
}
