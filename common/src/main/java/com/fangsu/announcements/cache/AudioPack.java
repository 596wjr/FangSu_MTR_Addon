package com.fangsu.announcements.cache;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** 可移植片段 ZIP。包内只含 WAV、无凭据配方和显式文本绑定，绝不提取任意归档路径。 */
public final class AudioPack {
    public static final String FORMAT="fangsu-audio-pack";
    public static final int MAX_CLIPS=4096,MAX_AUDIO_BYTES=24*1024*1024,MAX_MANIFEST_BYTES=8*1024*1024;
    public static final long MAX_TOTAL_BYTES=1024L*1024*1024;
    private static final int MAX_BINDINGS=16384,MAX_SCAN=65536;
    private static final String INDEX="pack-bindings.json";
    private static final Set<String> RECIPE_FIELDS=Set.of("schemaVersion","kind","text","spokenText","locale","voice","provider","account","model","sampleResource","parameters","parts");
    private static final Map<Path,BindingIndex> INDEXES=new HashMap<>();
    public record Failure(String file,String reason) { }
    public record Result(int processed,int skipped,List<Failure> failures) { public Result { failures=List.copyOf(failures); } }
    public record Binding(String text,String locale,String voice) { }
    private record Target(String key,String source) { }
    private static final class BindingIndex { final LinkedHashMap<Binding,Target> targets=new LinkedHashMap<>();BindingIndex(){}BindingIndex(BindingIndex old){targets.putAll(old.targets);} }
    private record Clip(String key,String audio,String sha,AudioLibrary.Recipe recipe,List<Binding> bindings) { }
    private record Exported(String key,AudioLibrary.Recipe recipe,AudioCacheStore.Artifact artifact,List<Binding> bindings) { }
    private AudioPack() { }

    /** 空选择表示导出全部有效、未删除片段；临时成品、凭据与参考音频 Base64 不进入包。 */
    public static Result exportPack(AudioLibrary library,AudioCacheStore cache,Path destination,List<String> keys) throws IOException {
        Objects.requireNonNull(destination);List<Failure> failures=new ArrayList<>();LinkedHashSet<String> selected=new LinkedHashSet<>();
        if(keys==null||keys.isEmpty()){for(var entry:library.list())if(entry.playable())selected.add(entry.key());}else selected.addAll(keys);
        if(selected.isEmpty())throw error("empty");if(selected.size()>MAX_CLIPS)throw error("limit");
        List<Exported> clips=new ArrayList<>();long total=0;int skipped=0;
        for(String selectedKey:selected){
            try{
                validKey(selectedKey);if(cache.suppressed(selectedKey)){skipped++;continue;}
                AudioLibrary.Recipe recipe;try{recipe=library.recipe(selectedKey);}catch(IOException e){recipe=null;}
                if(recipe!=null&&recipe.kind().equals("composition")){skipped++;continue;}
                var artifact=library.playable(selectedKey);if(Files.size(artifact.file())>MAX_AUDIO_BYTES)throw error("limit");
                String key=selectedKey;
                if(recipe==null){String name="legacy-"+selectedKey.substring(0,12)+".wav";recipe=imported("legacy-"+selectedKey.substring(0,12),"und","fangsu:imported",name,artifact.contentHash());key=SynthesisKey.of(recipe.parameters());}
                AudioLibrary.validate(key,recipe);portableRecipe(recipe);List<Binding> bindings=new ArrayList<>(readClipBindings(cache,selectedKey));
                if(!recipe.kind().equals("imported")&&!recipe.spokenText().isBlank())bindings.add(binding(recipe.spokenText(),recipe.locale(),recipe.voice()));
                bindings=new ArrayList<>(new LinkedHashSet<>(bindings));
                if(bindings.size()>32)throw error("binding_limit");
                if((total+=Files.size(artifact.file()))>MAX_TOTAL_BYTES)throw error("limit");
                clips.add(new Exported(key,recipe,artifact,List.copyOf(bindings)));
            }catch(IOException|RuntimeException e){failures.add(new Failure(String.valueOf(selectedKey),reason(e)));}
        }
        if(clips.isEmpty())throw error("empty");
        // 同文案不同风格的历史片段可能同时存在；无唯一目标时保留精确配方，不推断跨账户绑定。
        Map<Binding,String> owners=new HashMap<>();Set<Binding> ambiguous=new HashSet<>();
        for(var clip:clips)for(var binding:clip.bindings()){String previous=owners.putIfAbsent(binding,clip.key());if(previous!=null&&!previous.equals(clip.key()))ambiguous.add(binding);}
        if(owners.size()-ambiguous.size()>MAX_BINDINGS)throw error("binding_limit");
        Path output=destination.toAbsolutePath().normalize();if(output.getParent()==null||Files.isDirectory(output)||Files.isSymbolicLink(output))throw error("path");
        if(output.startsWith(cache.root()))throw error("path");Files.createDirectories(output.getParent());Path temp=Files.createTempFile(output.getParent(),".fangsu-audio-pack-",".zip");
        boolean moved=false;
        try{
            JsonArray manifestClips=new JsonArray();long copied=0;int exported=0;
            try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp),StandardCharsets.UTF_8)){
                for(var clip:clips){
                    // 读取前再次核对不可变代际文件，避免把外部改坏的缓存作为可移植成品分发。
                    byte[] audio=readFile(clip.artifact().file(),MAX_AUDIO_BYTES);WavInfo.inspect(audio);
                    if(!SynthesisKey.digest(audio).equals(clip.artifact().contentHash()))throw error("checksum");
                    if((copied+=audio.length)>MAX_TOTAL_BYTES)throw error("limit");String entry="audio/"+clip.key()+".wav";
                    write(zip,entry,audio);JsonObject row=new JsonObject();row.addProperty("key",clip.key());row.addProperty("audio",entry);row.addProperty("sha256",SynthesisKey.digest(audio));
                    row.addProperty("text",clip.recipe().text());row.addProperty("locale",clip.recipe().locale());row.addProperty("voice",clip.recipe().voice());row.add("recipe",recipeJson(clip.recipe()));
                    JsonArray bindings=new JsonArray();for(var b:clip.bindings())if(!ambiguous.contains(b))bindings.add(bindingJson(b));if(bindings.size()!=0)row.add("bindings",bindings);
                    manifestClips.add(row);exported++;
                }
                JsonObject manifest=new JsonObject();manifest.addProperty("schemaVersion",1);manifest.addProperty("format",FORMAT);manifest.add("clips",manifestClips);
                byte[] json=manifest.toString().getBytes(StandardCharsets.UTF_8);if(json.length>MAX_MANIFEST_BYTES)throw error("limit");write(zip,"manifest.json",json);
            }
            try{Files.move(temp,output,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,output,StandardCopyOption.REPLACE_EXISTING);}moved=true;
            return new Result(exported,skipped,failures);
        }finally{if(!moved)Files.deleteIfExists(temp);}
    }

    /** 全部清单先验证，再逐片段校验校验和及 WAV；已有本地音频（含删除墓碑）默认保留。 */
    public static Result importPack(AudioLibrary library,AudioCacheStore cache,Path source) throws IOException {
        Path input=source.toAbsolutePath().normalize();if(!Files.isRegularFile(input)||Files.size(input)>MAX_TOTAL_BYTES+16L*1024*1024)throw error("limit");
        validateCentralDirectory(input);String provenance=input.getFileName().toString();if(provenance.length()>255)throw error("path");
        try(ZipFile zip=new ZipFile(input.toFile(),StandardCharsets.UTF_8)){
            Map<String,ZipEntry> entries=archiveEntries(zip);ZipEntry manifestEntry=entries.get("manifest.json");if(manifestEntry==null||manifestEntry.isDirectory())throw error("invalid");
            JsonObject manifest=parse(read(zip,manifestEntry,MAX_MANIFEST_BYTES)).getAsJsonObject();
            if(!version(manifest)||!FORMAT.equals(string(manifest,"format",64)))throw error("schema");
            JsonArray raw=manifest.getAsJsonArray("clips");if(raw==null||raw.size()==0||raw.size()>MAX_CLIPS)throw error("limit");
            List<Clip> clips=new ArrayList<>();Set<String> audioNames=new HashSet<>(),clipKeys=new HashSet<>();Map<Binding,String> logical=new HashMap<>();long declared=0;
            for(JsonElement element:raw){
                Clip clip=clip(element.getAsJsonObject());ZipEntry audio=entries.get(clip.audio());if(audio==null||audio.isDirectory()||audio.getSize()<44||audio.getSize()>MAX_AUDIO_BYTES)throw error("audio");
                if(!audioNames.add(clip.audio())||!clipKeys.add(clip.key()))throw error("duplicate");
                if((declared+=audio.getSize())>MAX_TOTAL_BYTES)throw error("limit");
                for(var b:clip.bindings()){String previous=logical.putIfAbsent(b,clip.key());if(previous!=null&&!previous.equals(clip.key()))throw error("conflict");}
                clips.add(clip);
            }
            List<Failure> failures=new ArrayList<>();int processed=0,skipped=0;long consumed=0;
            synchronized(AudioPack.class){
                BindingIndex working=new BindingIndex(index(cache));Set<String> modified=new HashSet<>();
                Set<Binding> newBindings=new HashSet<>();for(var clip:clips)for(var b:clip.bindings())if(!working.targets.containsKey(b))newBindings.add(b);
                if((long)working.targets.size()+newBindings.size()>MAX_BINDINGS)throw error("binding_limit");
                for(var clip:clips){
                    try{
                        byte[] audio=read(zip,entries.get(clip.audio()),MAX_AUDIO_BYTES);if((consumed+=audio.length)>MAX_TOTAL_BYTES)throw error("limit");
                        if(!SynthesisKey.digest(audio).equals(clip.sha()))throw error("checksum");WavInfo.inspect(audio);safeDirectory(cache,clip.key());
                        Path dir=cache.root().resolve(clip.key());boolean present;
                        synchronized(cache){
                            present=Files.exists(dir.resolve("current.json"),LinkOption.NOFOLLOW_LINKS)||cache.suppressed(clip.key());
                            if(!present){
                                // 检查、推进 epoch 与发布同属一个事务，旧云端任务不能晚到覆盖导入文件。
                                AudioLibrary.Recipe existing;try{existing=library.recipe(clip.key());}catch(IOException e){existing=null;}
                                library.remember(clip.key(),existing==null?clip.recipe():existing);cache.invalidate(clip.key());
                                cache.publish(clip.key(),cache.epoch(clip.key()),audio);
                            }
                        }
                        boolean added=false,conflict=false;
                        for(var b:clip.bindings()){
                            Target prior=working.targets.get(b);if(prior!=null&&!prior.key().equals(clip.key())){conflict=true;continue;}
                            if(prior==null){if(working.targets.size()>=MAX_BINDINGS)throw error("binding_limit");working.targets.put(b,new Target(clip.key(),provenance));modified.add(clip.key());added=true;}
                        }
                        if(conflict)failures.add(new Failure(clip.audio(),"audio_pack_binding_conflict"));
                        if(present&&!added)skipped++;else processed++;
                    }catch(IOException|RuntimeException e){failures.add(new Failure(clip.audio(),reason(e)));}
                }
                // 配方、声音及绑定分别原子发布；清单失败会报错，下次导入可在保留声音的基础上重试。
                if(!modified.isEmpty()){
                    for(String key:modified)saveClipBindings(cache,key,working);
                    saveIndex(cache,working);INDEXES.put(cache.root(),working);
                }
            }
            return new Result(processed,skipped,failures);
        }catch(ZipException|RuntimeException e){throw new IOException("audio_pack_invalid",e);}
    }

    /** 显式可移植绑定优先于云端，当前代际会随详情页本地替换自动变化。应在 IO 线程调用。 */
    public static Optional<AudioCacheStore.Artifact> findBinding(AudioCacheStore cache,String text,String locale,String voice) throws IOException {
        try{
            Target target;synchronized(AudioPack.class){target=index(cache).targets.get(binding(text,locale,voice));}
            if(target==null)return Optional.empty();
            if(cache.suppressed(target.key()))throw new IOException("synthesis_disabled_or_deleted");
            return Optional.of(cache.find(target.key()).orElseThrow(()->new IOException("library_dependency_missing")));
        }catch(RuntimeException e){throw new IOException("audio_pack_invalid",e);}
    }
    public static synchronized void invalidateBindings(AudioCacheStore cache){INDEXES.remove(cache.root());}
    /** 显式重新生成成功后，旧片段的可移植绑定随真实新 key 迁移；不覆盖其他片段的绑定。 */
    public static synchronized void retargetBindings(AudioCacheStore cache,String oldKey,String newKey) throws IOException {
        validKey(oldKey);validKey(newKey);if(oldKey.equals(newKey))return;
        BindingIndex working=new BindingIndex(index(cache));boolean changed=false;
        for(var entry:working.targets.entrySet())if(entry.getValue().key().equals(oldKey)){
            entry.setValue(new Target(newKey,entry.getValue().source()));changed=true;
        }
        if(!changed)return;
        saveClipBindings(cache,newKey,working);saveClipBindings(cache,oldKey,working);saveIndex(cache,working);
        INDEXES.put(cache.root(),working);
    }

    private static Clip clip(JsonObject row) throws IOException {
        String audio=string(row,"audio",1024);safeName(audio,false);if(!audio.toLowerCase(Locale.ROOT).endsWith(".wav"))throw error("audio");
        String sha=string(row,"sha256",64);validKey(sha);List<Binding> bindings=new ArrayList<>();
        if(row.has("bindings")){JsonArray raw=row.getAsJsonArray("bindings");if(raw.size()>32)throw error("binding_limit");Set<Binding> unique=new HashSet<>();for(var value:raw){Binding b=readBinding(value.getAsJsonObject());if(!unique.add(b))throw error("duplicate");bindings.add(b);}}
        bindings=new ArrayList<>(new LinkedHashSet<>(bindings));
        String name=audio.substring(audio.lastIndexOf('/')+1),caption=row.has("text")?string(row,"text",4096):stem(name);
        String locale=row.has("locale")?locale(string(row,"locale",35)):"und",voice=row.has("voice")?voice(string(row,"voice",256)):"fangsu:imported";
        AudioLibrary.Recipe recipe;String key;
        if(row.has("recipe")&&!row.get("recipe").isJsonNull()){
            key=string(row,"key",64);validKey(key);recipe=recipe(row.getAsJsonObject("recipe"));AudioLibrary.validate(key,recipe);
            if(!caption.equals(recipe.text())||!locale.equals(locale(recipe.locale()))||!voice.equals(recipe.voice()))throw error("recipe");
        }else{
            if(row.has("key"))throw error("recipe");recipe=imported(caption,locale,voice,name,sha);key=SynthesisKey.of(recipe.parameters());AudioLibrary.validate(key,recipe);
        }
        if(recipe.kind().equals("composition"))throw error("recipe");return new Clip(key,audio,sha,recipe,List.copyOf(bindings));
    }
    private static AudioLibrary.Recipe imported(String text,String locale,String voice,String name,String sha){
        return new AudioLibrary.Recipe("imported",text,text,locale,voice,"local","default","imported","",Map.of("protocol","imported-audio-v1","contentHash",sha,"fileName",name),List.of());
    }
    private static AudioLibrary.Recipe recipe(JsonObject json) throws IOException {
        if(!RECIPE_FIELDS.containsAll(json.keySet())||json.has("schemaVersion")&&!version(json))throw error("recipe");
        Map<String,String> parameters=new HashMap<>();JsonObject raw=json.getAsJsonObject("parameters");if(raw==null||raw.size()>32)throw error("recipe");
        for(var entry:raw.entrySet())parameters.put(entry.getKey(),primitive(entry.getValue(),4096));
        List<String> parts=new ArrayList<>();JsonArray rawParts=json.getAsJsonArray("parts");if(rawParts==null||rawParts.size()>128)throw error("recipe");for(var part:rawParts)parts.add(primitive(part,64));
        var recipe=new AudioLibrary.Recipe(string(json,"kind",32),string(json,"text",4096),string(json,"spokenText",4096),string(json,"locale",35),string(json,"voice",256),string(json,"provider",32),string(json,"account",64),string(json,"model",100),string(json,"sampleResource",256),parameters,parts);
        portableRecipe(recipe);return recipe;
    }
    private static void portableRecipe(AudioLibrary.Recipe recipe) throws IOException {
        if(!Set.of("fixed","variable","imported").contains(recipe.kind())||recipe.parameters().values().stream().anyMatch(v->v.regionMatches(true,0,"data:",0,5)||v.regionMatches(true,0,"Bearer ",0,7)))throw error("recipe");
        locale(recipe.locale());voice(recipe.voice());
        if(recipe.kind().equals("imported")){
            String name=recipe.parameters().get("fileName");safeName(name,false);if(name.indexOf('/')>=0)throw error("recipe");
        }else{
            String endpoint=switch(recipe.provider()){
                case "mimo"->"https://api.xiaomimimo.com/v1/chat/completions";
                case "minimax-cn"->"https://api.minimax.cn/v1/t2a_v2";
                case "minimax-intl"->"https://api.minimax.io/v1/t2a_v2";
                default->"";
            };
            if(!endpoint.equals(recipe.parameters().get("endpoint")))throw error("recipe");
        }
    }
    private static JsonObject recipeJson(AudioLibrary.Recipe recipe){
        JsonObject json=new JsonObject();json.addProperty("kind",recipe.kind());json.addProperty("text",recipe.text());json.addProperty("spokenText",recipe.spokenText());json.addProperty("locale",recipe.locale());json.addProperty("voice",recipe.voice());json.addProperty("provider",recipe.provider());json.addProperty("account",recipe.account());json.addProperty("model",recipe.model());json.addProperty("sampleResource",recipe.sampleResource());
        JsonObject params=new JsonObject();recipe.parameters().forEach(params::addProperty);json.add("parameters",params);JsonArray parts=new JsonArray();recipe.parts().forEach(parts::add);json.add("parts",parts);return json;
    }
    private static Binding readBinding(JsonObject json) throws IOException {return binding(string(json,"text",4096),string(json,"locale",35),string(json,"voice",256));}
    private static Binding binding(String text,String locale,String voice) throws IOException {if(text==null||text.isBlank()||text.length()>4096)throw error("recipe");return new Binding(text,locale(locale),voice(voice));}
    private static JsonObject bindingJson(Binding binding){JsonObject j=new JsonObject();j.addProperty("text",binding.text());j.addProperty("locale",binding.locale());j.addProperty("voice",binding.voice());return j;}
    private static String locale(String value) throws IOException {if(value==null||value.length()>35||!value.matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*"))throw error("recipe");Locale locale=Locale.forLanguageTag(value);if(locale.getLanguage().isEmpty()&&!value.equalsIgnoreCase("und"))throw error("recipe");return locale.toLanguageTag();}
    private static String voice(String value) throws IOException {if(value==null||value.length()>256||value.contains("..")||!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw error("recipe");return value;}
    private static String stem(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}

    private static synchronized BindingIndex index(AudioCacheStore cache) throws IOException {
        var known=INDEXES.get(cache.root());if(known!=null)return known;BindingIndex loaded=new BindingIndex();Path file=cache.root().resolve(INDEX);
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw error("cache_unsafe");
        if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){
            JsonObject json=parse(readFile(file,MAX_MANIFEST_BYTES)).getAsJsonObject();if(!version(json))throw error("invalid");readTargets(json,loaded,null);
        }else{
            int count=0;try(var dirs=Files.newDirectoryStream(cache.root())){for(Path dir:dirs){if(++count>MAX_SCAN)throw error("limit");if(!dir.getFileName().toString().matches("[0-9a-f]{64}")||!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))continue;
                Path binding=dir.resolve(INDEX);if(!Files.isRegularFile(binding,LinkOption.NOFOLLOW_LINKS))continue;JsonObject json=parse(readFile(binding,MAX_MANIFEST_BYTES)).getAsJsonObject();if(!version(json))throw error("invalid");readTargets(json,loaded,dir.getFileName().toString());
            }}
            if(!loaded.targets.isEmpty())saveIndex(cache,loaded);
        }
        INDEXES.put(cache.root(),loaded);return loaded;
    }
    private static void readTargets(JsonObject json,BindingIndex index,String requiredKey) throws IOException {
        if(!"fangsu-audio-pack-bindings".equals(string(json,"format",64)))throw error("schema");
        JsonArray rows=json.getAsJsonArray("bindings");if(rows==null||rows.size()>MAX_BINDINGS)throw error("binding_limit");
        for(var value:rows){var row=value.getAsJsonObject();String key=string(row,"key",64);validKey(key);if(requiredKey!=null&&!requiredKey.equals(key))throw error("invalid");
            Binding b=readBinding(row);String source=string(row,"source",255);if(source.indexOf('/')>=0||source.indexOf('\\')>=0)throw error("path");
            Target previous=index.targets.putIfAbsent(b,new Target(key,source));if(previous!=null&&!previous.key().equals(key))throw error("conflict");if(index.targets.size()>MAX_BINDINGS)throw error("binding_limit");
        }
    }
    private static List<Binding> readClipBindings(AudioCacheStore cache,String key) throws IOException {
        BindingIndex all=index(cache);List<Binding> result=new ArrayList<>();all.targets.forEach((binding,target)->{if(target.key().equals(key))result.add(binding);});return result;
    }
    private static void saveClipBindings(AudioCacheStore cache,String key,BindingIndex index) throws IOException {safeDirectory(cache,key);saveBindings(cache.root().resolve(key).resolve(INDEX),index,key);}
    private static void saveIndex(AudioCacheStore cache,BindingIndex index) throws IOException {saveBindings(cache.root().resolve(INDEX),index,null);}
    private static void saveBindings(Path file,BindingIndex index,String only) throws IOException {
        if(Files.isSymbolicLink(file))throw error("cache_unsafe");JsonArray rows=new JsonArray();
        index.targets.forEach((binding,target)->{if(only==null||target.key().equals(only)){JsonObject row=bindingJson(binding);row.addProperty("key",target.key());row.addProperty("source",target.source());rows.add(row);}});
        JsonObject json=new JsonObject();json.addProperty("schemaVersion",1);json.addProperty("format","fangsu-audio-pack-bindings");json.add("bindings",rows);byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_MANIFEST_BYTES)throw error("binding_limit");AudioCacheStore.atomic(file,bytes);
    }
    private static void safeDirectory(AudioCacheStore cache,String key) throws IOException {
        validKey(key);Path dir=cache.root().resolve(key).toAbsolutePath().normalize(),realRoot=cache.root().toRealPath();
        if(!dir.getParent().equals(cache.root())||Files.isSymbolicLink(dir)||Files.exists(dir,LinkOption.NOFOLLOW_LINKS)&&(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||!dir.toRealPath().getParent().equals(realRoot)))throw error("cache_unsafe");
        for(String name:List.of("library.json","current.json","deleted",INDEX))if(Files.isSymbolicLink(dir.resolve(name)))throw error("cache_unsafe");
    }
    private static Map<String,ZipEntry> archiveEntries(ZipFile zip) throws IOException {
        Map<String,ZipEntry> entries=new HashMap<>();long total=0;
        for(var iterator=zip.entries();iterator.hasMoreElements();){var e=iterator.nextElement();safeName(e.getName(),e.isDirectory());if(entries.putIfAbsent(e.getName(),e)!=null)throw error("duplicate");
            if(entries.size()>MAX_CLIPS+65||e.getSize()<0||e.getCompressedSize()<0||(total+=e.getSize())>MAX_TOTAL_BYTES+MAX_MANIFEST_BYTES)throw error("limit");
            if(e.getMethod()!=ZipEntry.STORED&&e.getMethod()!=ZipEntry.DEFLATED)throw error("invalid");
        }return entries;
    }
    private static void safeName(String name,boolean directory) throws IOException {
        if(name==null||name.isEmpty()||name.length()>1024||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.chars().anyMatch(c->c<32||c==127))throw error("path");
        String test=directory&&name.endsWith("/")?name.substring(0,name.length()-1):name;for(String part:test.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw error("path");
    }
    /** 不依赖 JDK 私有 ZipEntry 字段，读取中央目录以拒绝加密、分卷及 UNIX 符号链接条目。 */
    private static void validateCentralDirectory(Path source) throws IOException {
        try(FileChannel channel=FileChannel.open(source,StandardOpenOption.READ)){
            long length=channel.size();if(length<22)throw error("invalid");int tailSize=(int)Math.min(length,65557);ByteBuffer tail=ByteBuffer.allocate(tailSize).order(ByteOrder.LITTLE_ENDIAN);read(channel,tail,length-tailSize);int end=-1;
            for(int p=tailSize-22;p>=0;p--)if(tail.getInt(p)==0x06054b50&&p+22+Short.toUnsignedInt(tail.getShort(p+20))==tailSize){end=p;break;}
            if(end<0||tail.getShort(end+4)!=0||tail.getShort(end+6)!=0)throw error("invalid");int count=Short.toUnsignedInt(tail.getShort(end+10));long size=Integer.toUnsignedLong(tail.getInt(end+12)),offset=Integer.toUnsignedLong(tail.getInt(end+16));
            if(count!=Short.toUnsignedInt(tail.getShort(end+8))||count>MAX_CLIPS+65||size>16L*1024*1024||offset+size>length-tailSize+end)throw error("limit");
            long position=offset;
            for(int i=0;i<count;i++){
                ByteBuffer entry=ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);read(channel,entry,position);if(entry.getInt(0)!=0x02014b50)throw error("invalid");
                int made=Short.toUnsignedInt(entry.getShort(4)),flags=Short.toUnsignedInt(entry.getShort(8)),method=Short.toUnsignedInt(entry.getShort(10));long attributes=Integer.toUnsignedLong(entry.getInt(38));int type=(int)(attributes>>>16)&0xf000;
                if((flags&1)!=0||method!=ZipEntry.STORED&&method!=ZipEntry.DEFLATED||entry.getShort(34)!=0||made>>>8==3&&type!=0&&type!=0x8000&&type!=0x4000)throw error("invalid");
                int name=Short.toUnsignedInt(entry.getShort(28)),extra=Short.toUnsignedInt(entry.getShort(30)),comment=Short.toUnsignedInt(entry.getShort(32));if(name>1024||(position+=46L+name+extra+comment)>offset+size)throw error("limit");
            }
            if(position!=offset+size)throw error("invalid");
        }
    }
    private static void read(FileChannel channel,ByteBuffer buffer,long position) throws IOException {while(buffer.hasRemaining()){int n=channel.read(buffer,position+buffer.position());if(n<=0)throw error("invalid");}buffer.flip();}
    private static byte[] read(ZipFile zip,ZipEntry entry,int limit) throws IOException {if(entry.getSize()>limit)throw error("limit");try(InputStream input=zip.getInputStream(entry)){byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit||bytes.length!=entry.getSize())throw error("limit");return bytes;}}
    private static byte[] readFile(Path path,int limit) throws IOException {if(!Files.isRegularFile(path)||Files.size(path)>limit)throw error("limit");try(var input=Files.newInputStream(path)){byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw error("limit");return bytes;}}
    private static void write(ZipOutputStream zip,String name,byte[] bytes) throws IOException {ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();}
    private static boolean version(JsonObject json){return json.has("schemaVersion")&&json.get("schemaVersion").isJsonPrimitive()&&json.getAsJsonPrimitive("schemaVersion").isNumber()&&json.get("schemaVersion").getAsBigDecimal().compareTo(BigDecimal.ONE)==0;}
    private static String string(JsonObject json,String field,int limit) throws IOException {if(!json.has(field))throw error("invalid");return primitive(json.get(field),limit);}
    private static String primitive(JsonElement value,int limit) throws IOException {if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw error("invalid");String text=value.getAsString();if(text.length()>limit)throw error("limit");return text;}
    private static void validKey(String key) throws IOException {if(key==null||!key.matches("[0-9a-f]{64}"))throw error("recipe");}
    private static IOException error(String reason){return new IOException("audio_pack_"+reason);}
    private static String reason(Exception error){String message=error.getMessage();return message!=null&&(message.startsWith("audio_pack_")||message.startsWith("library_")||message.startsWith("invalid_")||message.startsWith("unsupported_")||message.startsWith("truncated_")||message.startsWith("missing_")||message.startsWith("audio_"))?message:"audio_pack_io";}
    private static JsonElement parse(byte[] bytes) throws IOException {
        try(JsonReader reader=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8))){reader.setLenient(false);int[] count={0};JsonElement result=json(reader,0,count);if(reader.peek()!=JsonToken.END_DOCUMENT)throw error("invalid");return result;}
        catch(IllegalStateException|NumberFormatException e){throw new IOException("audio_pack_invalid",e);}
    }
    private static JsonElement json(JsonReader reader,int depth,int[] count) throws IOException {
        if(depth>16||++count[0]>500000)throw error("limit");
        switch(reader.peek()){
            case BEGIN_OBJECT:{JsonObject object=new JsonObject();reader.beginObject();while(reader.hasNext()){String name=reader.nextName();if(name.length()>128||object.has(name)||object.size()>=256)throw error("duplicate");object.add(name,json(reader,depth+1,count));}reader.endObject();return object;}
            case BEGIN_ARRAY:{JsonArray array=new JsonArray();reader.beginArray();while(reader.hasNext()){if(array.size()>=MAX_BINDINGS)throw error("limit");array.add(json(reader,depth+1,count));}reader.endArray();return array;}
            case STRING:{String value=reader.nextString();if(value.length()>65536)throw error("limit");return new JsonPrimitive(value);}
            case NUMBER:{String value=reader.nextString();if(value.length()>64)throw error("limit");return new JsonPrimitive(new BigDecimal(value));}
            case BOOLEAN:return new JsonPrimitive(reader.nextBoolean());
            case NULL:reader.nextNull();return JsonNull.INSTANCE;
            default:throw error("invalid");
        }
    }
}
