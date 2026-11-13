package com.fangsu.announcements.ui;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.definition.AnnouncementRegistry;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ThemedConfigScreen;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** 每种语言独立选音色，远端 ID 与逻辑音色分开标明。 */
public final class LanguageVoiceScreen extends ThemedConfigScreen {
    private final Screen parent;
    private final String templateId, variantId;
    private final AnnouncementClient.Settings draft;
    private String status = "ui.fangsu.ann.language_voice_hint";
    public LanguageVoiceScreen(Screen parent, String template, String variant) {
        super(ComponentHelper.translatable("ui.fangsu.ann.language_voices")); this.parent=parent; templateId=template; variantId=variant;
        draft=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);
    }
    @Override protected void buildFixedWidgets() {
        int x=getPanelLeft(),w=getPanelRight()-x;
        addFixedWidget(themedButton(x,height-40,w/2-2,20,ComponentHelper.translatable("ui.fangsu.config.apply"),b->{
            try { var changed=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);changed.languageVoices=new HashMap<>(draft.languageVoices);changed.voiceMappings=new HashMap<>(draft.voiceMappings);AnnouncementClient.saveSettings(changed); status="ui.fangsu.config.applied"; } catch(Exception e){status="ui.fangsu.ann.failed";}
            requestRebuild();
        }));
        closeButton=addFixedWidget(themedButton(x+w/2+2,height-40,w/2-2,20,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout) {
        int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x555555)+8;
        var snapshot=AnnouncementRegistry.INSTANCE.snapshot(); var template=snapshot.templates().get(templateId); if(template==null)return;
        var variant=template.variants().stream().filter(v->v.id().equals(variantId)).findFirst().orElse(null); if(variant==null)return;
        for(String locale:new TreeSet<>(template.locales().keySet())) {
            String key=templateId+"/"+variantId+"/"+locale;
            var binding=variant.binding(locale); String selected=draft.languageVoices.getOrDefault(key,binding==null?"":binding.voice());
            List<String> voices=snapshot.voices().values().stream().filter(v->!v.mode().equals("reference_audio")||v.samples().containsKey(locale)).map(v->v.id()).sorted().toList();
            addEntry(addButton(x,y,w,20,ComponentHelper.literal(AnnouncementLabels.locale(locale)+": "+(selected.isEmpty()?"—":AnnouncementLabels.voice(selected))),b->{
                Minecraft.getInstance().setScreen(new com.fangsu.ui.ConfigChoiceScreen(this,"ui.fangsu.ann.language_voices",voices.stream().map(value->new com.fangsu.ui.ConfigChoiceScreen.Choice(value,AnnouncementLabels.voice(value))).toList(),value->{draft.languageVoices.put(key,value);requestRebuild();}));
            }),y);y+=22;
            if(!selected.isEmpty()&&(!draft.provider.equals("mimo")||!snapshot.voices().containsKey(selected)||!snapshot.voices().get(selected).mode().equals("reference_audio"))){
                String mapping=draft.provider+"/"+selected+"/"+locale;
                y=addWrappedText(x,y,w,ComponentHelper.translatable(draft.provider.equals("aliyun")?"ui.fangsu.menu.aliyun_remote_voice":"ui.fangsu.menu.remote_voice",draft.provider,locale),0x555555)+4;
                var box=new net.minecraft.client.gui.components.EditBox(font,x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.voice_mapping"));
                box.setMaxLength(256);box.setValue(draft.voiceMappings.getOrDefault(mapping,""));box.setResponder(value->{if(value.isBlank())draft.voiceMappings.remove(mapping);else draft.voiceMappings.put(mapping,value);});addRenderableWidget(box);addEntry(box,y);y+=22;
            }
            addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.restore_voice"),b->{draft.languageVoices.remove(key);requestRebuild();}),y);y+=24;
        }
    }
    @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
}
