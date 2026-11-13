package com.fangsu.announcements.ui;

import com.fangsu.announcements.*;
import com.fangsu.announcements.cache.AudioSequence;
import com.fangsu.announcements.definition.*;
import com.fangsu.announcements.definition.AnnouncementDefinitions.*;
import com.fangsu.announcements.playback.CachedAudioPlayback;
import com.fangsu.announcements.provider.SynthesisService;
import com.fangsu.announcements.recordings.RecordingFamily;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.*;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.nio.file.Path;

/** 报站管理按用途分页，服务商和凭据有独立入口。 */
public final class AnnouncementCenterScreen extends ThemedConfigScreen {
    private final Screen parent;
    private String templateId="",locale="zh-CN",variant="",destination="",route="",status="ui.fangsu.menu.preview_hint";
    private AudioSequence current;
    private com.fangsu.announcements.provider.CloudFailure previewError;
    private record PreviewRequest(String template,String variant,String locale,String destination,String route,boolean force) { }
    private final Set<PreviewRequest> requests=new HashSet<>();
    private int page;
    private int taskRefreshTicks;
    private List<SynthesisService.Task> displayedTasks = List.of();
    private boolean showParts;
    private final AnnouncementClient.Settings onboardDraft;
    public AnnouncementCenterScreen(Screen parent){super(ComponentHelper.translatable("ui.fangsu.ann.title"));this.parent=parent;onboardDraft=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);}
    @Override protected int getContentTop(){return super.getContentTop()+24;}
    @Override protected void buildFixedWidgets(){int x=getPanelLeft(),w=getPanelRight()-x,tab=(w-64)/4;
        String[] tabs={"preview","onboard","tasks","advanced"};
        for(int i=0;i<tabs.length;i++){final int selected=i;var button=addFixedWidget(themedButton(x+i*(tab+4),getPanelTop()+4,tab,18,ComponentHelper.translatable("ui.fangsu.menu.tab_"+tabs[i]),b->{page=selected;scrollOffset=0;requestRebuild();}));selected(button,page==i);button.active=page!=i;}
        addFixedWidget(themedButton(x+w-48,getPanelTop()+4,48,18,ComponentHelper.translatable("ui.fangsu.ann.import_audio"),b->{CachedAudioPlayback.stopPreview();current=null;AudioLibraryScreen.chooseImport(this);}));
        addFixedWidget(themedButton(x,height-40,w/2-2,18,ComponentHelper.translatable("ui.fangsu.menu.provider"),b->Minecraft.getInstance().setScreen(new TtsProviderScreen(this))));
        closeButton=addFixedWidget(themedButton(x+w/2+2,height-40,w/2-2,18,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
        if(page==1){onboard(x,y,w);return;}if(page==2){tasks(x,y,w);return;}if(page==3){advanced(x,y,w);return;}
        y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x555555)+10;
        if (previewError != null)
            y = addWrappedText(x, y, w, TtsErrorText.describe(previewError.getMessage(), previewError), 0x555555) + 8;
        if(!requests.isEmpty())y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.generation_queue",requests.size()),0x555555)+6;
        Snapshot snapshot=AnnouncementRegistry.INSTANCE.snapshot();List<String> ids=new ArrayList<>(snapshot.templates().keySet());Collections.sort(ids);
        if(!ids.contains(templateId)){templateId=ids.isEmpty()?"":ids.get(0);variant="";current=null;}if(ids.isEmpty()){addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.missing"),0x555555);return;}
        Template template=snapshot.templates().get(templateId);if(variant.isEmpty())variant=template.defaultVariant();
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.template",AnnouncementLabels.template(templateId)),b->choose("ui.fangsu.menu.choose_template",ids,selected->{templateId=selected;variant="";current=null;})),y);y+=22;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.variant",AnnouncementLabels.variant(variant)),b->choose("ui.fangsu.menu.choose_variant",template.variants().stream().map(Variant::id).toList(),selected->{variant=selected;current=null;})),y);y+=22;
        List<String> languages=new ArrayList<>(template.locales().keySet());Collections.sort(languages);if(!languages.contains(locale))locale=languages.isEmpty()?"zh-CN":languages.get(0);
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.locale",AnnouncementLabels.locale(locale)),b->choose("ui.fangsu.menu.choose_locale",languages,selected->{locale=selected;current=null;})),y);y+=22;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.language_voices"),b->Minecraft.getInstance().setScreen(new LanguageVoiceScreen(this,templateId,variant))),y);y+=24;
        y=field(x,y,w,"ui.fangsu.ann.destination",destination,value->{destination=value;current=null;});
        if (template.requiredVariables().contains("route") || template.locales().getOrDefault(locale, List.of()).stream()
                .anyMatch(segment -> segment.text().contains("{route}"))) {
            y=field(x,y,w,"ui.fangsu.ann.preview_route",route,value->{route=value;current=null;});
        }
        if(Locale.forLanguageTag(locale).getLanguage().equals("en"))y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.station_name_hint"),0x555555)+8;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.preview_text"),b->requestRebuild()),y);y+=22;
        var resolved=AnnouncementClient.resolve(snapshot,templateId,variant,locale,previewVariables(),previewConditions());
        if(resolved.isPresent())y=addWrappedText(x,y,w,ComponentHelper.literal(resolved.get().displayText()),0x303030)+8;
        if(resolved.isPresent()&&!resolved.get().displayText().equals(resolved.get().spokenText()))y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.spoken_text",resolved.get().spokenText()),0x555555)+8;
        if(resolved.isPresent()){
            var parts=resolved.get().parts();
            addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.fragment_count",parts.size()),b->{showParts=!showParts;requestRebuild();}),y);y+=22;
            if(showParts)for(int i=0;i<parts.size();i++){
                var part=parts.get(i);y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.fragment",i+1,
                        ComponentHelper.translatable(part.variable().isEmpty()?"ui.fangsu.ann.fragment_fixed":"ui.fangsu.ann.fragment_variable"),part.spokenText()),0x555555)+5;
            }
        }
        var generate=addButton(x,y,w/2-2,20,ComponentHelper.translatable("ui.fangsu.ann.generate"),b->generate(false));primary(generate);generate.active=!requests.contains(previewRequest(false));addEntry(generate,y);
        var regenerate=addButton(x+w/2+2,y,w/2-2,20,ComponentHelper.translatable("ui.fangsu.ann.regenerate"),b->generate(true));regenerate.active=!requests.contains(previewRequest(true));addEntry(regenerate,y);y+=22;
        var play=addButton(x,y,w/2-2,20,ComponentHelper.translatable("ui.fangsu.ann.play"),b->{if(current!=null)CachedAudioPlayback.play(current);});play.active=current!=null;addEntry(play,y);
        addEntry(addButton(x+w/2+2,y,w/2-2,20,ComponentHelper.translatable("ui.fangsu.menu.stop"),b->CachedAudioPlayback.stopPreview()),y);y+=22;
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.cache_fragments_only"),0x555555)+6;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.manage_cached_fragments"),b->{CachedAudioPlayback.stopPreview();current=null;Minecraft.getInstance().setScreen(new AudioLibraryScreen(this));}),y);
    }
    private void choose(String title,List<String> values,java.util.function.Consumer<String> selected){Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,title,values.stream().map(value->new ConfigChoiceScreen.Choice(value,title.equals("ui.fangsu.menu.choose_template")?AnnouncementLabels.template(value):title.equals("ui.fangsu.menu.choose_locale")?AnnouncementLabels.locale(value):title.equals("ui.fangsu.menu.choose_variant")?AnnouncementLabels.variant(value):value)).toList(),value->{selected.accept(value);requestRebuild();}));}
    private void onboard(int x,int y,int w){
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.onboard_hint"),0x555555)+10;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.onboard_mode",ComponentHelper.translatable("ui.fangsu.ann.onboard_"+onboardDraft.onboardMode)),b->{onboardDraft.onboardMode=onboardDraft.onboardMode.equals("off")?"resource_pack":"off";requestRebuild();}),y);y+=22;
        y=field(x,y,w,"ui.fangsu.ann.languages",String.join(",",onboardDraft.languages),value->onboardDraft.languages=Arrays.stream(value.split(",")).map(String::trim).toList());
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.language_scope"),0x555555)+8;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.config.apply"),b->{try{var changed=new Gson().fromJson(new Gson().toJson(AnnouncementClient.settings()),AnnouncementClient.Settings.class);changed.onboardMode=onboardDraft.onboardMode;changed.languages=List.copyOf(onboardDraft.languages);AnnouncementClient.saveSettings(changed);status="ui.fangsu.config.applied";}catch(Exception e){status="ui.fangsu.ann.failed";}requestRebuild();}),y);y+=22;
        y=addWrappedText(x,y,w,ComponentHelper.translatable(status),0x555555)+8;
        var profiles=TrainAnnouncementRegistry.profiles();if(profiles.isEmpty()){addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.no_profiles"),0x555555);return;}
        for(var profile:new TreeMap<>(profiles).values())y=addWrappedText(x,y,w,ComponentHelper.literal(profile.id()+" · "+String.join(" / ",profile.languages())+" · "+profile.source()),0x303030)+8;
    }
    private void tasks(int x,int y,int w){
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.refresh"),b->requestRebuild()),y);y+=24;
        displayedTasks = taskSnapshot();
        if (displayedTasks.isEmpty()) {
            addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.no_tasks"),0x555555);
            return;
        }
        List<SynthesisService.Task> list = new ArrayList<>(displayedTasks);
        Collections.reverse(list);
        for (var task : list) {
            var taskState = ComponentHelper.translatable("ui.fangsu.menu.task_" + task.state().name().toLowerCase(Locale.ROOT));
            String shortKey = task.key().substring(0, 12);
            var row = task.error().isEmpty()
                    ? ComponentHelper.translatable("ui.fangsu.menu.task_status", shortKey, taskState)
                    : ComponentHelper.translatable("ui.fangsu.menu.task", shortKey, taskState, TtsErrorText.describe(task.error(), task.cloudFailure()));
            y = addWrappedText(x, y, w, row, 0x555555) + 8;
        }
    }

    private static List<SynthesisService.Task> taskSnapshot() {
        var service = AnnouncementClient.synthesis();
        return service == null ? List.of() : service.tasks();
    }

    @Override public void tick() {
        super.tick();
        if (page == 2 && ++taskRefreshTicks >= 20) {
            taskRefreshTicks = 0;
            if (!displayedTasks.equals(taskSnapshot())) requestRebuild();
        }
    }

    private void advanced(int x,int y,int w){
        y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.menu.advanced_hint"),0x555555)+10;
        addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.ann.editor"),b->Minecraft.getInstance().setScreen(new AnnouncementDefinitionEditor(this))),y);y+=24;
        for(var profile:new TreeMap<>(TrainAnnouncementRegistry.profiles()).values())if(profile.recording()!=null){addEntry(addButton(x,y,w,20,ComponentHelper.translatable("ui.fangsu.menu.edit_recording",profile.id()),b->Minecraft.getInstance().setScreen(new AnnouncementDefinitionEditor(this,profile.id()))),y);y+=22;}
    }
    private Map<String, String> previewVariables() {
        return Map.of("tripDestination", destination, "station", destination, "nextStation", destination,
                "route", RecordingFamily.name(route, Locale.forLanguageTag(locale).getLanguage()).strip());
    }
    private PreviewRequest previewRequest(boolean force){return new PreviewRequest(templateId,variant,locale,destination,route,force);}
    private void generate(boolean force) {
        PreviewRequest request=previewRequest(force);
        if (requests.contains(request)) return;
        var snapshot = AnnouncementRegistry.INSTANCE.snapshot();
        Optional<ResolvedUtterance> resolved = AnnouncementClient.resolve(snapshot, templateId, variant, locale,
                previewVariables(), previewConditions());
        if (resolved.isEmpty()) { status = "ui.fangsu.ann.missing"; requestRebuild(); return; }
        previewError = null;
        requests.add(request);
        status = "ui.fangsu.menu.preview_hint";
        requestRebuild();
        var audio = AnnouncementClient.generate(resolved.get(), force);
        var template = snapshot.templates().get(request.template());
        if (template != null && template.event().equals("platform.approaching")) {
            audio = audio.thenCombine(AnnouncementClient.stationChime(template), AudioSequence::withChime);
        }
        audio.whenComplete((artifact, error) -> Minecraft.getInstance().execute(() -> {
            requests.remove(request);
            if(request.equals(previewRequest(force))){
                if(error==null)current=artifact;
                previewError = TtsErrorText.find(error);
                status=error==null?"ui.fangsu.ann.ready":AudioLibraryScreen.errorKey(error);
            }
            requestRebuild();
        }));
    }
    private Set<String> previewConditions(){
        Set<String> conditions=new HashSet<>(Set.of("allStops","firstStop","notTerminal"));
        String name=com.fangsu.announcements.recordings.RecordingFamily.name(destination,locale).strip().toLowerCase(Locale.ROOT);
        if(!name.matches(".*(站|station|zhan|zhàn)$"))conditions.add("stationSuffixNeeded");
        return conditions;
    }
    private int field(int x, int y, int w, String label, String value, java.util.function.Consumer<String> consumer) {
        y = addWrappedText(x, y, w, ComponentHelper.translatable(label), 0x555555) + 4;
        EditBox box = new EditBox(font, x, y, w, 20, ComponentHelper.translatable(label));
        box.setMaxLength(256); box.setValue(value); box.setResponder(consumer); addRenderableWidget(box); addEntry(box, y);
        return y + 26;
    }
    @Override public void onFilesDrop(List<Path> paths){CachedAudioPlayback.stopPreview();current=null;AudioLibraryScreen.importDropped(this,paths);}
    @Override public void onClose(){CachedAudioPlayback.stopPreview();Minecraft.getInstance().setScreen(parent);}
}
