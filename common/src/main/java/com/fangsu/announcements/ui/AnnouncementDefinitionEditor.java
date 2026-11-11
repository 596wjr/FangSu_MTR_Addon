package com.fangsu.announcements.ui;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.extraConfig.MultiLineTextWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ThemedConfigScreen;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 本地定义 JSON 编辑器；只保存本地覆盖，不改资源包和服务器。 */
public final class AnnouncementDefinitionEditor extends ThemedConfigScreen {
    private final Screen parent;
    private final String recordingFamily;
    private String draft = "", status = "ui.fangsu.ann.editor_hint";
    private boolean loaded;
    public AnnouncementDefinitionEditor(Screen parent) { this(parent, null); }
    public AnnouncementDefinitionEditor(Screen parent, String family) {
        super(ComponentHelper.translatable(family == null ? "ui.fangsu.ann.editor" : "ui.fangsu.ann.recording_editor")); this.parent = parent; recordingFamily = family;
        if (family != null) status = "ui.fangsu.ann.recording_editor_hint";
        (family == null ? AnnouncementClient.localDefinitions() : AnnouncementClient.localRecording(family)).whenComplete((json, error) -> Minecraft.getInstance().execute(() -> {
            loaded = error == null; if (loaded) draft = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            else status = "ui.fangsu.ann.failed"; requestRebuild();
        }));
    }
    @Override protected void buildFixedWidgets() {
        int x = getPanelLeft(), w = getPanelRight()-x;
        addFixedWidget(themedButton(x, height-40, w/2-2, 20, ComponentHelper.translatable("ui.fangsu.config.apply"), b -> save()));
        closeButton = addFixedWidget(themedButton(x+w/2+2,height-40,w/2-2,20,ComponentHelper.translatable("gui.back"), b -> onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout) {
        int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x555555)+8;
        MultiLineTextWidget editor = new MultiLineTextWidget(x,y,w,Math.max(120,height-y-65),draft,value -> draft=value);
        addRenderableWidget(editor);addEntry(editor,y);
    }
    private void save() {
        if (!loaded || draft.length() > 1024*1024) return;
        try {
            JsonObject json=JsonParser.parseString(draft).getAsJsonObject();
            (recordingFamily == null ? AnnouncementClient.saveOverride(json) : AnnouncementClient.saveRecording(recordingFamily, json)).thenAccept(success -> Minecraft.getInstance().execute(() -> {
                status=success ? "ui.fangsu.config.applied" : "ui.fangsu.ann.failed";requestRebuild();
            }));
        } catch (RuntimeException e) {status="ui.fangsu.ann.failed";requestRebuild();}
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
}
