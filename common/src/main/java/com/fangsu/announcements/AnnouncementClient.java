package com.fangsu.announcements;

import com.fangsu.Main;
import com.fangsu.announcements.cache.*;
import com.fangsu.announcements.definition.*;
import com.fangsu.announcements.definition.AnnouncementDefinitions.*;
import com.fangsu.announcements.playback.CachedAudioPlayback;
import com.fangsu.announcements.provider.*;
import com.fangsu.announcements.resolve.AnnouncementResolver;
import com.google.gson.*;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** 客户端唯一装配点；后台准备样本，网络与磁盘不进入渲染循环。 */
public final class AnnouncementClient {
    public static final Path CONFIG = Platform.getConfigFolder().resolve("fangsu/announcements");
    public static final Path OVERRIDES = CONFIG.resolve("overrides.json");
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "fangsu-announcement-io"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
    private static AudioCacheStore cache;
    private static SynthesisService synthesis;
    private static AudioLibrary library;
    private static volatile Settings settings = new Settings();
    private static ResourceManager resources;
    private static final Set<String> PREWARMED=new LinkedHashSet<>();
    private static int prewarming;
    private static volatile long prewarmEpoch;
    public static final class Settings {
        public int schemaVersion = 1;
        public String provider = "mimo", model = "mimo-v2.5-tts", accountNamespace = "default";
        public boolean enabled = true;
        public boolean preSynthesize = false;
        public String onboardMode = "resource_pack";
        public Map<String, String> languageVoices = new HashMap<>();
        public java.util.List<String> languages = java.util.List.of("zh-CN", "en-US");
        public Map<String, String> voiceMappings = new HashMap<>();
    }
    public static ResourceManager resources() { return resources; }
    public static Settings settings() { return settings; }
    public static Optional<ResolvedUtterance> resolve(Snapshot snapshot, String template, String variant, String locale, Map<String,String> variables, Set<String> conditions) {
        String selected = variant == null && snapshot.templates().containsKey(template) ? snapshot.templates().get(template).defaultVariant() : variant;
        return AnnouncementResolver.resolve(snapshot, template, variant, locale, variables, conditions, settings.languageVoices.get(template + "/" + selected + "/" + locale));
    }
    public static SynthesisService synthesis() { return synthesis; }
    public static void sessionKey(String key) {
        try { com.fangsu.announcements.provider.ClientTokens.save(settings.provider, settings.accountNamespace, key, false); }
        catch (IOException e) { Main.LOGGER.warn("[FangSu] Session token could not be stored"); }
    }
    public static void reload(ResourceManager manager) {
        resetPreSynthesis();
        resources = manager; com.fangsu.announcements.playback.StationAnnouncementCoordinator.onReload();
        com.fangsu.announcements.playback.OnboardAnnouncementCoordinator.reset();
        AnnouncementRegistry.INSTANCE.reload(manager, OVERRIDES);
        com.fangsu.announcements.definition.TrainAnnouncementRegistry.reload(manager);
    }
    public static void shutdown() { if (synthesis != null) synthesis.close(); IO.shutdownNow(); com.fangsu.announcements.provider.ClientTokens.clearSession(); CachedAudioPlayback.stop(); }
    public static void init() {
        dev.architectury.event.events.client.ClientLifecycleEvent.CLIENT_STOPPING.register(client -> shutdown());
        final Object[] previousWorld = {null};
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> {
            CachedAudioPlayback.tick();
            com.fangsu.announcements.playback.OnboardAnnouncementCoordinator.tick(client);
            com.fangsu.announcements.playback.StationAnnouncementCoordinator.tick(client);
            if (previousWorld[0] != client.level) { previousWorld[0] = client.level; CachedAudioPlayback.stop(); }
        });
        try {
            Path file = CONFIG.resolve("connections.json");
            if (Files.exists(file)) {
                if (Files.size(file) > 65536) throw new IOException("connection_file_too_large");
                Settings loaded = new Gson().fromJson(Files.readString(file), Settings.class);
                if (loaded != null && Set.of("recordings", "cloud").contains(loaded.onboardMode)) loaded.onboardMode = "resource_pack";
                validate(loaded); settings = loaded;
            }
            cache = new AudioCacheStore(Minecraft.getInstance().gameDirectory.toPath().resolve("cache/fangsu/announcements"));
            library=new AudioLibrary(cache);
            IO.execute(()->{try{int retired=library.retireCompositions();if(retired>0)Main.LOGGER.info("[FangSu] Retired {} derived full-announcement caches; fragments retained",retired);}catch(IOException e){Main.LOGGER.warn("[FangSu] Old full-announcement cache cleanup failed ({})",e.getClass().getSimpleName());}});
            synthesis = new SynthesisService(cache, CONFIG.resolve("usage.json")); synthesis.setEnabled(settings.enabled);
        } catch (Exception e) { Main.LOGGER.warn("[FangSu] Announcement initialization failed ({})", e.getClass().getSimpleName()); }
    }
    public static void validate(Settings value) {
        if (value == null || value.schemaVersion != 1 || !Set.of("mimo", "minimax-cn", "minimax-intl").contains(value.provider)
                || value.model == null || !value.model.matches("[A-Za-z0-9_.-]{1,100}") || value.accountNamespace == null
                || !value.accountNamespace.matches("[A-Za-z0-9_.-]{1,64}") || value.voiceMappings == null || value.voiceMappings.size() > 1024 || value.onboardMode == null || !Set.of("off", "resource_pack").contains(value.onboardMode)

                || value.languageVoices == null || value.languageVoices.size() > 2048 || value.languageVoices.values().stream().anyMatch(v -> v == null || !v.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) || value.languages == null || value.languages.isEmpty() || value.languages.size() > 8 || value.languages.stream().anyMatch(locale -> locale == null || locale.length() > 35 || java.util.Locale.forLanguageTag(locale).getLanguage().isEmpty())) throw new IllegalArgumentException("invalid_connection");
    }
    public static void saveSettings(Settings value) throws IOException {
        validate(value);
        AudioCacheStore.atomic(CONFIG.resolve("connections.json"), new Gson().toJson(value).getBytes(StandardCharsets.UTF_8));
        settings = value; if (synthesis != null) synthesis.setEnabled(value.enabled);
        resetPreSynthesis();
    }
    private record SampleData(String voice,String hash) { }
    private record Prepared(String key,CloudTtsProvider.Region region,TtsProvider.Request request,AudioLibrary.Recipe recipe,boolean local) { }
    private record PreparedPlan(List<Prepared> parts,Settings connection,long backgroundEpoch) { }
    private record Regeneration(Prepared part,Settings connection,String oldKey,long oldEpoch,long newEpoch) { }
    private record ReferenceChoice(String resource,String style) { }
    private static final String CHINESE_NAMES_STYLE="Read the Chinese station names in their Chinese pronunciation, as demonstrated by the station-name examples in the reference audio. Keep the surrounding announcement in English.";
    public static CompletableFuture<AudioSequence> generate(ResolvedUtterance utterance,boolean force) {
        return generate(utterance,force,-1);
    }
    private static void resetPreSynthesis(){synchronized(PREWARMED){prewarmEpoch++;PREWARMED.clear();}}
    public static void preSynthesize(ResolvedUtterance utterance){
        if(!settings.preSynthesize||!settings.enabled||utterance.voice().mode().equals("recording"))return;
        Map<String,String> identity=new TreeMap<>();identity.put("provider",settings.provider);identity.put("account",settings.accountNamespace);identity.put("model",settings.model);
        identity.put("voice",utterance.voice().id()+"/"+utterance.voice().revision());identity.put("locale",utterance.locale());identity.put("style",utterance.style());identity.put("mappings",new Gson().toJson(settings.voiceMappings));
        for(int i=0;i<utterance.parts().size();i++)identity.put("part/"+i,utterance.parts().get(i).spokenText());
        final String key;final long epoch;
        synchronized(PREWARMED){
            epoch=prewarmEpoch;identity.put("epoch",String.valueOf(epoch));key=SynthesisKey.of(identity);
            if(PREWARMED.contains(key)||prewarming>=2)return;
            if(PREWARMED.size()>=2048)PREWARMED.remove(PREWARMED.iterator().next());
            PREWARMED.add(key);prewarming++;
        }
        // 仅准备缓存，完成回调绝不触发播放；后台同时最多两条播报，给实际播放请求保留队列空间。
        generate(utterance,false,epoch).whenComplete((audio,error)->{synchronized(PREWARMED){
            prewarming--;
            if(error!=null){while(error.getCause()!=null)error=error.getCause();if(Set.of("queue_full","pre_synthesis_disabled").contains(String.valueOf(error.getMessage())))PREWARMED.remove(key);}
        }});
    }
    private static CompletableFuture<AudioSequence> generate(ResolvedUtterance utterance,boolean force,long backgroundEpoch) {
        if(synthesis==null)return CompletableFuture.failedFuture(new IOException("service_unavailable"));
        Settings connection=new Gson().fromJson(new Gson().toJson(settings),Settings.class);
        ResourceManager manager=resources;
        try{return CompletableFuture.supplyAsync(()->{
            try{
                if(utterance.parts().isEmpty()||utterance.parts().size()>128)throw new IOException("fragment_count_limit");
                List<Prepared> parts=new ArrayList<>();Map<String,SampleData> samples=new HashMap<>();
                for(var part:utterance.parts()){
                    var packed=AudioPack.findBinding(cache,part.spokenText(),utterance.locale(),utterance.voice().id());
                    Prepared prepared=packed.isPresent()?new Prepared(packed.get().key(),null,null,null,true):preparePart(utterance,part,connection,manager,samples);
                    if(!prepared.local())library.remember(prepared.key(),prepared.recipe());parts.add(prepared);
                }
                // 顺序只保留在本次请求内存中；只保存单个固定语句 / 站名，不创建完整播报记录。
                return new PreparedPlan(List.copyOf(parts),connection,backgroundEpoch);
            }catch(Exception e){throw new CompletionException(e);}
        },IO).thenCompose(plan->generateParts(plan,force));}
        catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    private static CompletableFuture<AudioSequence> generateParts(PreparedPlan plan,boolean force){
        // 两条准备流水线，让一条播报也能填满 MiMo 的两路并发；服务层仍按提供商限制实际网络并发。
        List<CompletableFuture<Void>> lanes=new ArrayList<>(List.of(CompletableFuture.<Void>completedFuture(null),CompletableFuture.<Void>completedFuture(null)));
        Map<String,CompletableFuture<AudioCacheStore.Artifact>> fragments=new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicBoolean failed=new java.util.concurrent.atomic.AtomicBoolean();
        CompletableFuture<AudioSequence> result=new CompletableFuture<>();int index=0;
        for(Prepared part:plan.parts()){
            if(fragments.containsKey(part.key()))continue;
            int lane=index++%2;
            var future=lanes.get(lane).thenCompose(ignored->{
                if(failed.get())return CompletableFuture.<AudioCacheStore.Artifact>failedFuture(new IOException("fragment_preparation_cancelled"));
                if(plan.backgroundEpoch()>=0&&(plan.backgroundEpoch()!=prewarmEpoch||!settings.preSynthesize||!settings.enabled))return CompletableFuture.<AudioCacheStore.Artifact>failedFuture(new IOException("pre_synthesis_disabled"));
                return requestPart(part,plan.connection(),force);
            }).whenComplete((artifact,error)->{if(error!=null){failed.set(true);result.completeExceptionally(error);}});
            fragments.put(part.key(),future);lanes.set(lane,future.thenApply(artifact->null));
        }
        CompletableFuture.allOf(fragments.values().toArray(CompletableFuture[]::new)).whenComplete((ignored,error)->{
            if(error!=null){result.completeExceptionally(error);return;}
            try{CompletableFuture.supplyAsync(()->{
                try{
                    List<AudioCacheStore.Artifact> current=new ArrayList<>();
                    for(var part:plan.parts()){
                        if(cache.suppressed(part.key()))throw new IOException("library_audio_deleted");
                        current.add(cache.find(part.key()).orElseThrow(()->new IOException("library_dependency_missing")));
                    }
                    // 较早完成的片段可能已被详情页替换；汇总当前代际，时长也使用最新声音。
                    return new AudioSequence(current);
                }catch(IOException e){throw new CompletionException(e);}
            },IO).whenComplete((sequence,failure)->{if(failure==null)result.complete(sequence);else result.completeExceptionally(failure);});}
            catch(RejectedExecutionException e){result.completeExceptionally(new IOException("queue_full"));}
        });
        return result;
    }
    private static CompletableFuture<AudioCacheStore.Artifact> requestPart(Prepared part,Settings connection,boolean force){
        return requestPart(part,connection,force,null);
    }
    private static CompletableFuture<AudioCacheStore.Artifact> requestPart(Prepared part,Settings connection,boolean force,Regeneration regeneration){
        // 先读当前缓存，手动替换的文件优先于仍在网络队列里的旧请求。
        try{return CompletableFuture.supplyAsync(()->{
            try{return force&&!part.local()?Optional.<AudioCacheStore.Artifact>empty():cache.find(part.key());}
            catch(IOException e){throw new CompletionException(e);}
        },IO).thenComposeAsync(cached->{
            // 覆盖/删除也在同一 IO 队列：检查后至服务捕获 epoch 之间不能插入另一条本地修改。
            if(regeneration!=null)try{
                var replacement=regenerationOverride(regeneration);if(replacement.isPresent())return CompletableFuture.completedFuture(replacement.get());
            }catch(IOException e){return CompletableFuture.failedFuture(e);}
            if(cached.isPresent())return CompletableFuture.completedFuture(cached.get());
            if(part.local())return CompletableFuture.failedFuture(new IOException("library_dependency_missing"));
            long epoch=cache.epoch(part.key());
            return synthesis.request(part.key(),force,new CloudTtsProvider(part.region()),part.request(),()->{
                try{return ClientTokens.resolve(connection.provider,connection.accountNamespace);}
                catch(IOException e){throw new UncheckedIOException(e);}
            }).handle((artifact,error)->{
                if(error==null)return artifact;
                // 本地替换会推进 epoch。较早的云端结果失效后使用最新本地版本，绝不重新付费请求。
                if(!force||cache.epoch(part.key())!=epoch)try{var current=cache.find(part.key());if(current.isPresent())return current.get();}catch(IOException e){throw new CompletionException(e);}
                throw new CompletionException(error);
            });
        },IO);}catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    private static Prepared preparePart(ResolvedUtterance utterance,SpeechPart part,Settings connection,ResourceManager manager,Map<String,SampleData> samples) throws IOException {
        String provider=connection.provider;
        String voice=connection.voiceMappings.getOrDefault(provider+"/"+utterance.voice().id()+"/"+utterance.locale(),utterance.voice().presetVoices().getOrDefault(provider,""));
        String sampleHash="",model=connection.model,style=utterance.style(),sampleResource="";
        if(utterance.voice().mode().equals("reference_audio")&&provider.equals("mimo")){
            var reference=utterance.voice().samples().get(utterance.locale());
            if(reference==null||manager==null)throw new IOException("missing_language_sample");
            ReferenceChoice choice=referenceChoice(reference,utterance.locale(),part.spokenText(),style);
            String resource=choice.resource();style=choice.style();
            SampleData sample=samples.get(resource);if(sample==null){sample=loadSample(manager,resource);samples.put(resource,sample);}
            sampleResource=resource;
            voice=sample.voice();sampleHash=sample.hash();model="mimo-v2.5-tts-voiceclone";
        }
        if(voice.isBlank())throw new IOException("voice_mapping_required");
        if(utterance.voice().mode().equals("recording"))throw new IOException("recording_requires_offline_asset");
        CloudTtsProvider.Region region=provider.equals("mimo")?CloudTtsProvider.Region.MIMO:provider.equals("minimax-cn")?CloudTtsProvider.Region.MINIMAX_CN:CloudTtsProvider.Region.MINIMAX_INTL;
        Map<String,String> keyData=new HashMap<>();
        keyData.put("protocol","2-fragment");keyData.put("provider",provider);keyData.put("endpoint",region.endpoint.toString());
        keyData.put("account",connection.accountNamespace);keyData.put("model",model);
        keyData.put("voiceId",utterance.voice().id());keyData.put("voiceRevision",String.valueOf(utterance.voice().revision()));
        keyData.put("voiceContent",sampleHash.isEmpty()?voice:sampleHash);keyData.put("locale",utterance.locale());keyData.put("text",part.spokenText());keyData.put("style",style);
        keyData.put("encoding",provider.equals("mimo")?"wav/provider-default/pcm16/v1":"wav/32000/mono/pcm16/v1");
        var recipe=new AudioLibrary.Recipe(part.variable().isEmpty()?"fixed":"variable",part.displayText(),part.spokenText(),utterance.locale(),utterance.voice().id(),
                provider,connection.accountNamespace,model,sampleResource,keyData,List.of());
        return new Prepared(SynthesisKey.of(keyData),region,new TtsProvider.Request(model,part.spokenText(),utterance.locale(),voice,style),recipe,false);
    }
    private static ReferenceChoice referenceChoice(ReferenceSample reference,String locale,String spokenText,String style){
        // 固定英文用主参考；中文站名片段才使用该音色当前定义的站名提示。
        if(Locale.forLanguageTag(locale).getLanguage().equals("en")
                &&com.fangsu.announcements.resolve.StationNamePronunciation.containsChinese(spokenText)&&!reference.chineseNamesResource().isEmpty())
            return new ReferenceChoice(reference.chineseNamesResource(),(style.isEmpty()?"":style+"\n")+CHINESE_NAMES_STYLE);
        return new ReferenceChoice(reference.resource(),style);
    }
    private static String withoutChineseNamesStyle(String style){
        if(style.equals(CHINESE_NAMES_STYLE))return "";
        String suffix="\n"+CHINESE_NAMES_STYLE;return style.endsWith(suffix)?style.substring(0,style.length()-suffix.length()):style;
    }
    private static SampleData loadSample(ResourceManager manager,String resource) throws IOException {
        ResourceLocation location=new ResourceLocation(resource);Resource sample;
        //#if MC_VERSION >= 11900
        sample=manager.getResource(location).orElseThrow(()->new IOException("missing_sample"));
        try(InputStream input=sample.open()){
        //#else
        //$$ sample=manager.getResource(location);
        //$$ try(InputStream input=sample.getInputStream()){
        //#endif
            byte[] bytes=input.readNBytes(8*1024*1024+1);
            if(bytes.length>8*1024*1024||bytes.length<12)throw new IOException("sample_size_limit");
            String mime;
            if(resource.endsWith(".wav")){if(WavInfo.inspect(bytes).seconds()>60)throw new IOException("sample_duration_limit");mime="wav";}
            else if(resource.endsWith(".mp3")&&((bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3')||((bytes[0]&255)==255&&(bytes[1]&224)==224)))mime="mp3";
            else throw new IOException("unsupported_sample_format");
            return new SampleData("data:audio/"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes),SynthesisKey.digest(bytes));
        }
    }
    public static CompletableFuture<Void> restore(String key) { return CompletableFuture.runAsync(() -> { try { cache.restore(key); } catch (IOException e) { throw new CompletionException(e); } }, IO); }
    public static CompletableFuture<Void> delete(String key) { return CompletableFuture.runAsync(() -> { try { library.delete(key); } catch (IOException e) { throw new CompletionException(e); } }, IO); }
    public static CompletableFuture<List<AudioLibrary.Entry>> audioEntries(){return CompletableFuture.supplyAsync(()->{
        try{if(library==null)throw new IOException("service_unavailable");return library.list();}catch(IOException e){throw new CompletionException(e);}
    },IO);}
    public static CompletableFuture<AudioImporter.Result> importAudio(List<Path> paths){
        if(paths.isEmpty()||paths.size()>128)return CompletableFuture.failedFuture(new IOException("import_count"));
        List<Path> selected=List.copyOf(paths);
        try{return CompletableFuture.supplyAsync(()->{
            if(library==null||cache==null)throw new CompletionException(new IOException("service_unavailable"));
            return AudioImporter.importFiles(library,cache,selected);
        },IO);}catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    public static CompletableFuture<AudioPack.Result> importAudioPack(Path source){
        try{return CompletableFuture.supplyAsync(()->{
            try{if(library==null||cache==null)throw new IOException("service_unavailable");return AudioPack.importPack(library,cache,source);}
            catch(IOException e){throw new CompletionException(e);}
        },IO);}catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    public static CompletableFuture<AudioPack.Result> exportAudioPack(Path destination,List<String> keys){
        List<String> selected=keys==null?List.of():List.copyOf(keys);
        try{return CompletableFuture.supplyAsync(()->{
            try{if(library==null||cache==null)throw new IOException("service_unavailable");return AudioPack.exportPack(library,cache,destination,selected);}
            catch(IOException e){throw new CompletionException(e);}
        },IO);}catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    public static CompletableFuture<AudioCacheStore.Artifact> libraryAudio(String key){return CompletableFuture.supplyAsync(()->{
        try{return library.playable(key);}catch(IOException e){throw new CompletionException(e);}
    },IO);}
    public static CompletableFuture<Void> renameImportedAudio(String key,String text){return CompletableFuture.runAsync(()->{
        try{var recipe=library.recipe(key);if(recipe==null||!recipe.kind().equals("imported"))throw new IOException("library_imported");
            library.remember(key,new AudioLibrary.Recipe(recipe.kind(),text,text,recipe.locale(),recipe.voice(),recipe.provider(),recipe.account(),recipe.model(),recipe.sampleResource(),recipe.parameters(),recipe.parts()));
        }catch(IOException e){throw new CompletionException(e);}
    },IO);}
    public static CompletableFuture<AudioCacheStore.Artifact> replaceAudio(String key,Path source){
        try{return CompletableFuture.supplyAsync(()->{
            try{
                if(library==null||cache==null)throw new IOException("service_unavailable");
                cache.epoch(key); // 校验稳定缓存编号，不从文件名猜测要覆盖的条目。
                if(!Files.isRegularFile(cache.root().resolve(key).resolve("current.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("library_audio_missing");
                AudioLibrary.Recipe recipe;try{recipe=library.recipe(key);}catch(IOException e){recipe=null;}
                if(recipe!=null&&recipe.kind().equals("composition"))throw new IOException("library_composition_retired");
                byte[] audio=AudioImporter.decode(source);
                // decode 成功后才改变原条目；恢复墓碑并推进版本，防止旧合成任务晚到覆盖本地文件。
                synchronized(cache){
                    boolean deleted=cache.suppressed(key);cache.restore(key);
                    try{return cache.publish(key,cache.epoch(key),audio);}
                    catch(IOException e){if(deleted)cache.delete(key);throw e;}
                }
            }catch(IOException e){throw new CompletionException(e);}
        },IO);}catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    public static CompletableFuture<AudioCacheStore.Artifact> regenerateAudio(String key){
        ResourceManager manager=resources;
        Snapshot definitions=AnnouncementRegistry.INSTANCE.snapshot();
        try{return CompletableFuture.supplyAsync(()->{
            try{
                if(library==null||synthesis==null)throw new IOException("service_unavailable");
                var recipe=library.recipe(key);if(recipe==null)throw new IOException("library_recipe_missing");
                if(recipe.kind().equals("imported"))throw new IOException("library_imported");
                if(recipe.kind().equals("composition"))throw new IOException("library_composition_retired");
                if(cache.suppressed(key))throw new IOException("library_audio_deleted");
                long oldEpoch=cache.epoch(key);
                Settings connection=new Settings();connection.provider=recipe.provider();connection.accountNamespace=recipe.account();connection.model=recipe.model();
                Prepared part=restorePrepared(recipe,manager,definitions);
                library.remember(part.key(),part.recipe());
                return new Regeneration(part,connection,key,oldEpoch,cache.epoch(part.key()));
            }catch(IOException e){throw new CompletionException(e);}
        },IO).thenCompose(plan->requestPart(plan.part(),plan.connection(),plan.oldKey().equals(plan.part().key()),plan)
                .thenCompose(artifact->CompletableFuture.supplyAsync(()->{
                    try{
                        var replacement=regenerationOverride(plan);if(replacement.isPresent()){
                            // 新 key 被本地覆盖仍可采用，但旧详情被覆盖时必须留在原条目。
                            if(!replacement.get().key().equals(plan.part().key()))return replacement.get();
                        }
                        var current=cache.find(plan.part().key()).orElseThrow(()->new IOException("library_audio_missing"));
                        if(!plan.oldKey().equals(current.key())){
                            AudioPack.retargetBindings(cache,plan.oldKey(),current.key());
                            library.delete(plan.oldKey());
                        }
                        return current;
                    }catch(IOException e){throw new CompletionException(e);}
                },IO)));}
        catch(RejectedExecutionException e){return CompletableFuture.failedFuture(new IOException("queue_full"));}
    }
    private static Optional<AudioCacheStore.Artifact> regenerationOverride(Regeneration plan) throws IOException {
        if(cache.suppressed(plan.oldKey()))throw new IOException("library_audio_deleted");
        if(cache.epoch(plan.oldKey())!=plan.oldEpoch())return Optional.of(cache.find(plan.oldKey()).orElseThrow(()->new IOException("library_audio_missing")));
        if(cache.suppressed(plan.part().key()))throw new IOException("library_audio_deleted");
        if(cache.epoch(plan.part().key())!=plan.newEpoch())return Optional.of(cache.find(plan.part().key()).orElseThrow(()->new IOException("library_audio_missing")));
        return Optional.empty();
    }
    private static Prepared restorePrepared(AudioLibrary.Recipe recipe,ResourceManager manager,Snapshot definitions) throws IOException {
        Map<String,String> p=new HashMap<>(recipe.parameters());CloudTtsProvider.Region region=switch(recipe.provider()){
            case "mimo"->CloudTtsProvider.Region.MIMO;case "minimax-cn"->CloudTtsProvider.Region.MINIMAX_CN;case "minimax-intl"->CloudTtsProvider.Region.MINIMAX_INTL;
            default->throw new IOException("invalid_library_recipe");
        };
        if(!region.endpoint.toString().equals(p.get("endpoint")))throw new IOException("invalid_library_recipe");
        String voice=p.get("voiceContent"),sampleResource=recipe.sampleResource(),style=p.get("style");
        Voice definition=definitions.voices().get(recipe.voice());
        if(definition!=null)p.put("voiceRevision",String.valueOf(definition.revision()));
        if(!sampleResource.isEmpty()){
            if(manager==null)throw new IOException("missing_sample");
            if(definition!=null){
                if(!definition.mode().equals("reference_audio"))throw new IOException("missing_language_sample");
                ReferenceSample reference=definition.samples().get(recipe.locale());if(reference==null)throw new IOException("missing_language_sample");
                ReferenceChoice choice=referenceChoice(reference,recipe.locale(),recipe.spokenText(),withoutChineseNamesStyle(style));
                sampleResource=choice.resource();style=choice.style();
            }
            // 当前定义优先；定义已移除时仅允许原参考路径的最新有效文件，不猜测另一种音色。
            SampleData sample=loadSample(manager,sampleResource);voice=sample.voice();p.put("voiceContent",sample.hash());
        }else if(definition!=null){
            voice=settings.voiceMappings.getOrDefault(recipe.provider()+"/"+recipe.voice()+"/"+recipe.locale(),definition.presetVoices().getOrDefault(recipe.provider(),voice));
            if(voice.isBlank())throw new IOException("voice_mapping_required");p.put("voiceContent",voice);
        }
        p.put("style",style);
        var current=new AudioLibrary.Recipe(recipe.kind(),recipe.text(),recipe.spokenText(),recipe.locale(),recipe.voice(),recipe.provider(),recipe.account(),recipe.model(),sampleResource,p,List.of());
        return new Prepared(SynthesisKey.of(p),region,new TtsProvider.Request(recipe.model(),recipe.spokenText(),recipe.locale(),voice,style),current,false);
    }
    public static CompletableFuture<JsonObject> localRecording(String family) {
        return CompletableFuture.supplyAsync(() -> com.fangsu.announcements.recordings.RecordingRegistry.definition(family), IO);
    }
    public static CompletableFuture<Boolean> saveRecording(String family, JsonObject json) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!json.has("schemaVersion") || json.get("schemaVersion").getAsInt() != 1) return false;
                com.fangsu.announcements.recordings.RecordingRegistry.validate(json);
                byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); if (bytes.length > 1024*1024) return false;
                AudioCacheStore.atomic(com.fangsu.announcements.recordings.RecordingRegistry.localPath(family), bytes);
                return com.fangsu.announcements.recordings.RecordingRegistry.reload();
            } catch (Exception e) { return false; }
        }, IO);
    }
    public static CompletableFuture<JsonObject> localDefinitions() {
        return CompletableFuture.supplyAsync(() -> {
            if (Files.exists(OVERRIDES)) try (InputStream input = Files.newInputStream(OVERRIDES)) { return AnnouncementRegistry.read(input); }
            catch (IOException e) { throw new CompletionException(e); }
            JsonObject empty = new JsonObject(); empty.addProperty("schemaVersion", 1); empty.add("voices", new JsonArray()); empty.add("templates", new JsonArray()); return empty;
        }, IO);
    }
    public static CompletableFuture<Boolean> saveOverride(JsonObject json) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(CONFIG); Path draft = Files.createTempFile(CONFIG, "definition-", ".tmp");
                try {
                    byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); Files.write(draft, bytes);
                    var prepared = AnnouncementRegistry.INSTANCE.prepare(resources, draft);
                    AudioCacheStore.atomic(OVERRIDES, bytes); AnnouncementRegistry.INSTANCE.publish(prepared); return true;
                } finally { Files.deleteIfExists(draft); }
            } catch (Exception e) { if (resources != null) AnnouncementRegistry.INSTANCE.reload(resources, OVERRIDES); return false; }
        }, IO);
    }
}
