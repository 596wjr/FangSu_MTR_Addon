package com.fangsu.announcements.ui;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.cache.*;
import com.fangsu.announcements.playback.CachedAudioPlayback;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Path;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** 紧凑列表只承担查找和选择；操作固定在底部，完整文字使用独立详情页。 */
public final class AudioLibraryScreen extends ThemedConfigScreen {
    private final Screen parent;
    private List<AudioLibrary.Entry> entries=List.of();
    private com.fangsu.announcements.provider.CloudFailure regenerationError;
    private String search="",kind="all",language="all",state="active",selectedKey="",status="ui.fangsu.ann.library_loading";
    private int page,loadVersion,operationVersion;
    private final Set<String> regeneratingKeys=new HashSet<>();
    private int previewVersion;
    private boolean refreshPending;
    private boolean initialized,loading,busy,closed;
    private EditBox filter;
    private net.minecraft.network.chat.Component importReport;
    public AudioLibraryScreen(Screen parent){super(ComponentHelper.translatable("ui.fangsu.ann.library_title"));this.parent=parent;}
    @Override protected int getPanelBottom(){return height-64;}
    @Override protected int getContentTop(){return getPanelTop()+58;}
    @Override protected int getContentBottom(){return height-68;}
    private int pageSize(){return Math.max(1,(getContentBottom()-getContentTop())/28);}
    private AudioLibrary.Entry selection(){return filtered().stream().filter(e->e.key().equals(selectedKey)).findFirst().orElse(null);}
    @Override protected void buildFixedWidgets(){
        int x=getPanelLeft()+4,w=getPanelRight()-x-4,y=getPanelTop()+3;
        boolean focus=filter==null||filter.isFocused();int cursor=filter==null?search.length():filter.getCursorPosition();
        filter=new EditBox(font,x,y,w-174,18,ComponentHelper.translatable("ui.fangsu.ann.library_search"));
        filter.setMaxLength(256);filter.setValue(search);filter.setSuggestion(search.isEmpty()?ComponentHelper.translatableString("ui.fangsu.ann.library_search"):null);
        filter.setCursorPosition(Math.min(cursor,search.length()));filter.setResponder(value->{search=value;page=0;selectedKey="";scrollOffset=0;requestRebuild();});
        addRenderableWidget(filter);fixedWidgets.add(filter);
        if(focus){setFocused(filter);
            //#if MC_VERSION >= 11904
            filter.setFocused(true);
            //#else
            //$$ filter.setFocus(true);
            //#endif
        }
        var importer=addFixedWidget(themedButton(x+w-170,y,54,18,ComponentHelper.translatable("ui.fangsu.ann.import_audio"),b->pickFiles()));importer.active=!loading&&!busy;
        var packs=addFixedWidget(themedButton(x+w-112,y,54,18,ComponentHelper.translatable("ui.fangsu.ann.pack_menu"),b->Minecraft.getInstance().setScreen(new PackMenu(this))));packs.active=!loading&&!busy;
        var refresh=addFixedWidget(themedButton(x+w-54,y,54,18,ComponentHelper.translatable("ui.fangsu.menu.refresh"),b->refresh()));refresh.active=!loading&&!busy;
        int col=(w-6)/3;y+=21;
        addFixedWidget(themedButton(x,y,col,16,ComponentHelper.translatable("ui.fangsu.ann.library_kind",labelKind(kind)),b->chooseKind()));
        String locale=language.equals("all")?labelAll():language.split("-")[0].toUpperCase(Locale.ROOT);
        addFixedWidget(themedButton(x+col+3,y,col,16,ComponentHelper.translatable("ui.fangsu.ann.library_language",locale),b->chooseLanguage()));
        addFixedWidget(themedButton(x+2*(col+3),y,w-2*(col+3),16,ComponentHelper.translatable("ui.fangsu.ann.library_state",ComponentHelper.translatable("ui.fangsu.ann.library_"+state)),b->chooseState()));
        final Component notice;
        if (regenerationError != null && status.equals("ui.fangsu.ann.failed")) {
            notice = TtsErrorText.describe(regenerationError.getMessage(), regenerationError);
        } else if (!busy && !regeneratingKeys.isEmpty()) {
            notice = ComponentHelper.translatable("ui.fangsu.ann.library_regeneration_queue", regeneratingKeys.size());
        } else if (importReport != null && (status.equals("ui.fangsu.ann.import_done")
                || status.equals("ui.fangsu.ann.resource_pack_ready") || status.equals("ui.fangsu.ann.resource_pack_incompatible"))) {
            notice = importReport;
        } else {
            notice = ComponentHelper.translatable(status);
        }
        addFixedWidget(themedButton(x, getContentTop()-14, w, 12, notice,
                b -> Minecraft.getInstance().setScreen(new Notice(this, notice))));
        var item=selection();int action=(w-9)/4;
        var play=addFixedWidget(themedButton(x,height-53,action,16,ComponentHelper.translatable("ui.fangsu.ann.play"),b->act(item,"play",this)));play.active=item!=null&&item.playable();
        var regenerate=addFixedWidget(themedButton(x+action+3,height-53,action,16,ComponentHelper.translatable("ui.fangsu.ann.library_regenerate_short"),b->act(item,"regenerate",this)));regenerate.active=canRegenerate(item);
        var delete=addFixedWidget(themedButton(x+2*(action+3),height-53,action,16,ComponentHelper.translatable(item!=null&&item.deleted()?"ui.fangsu.ann.restore":"ui.fangsu.ann.delete"),b->act(item,"delete",this)));delete.active=item!=null&&!busy&&!loading;if(item==null||!item.deleted())danger(delete);
        var details=addFixedWidget(themedButton(x+3*(action+3),height-53,w-3*(action+3),16,ComponentHelper.translatable("ui.fangsu.ann.library_details"),b->Minecraft.getInstance().setScreen(new Details(this,item))));details.active=item!=null;
        int nav=(w-15)/6;var rows=filtered();int pages=Math.max(1,(rows.size()+pageSize()-1)/pageSize());page=Math.min(page,pages-1);
        var previous=addFixedWidget(themedButton(x,height-29,nav,16,ComponentHelper.translatable("ui.fangsu.ann.library_previous_short"),b->{page--;selectedKey="";requestRebuild();}));previous.active=page>0;
        var counter=addFixedWidget(themedButton(x+nav+3,height-29,nav,16,ComponentHelper.translatable("ui.fangsu.ann.library_page",page+1,pages),b->{}));counter.active=false;
        var next=addFixedWidget(themedButton(x+2*(nav+3),height-29,nav,16,ComponentHelper.translatable("ui.fangsu.ann.library_next_short"),b->{page++;selectedKey="";requestRebuild();}));next.active=page+1<pages;
        addFixedWidget(themedButton(x+3*(nav+3),height-29,nav,16,ComponentHelper.translatable("ui.fangsu.ann.library_cloud_short"),b->Minecraft.getInstance().setScreen(new TtsProviderScreen(this))));
        addFixedWidget(themedButton(x+4*(nav+3),height-29,nav,16,ComponentHelper.translatable("ui.fangsu.ann.library_stop_short"),b->stopPreview()));
        closeButton=addFixedWidget(themedButton(x+5*(nav+3),height-29,w-5*(nav+3),16,ComponentHelper.translatable("gui.back"),b->onClose()));
    }
    @Override protected void buildScrollableContent(ContentLayout layout){
        if(!initialized){initialized=true;refresh();}
        int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;var rows=filtered();
        int size=pageSize();
        int pages=Math.max(1,(rows.size()+size-1)/size);
        page=Math.min(page,pages-1);
        if(rows.isEmpty()){addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_empty"),0xAEB6C2);return;}
        for(int i=page*size;i<Math.min(rows.size(),(page+1)*size);i++){
            var item=rows.get(i);var recipe=item.recipe();String title=title(item);
            String note=labelKind(recipe==null?"legacy":recipe.kind())+" · "+(recipe==null?"?":recipe.locale())+String.format(Locale.ROOT," · %.2f s",item.seconds());
            if(item.deleted())note+=" · "+ComponentHelper.translatableString("ui.fangsu.ann.library_deleted");
            else if(!item.problem().isEmpty())note+=" · "+ComponentHelper.translatableString("ui.fangsu.ann.library_"+item.problem());
            var row=itemRow(x,y,w,ComponentHelper.literal(title),ComponentHelper.literal(note),b->{selectedKey=item.key();requestRebuild();});
            selected(row,selectedKey.equals(item.key()));addRenderableWidget(row);addEntry(row,y);y+=28;
        }
    }
    private static String title(AudioLibrary.Entry item){return item.recipe()==null?ComponentHelper.translatableString("ui.fangsu.ann.library_legacy")+" · "+item.key().substring(0,8):item.recipe().text().replace('\n',' ');}
    private boolean canRegenerate(AudioLibrary.Entry item){return item!=null&&!busy&&item.regenerable()&&!regeneratingKeys.contains(item.key())&&AnnouncementClient.synthesis()!=null&&AnnouncementClient.synthesis().enabled();}
    private void act(AudioLibrary.Entry item,String action,Screen origin){if(item==null)return;
        if(action.equals("play"))play(item,origin);
        else if(action.equals("regenerate"))queueRegeneration(item);
        else{stopPreview();perform(()->item.deleted()?AnnouncementClient.restore(item.key()):AnnouncementClient.delete(item.key()));}
    }
    private void updateDetails(){var screen=Minecraft.getInstance().screen;if(screen instanceof Details details&&details.owner==this){
        entries.stream().filter(e->e.key().equals(details.item.key())).findFirst().ifPresent(details::update);
    }}
    private String labelAll(){return ComponentHelper.translatableString("ui.fangsu.ann.library_all");}
    private String labelKind(String value){return ComponentHelper.translatableString("ui.fangsu.ann.library_"+value);}
    private List<AudioLibrary.Entry> filtered(){String needle=search.toLowerCase(Locale.ROOT);return entries.stream().filter(e->{
        var r=e.recipe();String type=r==null?"legacy":r.kind();
        if(!kind.equals("all")&&!kind.equals(type))return false;
        if(!language.equals("all")&&(r==null||!language.equals(r.locale())))return false;
        if(state.equals("active")&&e.deleted()||state.equals("deleted")&&!e.deleted())return false;
        return (e.key()+" "+labelKind(type)+(r==null?"":" "+r.text()+" "+r.spokenText()+" "+r.voice()+" "+AnnouncementLabels.voice(r.voice())+" "+r.provider()+" "+r.account())).toLowerCase(Locale.ROOT).contains(needle);
    }).toList();}
    private void chooseKind(){choose("ui.fangsu.ann.library_kind_title",List.of("all","fixed","variable","imported","legacy"),this::labelKind,value->kind=value);}
    private static boolean packPath(Path path){return AnnouncementArchiveImport.archive(path);}
    public static void chooseImport(Screen parent){var library=new AudioLibraryScreen(parent);Minecraft.getInstance().setScreen(library);library.pickFiles();}
    public static void importDropped(Screen parent,List<Path> paths){if(paths.isEmpty())return;var library=new AudioLibraryScreen(parent);Minecraft.getInstance().setScreen(library);library.onFilesDrop(paths);}
    @Override public void onFilesDrop(List<Path> paths){
        if(paths.stream().anyMatch(AudioLibraryScreen::packPath)){
            if(paths.size()!=1){status="ui.fangsu.ann.pack_drop_alone";requestRebuild();return;}
            inspectArchive(paths.get(0));
        }else importFiles(paths);
    }
    private void inspectArchive(Path path){
        if(busy)return;int version=++operationVersion;busy=true;status="ui.fangsu.ann.import_detecting";requestRebuild();
        AnnouncementArchiveImport.inspect(path).whenComplete((kind,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;busy=false;
            if(error!=null){status=errorKey(error);requestRebuild();return;}
            if(kind==AnnouncementArchiveImport.Kind.RESOURCE)runResourcePack(path);else runPack(path,null,false);
        }));
    }
    private void runResourcePack(Path path){
        if(busy)return;int version=++operationVersion;busy=true;status="ui.fangsu.ann.resource_pack_importing";requestRebuild();
        AnnouncementArchiveImport.install(path).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;busy=false;
            if(error!=null){status=errorKey(error);if(status.equals("ui.fangsu.ann.failed"))status="ui.fangsu.ann.resource_pack_failed";}
            else{
                status=result.enabled()?"ui.fangsu.ann.resource_pack_ready":"ui.fangsu.ann.resource_pack_incompatible";
                importReport=ComponentHelper.translatable("ui.fangsu.ann.resource_pack_name",result.name()).append("\n").append(ComponentHelper.translatable(status));
            }
            refresh();requestRebuild();
            if(error==null&&!result.enabled()&&Minecraft.getInstance().screen==this)openResourcePacks();
        }));
    }
    private void openResourcePacks(){
        try{AnnouncementArchiveImport.openResourcePacks(this,()->{status="ui.fangsu.ann.resource_pack_selected";refresh();requestRebuild();},error->{status=errorKey(error);refresh();requestRebuild();});}
        catch(RuntimeException e){status=errorKey(e);requestRebuild();}
    }
    private List<String> exportKeys(boolean all){return (all?entries:filtered()).stream().filter(AudioLibrary.Entry::playable).map(AudioLibrary.Entry::key).toList();}
    private void pickPack(boolean exporting,List<String> keys){
        if(!exporting){pickFiles();return;}
        if(busy)return;busy=true;int version=++operationVersion;status="ui.fangsu.ann.pack_choosing";requestRebuild();Minecraft.getInstance().setScreen(this);
        CompletableFuture.supplyAsync(()->{
            try(MemoryStack stack=MemoryStack.stackPush()){
                PointerBuffer patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.zip")).flip();
                String selected=exporting?TinyFileDialogs.tinyfd_saveFileDialog(ComponentHelper.translatableString("ui.fangsu.ann.pack_export"),"fangsu-audio-pack.zip",patterns,"FangSu Audio Pack (*.zip)")
                        :TinyFileDialogs.tinyfd_openFileDialog(ComponentHelper.translatableString("ui.fangsu.ann.pack_import"),null,patterns,"FangSu Audio Pack (*.zip)",false);
                return selected==null?null:Path.of(selected);
            }
        }).whenComplete((path,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;busy=false;
            if(error!=null){status="ui.fangsu.ann.pack_failed";requestRebuild();return;}
            if(path==null){status="ui.fangsu.ann.library_ready";requestRebuild();return;}
            if(!packPath(path)){status="ui.fangsu.ann.pack_extension";requestRebuild();return;}
            if(exporting)runPack(path,keys,true);else inspectArchive(path);
        }));
    }
    private void runPack(Path path,List<String> keys,boolean exporting){
        if(busy)return;int version=++operationVersion;busy=true;status=exporting?"ui.fangsu.ann.pack_exporting":"ui.fangsu.ann.pack_importing";requestRebuild();
        try{
            var future=exporting?AnnouncementClient.exportAudioPack(path,keys):AnnouncementClient.importAudioPack(path);
            future.whenComplete((result,error)->Minecraft.getInstance().execute(()->{
                if(closed||version!=operationVersion)return;busy=false;
                if(error!=null){status=errorKey(error);if(status.equals("ui.fangsu.ann.failed"))status="ui.fangsu.ann.pack_failed";}
                else{
                    if(!exporting){search="";kind="all";language="all";state="active";selectedKey="";page=0;scrollOffset=0;}
                    status="ui.fangsu.ann.import_done";
                    StringBuilder text=new StringBuilder(ComponentHelper.translatable(exporting?"ui.fangsu.ann.pack_export_summary":"ui.fangsu.ann.pack_import_summary",result.processed(),result.skipped(),result.failures().size()).getString());
                    for(var failure:result.failures())text.append("\n").append(failure.file()).append(": ").append(ComponentHelper.translatableString(errorKey(new java.io.IOException(failure.reason()))));
                    importReport=ComponentHelper.literal(text.toString());
                }
                refresh();updateDetails();requestRebuild();
            }));
        }catch(RuntimeException e){busy=false;status="ui.fangsu.ann.pack_failed";requestRebuild();}
    }
    private void pickFiles(){
        if(busy)return;busy=true;int version=++operationVersion;requestRebuild();Minecraft.getInstance().setScreen(this);
        CompletableFuture.supplyAsync(()->{
            try(MemoryStack stack=MemoryStack.stackPush()){
                PointerBuffer patterns=stack.mallocPointer(6);
                for(String pattern:List.of("*.wav","*.ogg","*.aiff","*.aif","*.au","*.zip"))patterns.put(stack.UTF8(pattern));patterns.flip();
                String selected=TinyFileDialogs.tinyfd_openFileDialog(ComponentHelper.translatableString("ui.fangsu.ann.import_home"),null,patterns,"Audio / FangSu audio ZIP / Minecraft resource pack ZIP",true);
                return selected==null?List.<Path>of():Arrays.stream(selected.split("\\|",-1)).filter(s->!s.isBlank()).map(Path::of).toList();
            }
        }).whenComplete((paths,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;busy=false;
            if(error!=null){status="ui.fangsu.ann.import_failed";requestRebuild();return;}
            if(!paths.isEmpty())onFilesDrop(paths);else requestRebuild();
        }));
    }
    private void importFiles(List<Path> paths){
        if(busy||paths.isEmpty())return;
        if(paths.size()>128){status="ui.fangsu.ann.import_count";requestRebuild();return;}
        busy=true;status="ui.fangsu.ann.import_working";requestRebuild();
        AnnouncementClient.importAudio(paths).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            busy=false;if(closed)return;
            if(error!=null)status="ui.fangsu.ann.import_failed";
            else{
                search="";kind="all";language="all";state="active";page=0;selectedKey="";scrollOffset=0;
                status="ui.fangsu.ann.import_done";
                StringBuilder report=new StringBuilder(ComponentHelper.translatable("ui.fangsu.ann.import_summary",result.imported(),result.duplicates(),result.failures().size()).getString());
                for(var failure:result.failures())report.append("\n").append(failure.file()).append(": ").append(ComponentHelper.translatableString("ui.fangsu.ann."+failure.reason()));
                importReport=ComponentHelper.literal(report.toString());
            }
            refresh();requestRebuild();
        }));
    }
    private void chooseLanguage(){List<String> choices=new ArrayList<>();choices.add("all");entries.stream().filter(e->e.recipe()!=null).map(e->e.recipe().locale()).distinct().sorted().forEach(choices::add);
        choose("ui.fangsu.ann.library_language_title",choices,value->value.equals("all")?labelAll():AnnouncementLabels.locale(value),value->language=value);}
    private void chooseState(){choose("ui.fangsu.ann.library_state_title",List.of("active","deleted","all"),value->ComponentHelper.translatableString("ui.fangsu.ann.library_"+value),value->state=value);}
    private void choose(String title,List<String> values,java.util.function.Function<String,String> label,java.util.function.Consumer<String> change){
        Minecraft.getInstance().setScreen(new ConfigChoiceScreen(this,title,values.stream().map(value->new ConfigChoiceScreen.Choice(value,label.apply(value))).toList(),value->{change.accept(value);page=0;selectedKey="";scrollOffset=0;requestRebuild();}));
    }
    private void refresh(){if(loading){refreshPending=true;return;}loading=true;int version=++loadVersion;requestRebuild();
        try{AnnouncementClient.audioEntries().whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(version!=loadVersion||closed)return;loading=false;
            if(error==null){entries=result;if(status.equals("ui.fangsu.ann.library_loading"))status="ui.fangsu.ann.library_ready";}else status=errorKey(error);updateDetails();requestRebuild();
            if(refreshPending){refreshPending=false;refresh();}
        }));}catch(RuntimeException e){loading=false;status="ui.fangsu.ann.failed";requestRebuild();}
    }
    private void stopPreview(){
        previewVersion++;
        CachedAudioPlayback.stopPreview();
    }
    private void play(AudioLibrary.Entry item,Screen origin){
        if(!item.playable())return;
        int version=++previewVersion;
        AnnouncementClient.libraryAudio(item.key()).whenComplete((audio,error)->Minecraft.getInstance().execute(()->{
            if(closed||previewVersion!=version||Minecraft.getInstance().screen!=origin)return;
            if(error==null)CachedAudioPlayback.play(audio);
            else{status=errorKey(error);updateDetails();requestRebuild();}
        }));
    }
    private void queueRegeneration(AudioLibrary.Entry item){
        if(!canRegenerate(item))return;
        String key=item.key();
        regenerationError = null;
        regeneratingKeys.add(key);
        updateDetails();requestRebuild();
        AnnouncementClient.regenerateAudio(key).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            regeneratingKeys.remove(key);
            regenerationError = TtsErrorText.find(error);
            if(closed)return;
            if(!busy){
                if(error!=null)status=errorKey(error);
                else if(result.fragments()>1)status="ui.fangsu.ann.library_regenerated_fragments";
                else{
                    status="ui.fangsu.ann.library_done";
                    var audio=result.first();
                    if(!audio.key().equals(key)){
                        if(selectedKey.equals(key)){selectedKey=audio.key();search="";state="active";page=0;scrollOffset=0;}
                        if(Minecraft.getInstance().screen instanceof Details details&&details.owner==this&&details.item.key().equals(key))details.retarget(audio);
                    }
                }
            }
            refresh();updateDetails();requestRebuild();
        }));
    }
    private void perform(java.util.function.Supplier<CompletableFuture<?>> action){
        if(busy)return;
        int version=++operationVersion;
        busy=true;status="ui.fangsu.ann.working";updateDetails();requestRebuild();
        try{action.get().whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;
            busy=false;status=error==null?"ui.fangsu.ann.library_done":errorKey(error);
            refresh();updateDetails();requestRebuild();
        }));}catch(RuntimeException e){busy=false;status=errorKey(e);updateDetails();requestRebuild();}
    }
    private void replaceAudio(AudioLibrary.Entry item,List<Path> paths){
        if(paths.isEmpty())return;
        if(paths.stream().anyMatch(AudioLibraryScreen::packPath)){status="ui.fangsu.ann.pack_detail_drop";updateDetails();requestRebuild();return;}
        if(paths.size()!=1){status="ui.fangsu.ann.replace_one";updateDetails();requestRebuild();return;}
        if(busy){status="ui.fangsu.ann.replace_busy";updateDetails();requestRebuild();return;}
        stopPreview();int version=++operationVersion;busy=true;status="ui.fangsu.ann.replace_working";updateDetails();requestRebuild();
        try{AnnouncementClient.replaceAudio(item.key(),paths.get(0)).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(closed||version!=operationVersion)return;
            busy=false;status=error==null?"ui.fangsu.ann.replace_done":errorKey(error);updateDetails();refresh();requestRebuild();
        }));}catch(RuntimeException e){if(version!=operationVersion)return;busy=false;status=errorKey(e);updateDetails();requestRebuild();}
    }
    static String errorKey(Throwable error){for(Throwable cause=error;cause!=null;cause=cause.getCause()){String message=cause.getMessage();
        if(message==null)continue;
        String key=switch(message){
            case "queue_full"->"ui.fangsu.ann.library_queue_full";
            case "aliyun_voice_pending","aliyun_voice_unavailable"->"ui.fangsu.ann."+message;
            case "missing_key"->"ui.fangsu.ann.library_missing_key";
            case "synthesis_disabled_or_deleted"->"ui.fangsu.ann.library_cloud_disabled";
            case "library_recipe_missing"->"ui.fangsu.ann.library_legacy_hint";
            case "library_sample_changed"->"ui.fangsu.ann.library_sample_changed";
            case "library_dependency_missing"->"ui.fangsu.ann.library_dependency_missing";
            case "resource_pack_invalid"->"ui.fangsu.ann.resource_pack_invalid";
            case "resource_pack_path"->"ui.fangsu.ann.resource_pack_path";
            case "resource_pack_limit"->"ui.fangsu.ann.resource_pack_limit";
            case "resource_pack_failed"->"ui.fangsu.ann.resource_pack_failed";
            case "resource_pack_reload_failed"->"ui.fangsu.ann.resource_pack_reload_failed";
            case "resource_pack_unavailable"->"ui.fangsu.ann.resource_pack_unavailable";
            case "pack_type_unknown"->"ui.fangsu.ann.pack_type_unknown";
            case "pack_type_ambiguous"->"ui.fangsu.ann.pack_type_ambiguous";
            case "library_audio_deleted"->"ui.fangsu.ann.library_deleted";
            case "library_composition_retired","composition_retired"->"ui.fangsu.ann.library_composition_retired";
            case "import_size"->"ui.fangsu.ann.import_size";
            case "import_duration"->"ui.fangsu.ann.import_duration";
            case "import_format"->"ui.fangsu.ann.import_format";
            case "import_failed"->"ui.fangsu.ann.import_failed";
            case "audio_pack_empty"->"ui.fangsu.ann.pack_empty";
            case "audio_pack_limit","audio_pack_binding_limit"->"ui.fangsu.ann.pack_limit";
            case "audio_pack_checksum"->"ui.fangsu.ann.pack_checksum";
            case "audio_pack_conflict","audio_pack_binding_conflict"->"ui.fangsu.ann.pack_conflict";
            case "audio_pack_path","audio_pack_invalid","audio_pack_schema","audio_pack_audio","audio_pack_duplicate","audio_pack_recipe","audio_pack_cache_unsafe"->"ui.fangsu.ann.pack_invalid";
            case "audio_pack_io"->"ui.fangsu.ann.pack_failed";
            default->null;
        };
        if(key!=null)return key;
        }return "ui.fangsu.ann.failed";
    }
    private static final class Details extends ThemedConfigScreen {
        private final AudioLibraryScreen owner;private AudioLibrary.Entry item;private boolean advanced;
        Details(AudioLibraryScreen owner,AudioLibrary.Entry item){super(ComponentHelper.translatable("ui.fangsu.ann.library_details"));this.owner=owner;this.item=item;}
        void update(AudioLibrary.Entry value){item=value;requestRebuild();}
        void retarget(AudioCacheStore.Artifact artifact){item=new AudioLibrary.Entry(artifact.key(),item.recipe(),artifact.createdAt(),item.bytes(),artifact.audio().seconds(),false,"",item.uses());requestRebuild();}
        @Override public void onFilesDrop(List<Path> paths){owner.replaceAudio(item,paths);}
        @Override protected int getPanelBottom(){return height-60;}
        @Override protected int getContentBottom(){return height-66;}
        @Override protected void buildFixedWidgets(){int x=getPanelLeft()+5,w=getPanelRight()-x-5,col=(w-6)/3;
            var play=addFixedWidget(themedButton(x,height-53,col,16,ComponentHelper.translatable("ui.fangsu.ann.play"),b->owner.act(item,"play",this)));play.active=item.playable();
            var regenerate=addFixedWidget(themedButton(x+col+3,height-53,col,16,ComponentHelper.translatable("ui.fangsu.ann.regenerate"),b->owner.act(item,"regenerate",this)));regenerate.active=owner.canRegenerate(item);
            var remove=addFixedWidget(themedButton(x+2*(col+3),height-53,w-2*(col+3),16,ComponentHelper.translatable(item.deleted()?"ui.fangsu.ann.restore":"ui.fangsu.ann.delete"),b->owner.act(item,"delete",this)));remove.active=!owner.busy&&!owner.loading;if(!item.deleted())danger(remove);
            addFixedWidget(themedButton(x,height-29,w/2-2,16,ComponentHelper.translatable("ui.fangsu.ann.library_stop_short"),b->owner.stopPreview()));
            closeButton=addFixedWidget(themedButton(x+w/2+2,height-29,w/2-2,16,ComponentHelper.translatable("gui.back"),b->onClose()));
        }
        @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;var recipe=item.recipe();
            if(recipe!=null&&recipe.kind().equals("imported")){
                EditBox caption=new EditBox(font,x,y,w-58,18,ComponentHelper.translatable("ui.fangsu.ann.import_caption"));caption.setMaxLength(4096);caption.setValue(recipe.text());addRenderableWidget(caption);addEntry(caption,y);
                var save=addButton(x+w-54,y,54,18,ComponentHelper.translatable("ui.fangsu.config.apply"),b->owner.perform(()->AnnouncementClient.renameImportedAudio(item.key(),caption.getValue())));save.active=!owner.busy&&!owner.loading;addEntry(save,y);y+=24;
            }
            y=addWrappedText(x,y,w,ComponentHelper.literal(title(item)),0xE1E6EC)+8;
            if(recipe!=null&&!recipe.spokenText().equals(recipe.text()))y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.spoken_text",recipe.spokenText()),0xAEB6C2)+8;
            String info=owner.labelKind(recipe==null?"legacy":recipe.kind())+" · "+(recipe==null?"?":AnnouncementLabels.locale(recipe.locale())+" · "+AnnouncementLabels.voice(recipe.voice()))
                    +String.format(Locale.ROOT," · %.2f s · %.0f KiB",item.seconds(),item.bytes()/1024d);
            y=addWrappedText(x,y,w,ComponentHelper.literal(info),0xAEB6C2)+6;
            if(item.deleted())y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_deleted"),0xD5B786)+6;
            if(recipe==null)y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_legacy_hint"),0xD5B786)+6;
            else if(recipe.kind().equals("imported"))y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_imported_hint"),0xAEB6C2)+6;
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.replace_hint"),0xAEB6C2)+6;
            if(owner.regeneratingKeys.contains(item.key()))y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_regeneration_pending"),0xAEB6C2)+6;
            if (owner.regenerationError != null && owner.status.equals("ui.fangsu.ann.failed"))
                y = addWrappedText(x,y,w,TtsErrorText.describe(owner.regenerationError.getMessage(), owner.regenerationError),0xAEB6C2)+6;
            if(!owner.status.equals("ui.fangsu.ann.library_ready"))y=addWrappedText(x,y,w,ComponentHelper.translatable(owner.status),0xAEB6C2)+6;
            addEntry(addButton(x,y,w,16,ComponentHelper.translatable("ui.fangsu.ann.library_advanced"),b->{advanced=!advanced;requestRebuild();}),y);y+=22;
            if(advanced){
                if(recipe!=null)y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.library_source",recipe.provider(),recipe.account(),recipe.model()),0xAEB6C2)+5;
                if(item.createdAt()>0)y=addWrappedText(x,y,w,ComponentHelper.literal(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.createdAt()))),0xAEB6C2)+5;
                addWrappedText(x,y,w,ComponentHelper.literal(item.key()),0xAEB6C2);
            }
        }
        @Override public void onClose(){owner.stopPreview();Minecraft.getInstance().setScreen(owner);}
    }
    private static final class PackMenu extends ThemedConfigScreen {
        private final AudioLibraryScreen owner;
        PackMenu(AudioLibraryScreen owner){super(ComponentHelper.translatable("ui.fangsu.ann.pack_title"));this.owner=owner;}
        @Override protected void buildFixedWidgets(){closeButton=addFixedWidget(themedButton(getPanelLeft(),height-35,getPanelRight()-getPanelLeft(),16,ComponentHelper.translatable("gui.back"),b->onClose()));}
        @Override protected void buildScrollableContent(ContentLayout layout){int x=layout.areaLeft,w=layout.areaRight-x,y=layout.y;
            y=addWrappedText(x,y,w,ComponentHelper.translatable("ui.fangsu.ann.pack_hint"),0xAEB6C2)+8;
            addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.ann.pack_import"),b->owner.pickPack(false,null)),y);y+=24;
            addEntry(addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.ann.resource_pack_manage"),b->owner.openResourcePacks()),y);y+=24;
            var filtered=owner.exportKeys(false);var all=owner.exportKeys(true);
            var selected=addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.ann.pack_export_filtered",filtered.size()),b->owner.pickPack(true,filtered));selected.active=!filtered.isEmpty();addEntry(selected,y);y+=24;
            var export=addButton(x,y,w,18,ComponentHelper.translatable("ui.fangsu.ann.pack_export_all",all.size()),b->owner.pickPack(true,all));export.active=!all.isEmpty();addEntry(export,y);
        }
        @Override public void onFilesDrop(List<Path> paths){Minecraft.getInstance().setScreen(owner);owner.onFilesDrop(paths);}
        @Override public void onClose(){Minecraft.getInstance().setScreen(owner);}
    }
    private static final class Notice extends ThemedConfigScreen {
        private final Screen parent;private final net.minecraft.network.chat.Component message;
        Notice(Screen parent,net.minecraft.network.chat.Component message){super(ComponentHelper.translatable("ui.fangsu.ann.library_title"));this.parent=parent;this.message=message;}
        @Override public void onFilesDrop(List<Path> paths){if(parent instanceof AudioLibraryScreen library){Minecraft.getInstance().setScreen(library);library.onFilesDrop(paths);}}
        @Override protected void buildScrollableContent(ContentLayout layout){int y=layout.y;for(String line:message.getString().split("\\R",-1))y=addWrappedText(layout.areaLeft,y,layout.areaRight-layout.areaLeft,ComponentHelper.literal(line),0xC6CDD7)+3;}
        @Override protected void buildFixedWidgets(){closeButton=addFixedWidget(themedButton(getPanelLeft(),height-35,getPanelRight()-getPanelLeft(),16,ComponentHelper.translatable("gui.back"),b->onClose()));}
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    }
    @Override public void onClose(){closed=true;stopPreview();Minecraft.getInstance().setScreen(parent);}
}
