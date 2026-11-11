package com.fangsu.announcements.cache;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 每条音频保存无凭据的重生成信息；列表只读磁盘元数据，不调用云端。 */
public final class AudioLibrary {
    public record Recipe(String kind,String text,String spokenText,String locale,String voice,String provider,String account,
                         String model,String sampleResource,Map<String,String> parameters,List<String> parts) {
        public Recipe { parameters=Map.copyOf(parameters);parts=List.copyOf(parts); }
    }
    public record Entry(String key,Recipe recipe,long createdAt,long bytes,double seconds,boolean deleted,String problem,int uses) {
        public boolean playable(){return !deleted&&problem.isEmpty()&&(recipe==null||!recipe.kind().equals("composition"));}
        public boolean regenerable(){return !deleted&&recipe!=null&&!Set.of("imported","composition").contains(recipe.kind());}
    }
    private static final Set<String> PARAMETERS=Set.of("protocol","provider","endpoint","account","model","voiceId","voiceRevision","voiceContent","locale","text","style","encoding");
    private final AudioCacheStore cache;
    public AudioLibrary(AudioCacheStore cache){this.cache=cache;}
    /** 仅用于识别并清理旧版完整拼接缓存；新播报不再保存这种配方。 */
    public static String compositionKey(List<String> keys){
        Map<String,String> sequence=new TreeMap<>();sequence.put("protocol","segmented-composition-v1");
        for(int i=0;i<keys.size();i++)sequence.put("part/"+i,keys.get(i));return SynthesisKey.of(sequence);
    }
    private Path directory(String key){if(!key.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid_cache_key");return cache.root().resolve(key);}
    public void remember(String key,Recipe recipe) throws IOException {
        if(recipe.kind().equals("composition"))throw new IOException("library_composition_retired");
        validate(key,recipe);JsonObject json=new JsonObject();json.addProperty("schemaVersion",1);
        json.addProperty("kind",recipe.kind());json.addProperty("text",recipe.text());json.addProperty("spokenText",recipe.spokenText());
        json.addProperty("locale",recipe.locale());json.addProperty("voice",recipe.voice());json.addProperty("provider",recipe.provider());
        json.addProperty("account",recipe.account());json.addProperty("model",recipe.model());json.addProperty("sampleResource",recipe.sampleResource());
        JsonObject parameters=new JsonObject();recipe.parameters().forEach(parameters::addProperty);json.add("parameters",parameters);
        JsonArray parts=new JsonArray();recipe.parts().forEach(parts::add);json.add("parts",parts);
        byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>65536)throw new IOException("library_recipe_limit");
        Path file=directory(key).resolve("library.json");
        if(Files.exists(file)&&Files.size(file)<=65536&&Arrays.equals(Files.readAllBytes(file),bytes))return;
        AudioCacheStore.atomic(file,bytes);
    }
    public Recipe recipe(String key) throws IOException {
        Path file=directory(key).resolve("library.json");if(!Files.exists(file))return null;
        try{
            JsonObject json=read(file);if(json.get("schemaVersion").getAsInt()!=1)throw new IOException("library_recipe_schema");
            Map<String,String> parameters=new HashMap<>();json.getAsJsonObject("parameters").entrySet().forEach(e->parameters.put(e.getKey(),e.getValue().getAsString()));
            List<String> parts=new ArrayList<>();for(var part:json.getAsJsonArray("parts"))parts.add(part.getAsString());
            Recipe recipe=new Recipe(string(json,"kind"),string(json,"text"),string(json,"spokenText"),string(json,"locale"),string(json,"voice"),
                    string(json,"provider"),string(json,"account"),string(json,"model"),string(json,"sampleResource"),parameters,parts);
            validate(key,recipe);return recipe;
        }catch(RuntimeException e){throw new IOException("invalid_library_recipe");}
    }
    private static String string(JsonObject json,String key){return json.has(key)?json.get(key).getAsString():"";}
    private static JsonObject read(Path file) throws IOException {
        if(Files.size(file)>65536)throw new IOException("library_recipe_limit");
        try{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("invalid_cache_manifest");}
    }
    static void validate(String key,Recipe recipe) throws IOException {
        if(recipe.kind().equals("imported")){
            var p=recipe.parameters();
            if(recipe.text().length()>4096||!recipe.spokenText().equals(recipe.text())||(recipe.locale().length()>35||!recipe.locale().equals("und")&&java.util.Locale.forLanguageTag(recipe.locale()).getLanguage().isEmpty())
                    ||(recipe.voice().length()>256||!recipe.voice().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))||!recipe.provider().equals("local")||!recipe.account().equals("default")
                    ||!recipe.model().equals("imported")||!recipe.sampleResource().isEmpty()||!recipe.parts().isEmpty()
                    ||!p.keySet().equals(Set.of("protocol","contentHash","fileName"))||!"imported-audio-v1".equals(p.get("protocol"))
                    ||!p.get("contentHash").matches("[0-9a-f]{64}")||p.get("fileName").length()>4096||!SynthesisKey.of(p).equals(key))throw new IOException("invalid_library_recipe");
            return;
        }
        if(!Set.of("fixed","variable","composition").contains(recipe.kind())||recipe.text().length()>4096||recipe.spokenText().length()>4096
                ||recipe.locale().length()>35||recipe.voice().length()>256||!recipe.voice().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||!Set.of("mimo","minimax-cn","minimax-intl").contains(recipe.provider())||!recipe.account().matches("[A-Za-z0-9_.-]{1,64}")
                ||!recipe.model().matches("[A-Za-z0-9_.-]{1,100}"))throw new IOException("invalid_library_recipe");
        if(!recipe.sampleResource().isEmpty()&&(recipe.sampleResource().length()>256||recipe.sampleResource().contains("..")||!recipe.sampleResource().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))throw new IOException("invalid_library_recipe");
        if(recipe.kind().equals("composition")){
            if(!recipe.parameters().isEmpty()||recipe.parts().isEmpty()||recipe.parts().size()>128||recipe.parts().stream().anyMatch(p->!p.matches("[0-9a-f]{64}"))
                    ||!compositionKey(recipe.parts()).equals(key))throw new IOException("invalid_library_recipe");
        }else{
            var p=recipe.parameters();
            if(!recipe.parts().isEmpty()||!p.keySet().equals(PARAMETERS)||p.values().stream().anyMatch(v->v.length()>4096)||!SynthesisKey.of(p).equals(key)
                    ||!p.get("protocol").equals("2-fragment")||!p.get("provider").equals(recipe.provider())||!p.get("account").equals(recipe.account())
                    ||!p.get("model").equals(recipe.model())||!p.get("voiceId").equals(recipe.voice())||!p.get("locale").equals(recipe.locale())
                    ||!p.get("text").equals(recipe.spokenText())||p.get("voiceContent").length()>256)throw new IOException("invalid_library_recipe");
        }
    }
    private List<Path> directories() throws IOException {
        List<Path> result=new ArrayList<>();
        try(var stream=Files.newDirectoryStream(cache.root())){for(Path path:stream)
            if(path.getFileName().toString().matches("[0-9a-f]{64}")&&Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))result.add(path);}
        return result;
    }
    public List<Entry> list() throws IOException {
        List<Entry> entries=new ArrayList<>();
        for(Path dir:directories()){
            String key=dir.getFileName().toString();Path current=dir.resolve("current.json");if(!Files.exists(current))continue;
            Recipe recipe=null;try{recipe=recipe(key);}catch(IOException ignored){ /* 旧缓存或损坏的元数据仍可在列表里清理。 */ }
            if(recipe!=null&&recipe.kind().equals("composition"))continue;
            long created=0,size=0;double seconds=0;String problem="";
            try{
                JsonObject manifest=read(current);created=manifest.get("createdAt").getAsLong();
                String generation=manifest.get("generation").getAsString();if(!generation.matches("[0-9a-f-]{36}"))throw new IOException("invalid_generation");
                Path audio=dir.resolve(generation+".wav");size=Files.size(audio);if(size>24*1024*1024)throw new IOException("audio_too_large");
                seconds=manifest.has("durationSeconds")?manifest.get("durationSeconds").getAsDouble():WavInfo.inspect(Files.readAllBytes(audio)).seconds();
                if(!Double.isFinite(seconds)||seconds<=0||seconds>180)throw new IOException("invalid_wav");
            }catch(IOException|RuntimeException e){problem="unavailable";}
            entries.add(new Entry(key,recipe,created,size,seconds,cache.suppressed(key),problem,0));
        }
        entries.sort(Comparator.comparingLong(Entry::createdAt).reversed().thenComparing(Entry::key));return List.copyOf(entries);
    }
    public AudioCacheStore.Artifact playable(String key) throws IOException {
        Recipe recipe;try{recipe=recipe(key);}catch(IOException e){recipe=null;}
        if(recipe!=null&&recipe.kind().equals("composition"))throw new IOException("library_composition_retired");
        return cache.find(key).orElseThrow(()->new IOException("library_audio_missing"));
    }
    /** 只清理经过旧配方、片段序列和缓存键共同验证的自动拼接成品。片段与用户导入不受影响。 */
    public int retireCompositions() throws IOException {
        int retired=0;IOException failure=null;
        for(Path dir:directories()){
            Path metadata=dir.resolve("library.json");
            if(Files.isSymbolicLink(metadata))continue;
            Recipe recipe;
            try{recipe=recipe(dir.getFileName().toString());}catch(IOException ignored){continue;}
            if(recipe==null||!recipe.kind().equals("composition"))continue;
            try{cache.purgeDerived(dir.getFileName().toString());retired++;}
            catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        }
        if(failure!=null)throw failure;
        return retired;
    }
    /** 兼容旧调用；新播放会话直接读取各片段的当前代际，不再使完整音频失效或重拼。 */
    public void invalidateDependents(String key) throws IOException {
    }
    public void delete(String key) throws IOException {cache.delete(key);invalidateDependents(key);}
}
