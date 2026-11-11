package com.fangsu.announcements.ui;

import com.fangsu.announcements.cache.AudioPack;
import com.fangsu.mappings.ComponentHelper;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.server.packs.repository.PackRepository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** ZIP 按根清单分流：离线片段包进入音频缓存；Minecraft 资源包按原生资源包语义安装。 */
public final class AnnouncementArchiveImport {
    public enum Kind { AUDIO, RESOURCE }
    public record Installed(String name,boolean enabled) { }
    private record Snapshot(Path file,String name) { }
    private static final long MAX_BYTES=1024L*1024*1024;
    private static final int MAX_ENTRIES=65536,MAX_META=65536;
    private static final ExecutorService FILES=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"fangsu-pack-import");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private AnnouncementArchiveImport() { }
    public static boolean archive(Path file){return file.getFileName()!=null&&file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");}
    public static CompletableFuture<Kind> inspect(Path source){return submit(()->classify(source));}
    private static Kind classify(Path source) throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>MAX_BYTES+16L*1024*1024)throw error("resource_pack_limit");
        try(ZipFile zip=new ZipFile(source.toFile(),StandardCharsets.UTF_8)){
            Set<String> names=new HashSet<>();boolean assets=false;long total=0;int count=0;
            for(var entries=zip.entries();entries.hasMoreElements();){var entry=entries.nextElement();String name=entry.getName();
                if(++count>MAX_ENTRIES||entry.getSize()<0||entry.getSize()>MAX_BYTES)throw error("resource_pack_limit");
                if(name.length()>512||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.chars().anyMatch(c->c<32||c==127)||!names.add(name))throw error("resource_pack_path");
                String[] parts=name.split("/",-1);for(int i=0;i<parts.length;i++)if(parts[i].equals(".")||parts[i].equals("..")||parts[i].isEmpty()&&!(i==parts.length-1&&entry.isDirectory()))throw error("resource_pack_path");
                if((total+=entry.getSize())>MAX_BYTES+16L*1024*1024)throw error("resource_pack_limit");
                if(!entry.isDirectory()&&name.startsWith("assets/"))assets=true;
            }
            boolean audio=false;var manifest=zip.getEntry("manifest.json");
            if(manifest!=null&&!manifest.isDirectory()){
                try{var root=JsonParser.parseString(read(zip,manifest,AudioPack.MAX_MANIFEST_BYTES)).getAsJsonObject();audio=root.has("format")&&root.get("format").isJsonPrimitive()&&AudioPack.FORMAT.equals(root.get("format").getAsString());}
                catch(RuntimeException ignored){/* 普通资源包可能自带其他用途的 manifest.json。 */}
            }
            var metadata=zip.getEntry("pack.mcmeta");boolean resource=false;
            if(metadata!=null&&!metadata.isDirectory()&&assets){
                try{var root=JsonParser.parseString(read(zip,metadata,MAX_META)).getAsJsonObject();var pack=root.getAsJsonObject("pack");
                    resource=pack!=null&&pack.has("pack_format")&&pack.get("pack_format").isJsonPrimitive()&&pack.get("pack_format").getAsJsonPrimitive().isNumber()
                            &&pack.get("pack_format").getAsBigDecimal().intValueExact()>0&&pack.has("description")&&!pack.get("description").isJsonNull();
                }catch(RuntimeException e){if(!audio)throw error("resource_pack_invalid");}
            }
            if(audio&&resource)throw error("pack_type_ambiguous");
            if(resource)return Kind.RESOURCE;if(audio)return Kind.AUDIO;
            throw error(metadata==null?"pack_type_unknown":"resource_pack_invalid");
        }catch(ZipException|IllegalArgumentException e){throw error("pack_type_unknown");}
    }
    private static String read(ZipFile zip,ZipEntry entry,int maximum) throws IOException {
        if(entry.getSize()<0||entry.getSize()>maximum)throw error("resource_pack_limit");
        try(InputStream input=zip.getInputStream(entry)){byte[] data=input.readNBytes(maximum+1);if(data.length>maximum)throw error("resource_pack_limit");return new String(data,StandardCharsets.UTF_8);}
    }
    public static CompletableFuture<Installed> install(Path source){
        Minecraft client=Minecraft.getInstance();Path directory=directory(client);
        CompletableFuture<Installed> result=new CompletableFuture<>();
        submit(()->copy(source,directory)).whenComplete((snapshot,error)->client.execute(()->{
            if(error!=null){result.completeExceptionally(error);return;}
            activate(client,snapshot,result);
        }));return result;
    }
    private static Snapshot copy(Path source,Path directory) throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>MAX_BYTES+16L*1024*1024)throw error("resource_pack_limit");
        Files.createDirectories(directory);Path temporary=Files.createTempFile(directory,".fangsu-import-",".tmp");
        try{
            try(InputStream input=Files.newInputStream(source);OutputStream output=Files.newOutputStream(temporary)){
                byte[] buffer=new byte[65536];int read;long bytes=0;while((read=input.read(buffer))!=-1){if((bytes+=read)>MAX_BYTES+16L*1024*1024)throw error("resource_pack_limit");output.write(buffer,0,read);}
            }
            if(classify(temporary)!=Kind.RESOURCE)throw error("resource_pack_invalid");
            String name=source.getFileName().toString().replaceAll("[^\\p{L}\\p{N}._ -]","_");
            if(name.length()>120)name=name.substring(0,116)+".zip";if(!name.toLowerCase(Locale.ROOT).endsWith(".zip"))name+=".zip";
            String stem=name.substring(0,name.length()-4),hash=digest(temporary);
            for(int number=0;number<10000;number++){
                String candidate=number==0?name:stem+" ("+number+").zip";Path target=directory.resolve(candidate).toAbsolutePath().normalize();
                if(!target.getParent().equals(directory.toAbsolutePath().normalize()))throw error("resource_pack_path");
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                    if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Files.size(target)==Files.size(temporary)&&hash.equals(digest(target)))return new Snapshot(target,candidate);
                    continue;
                }
                try{Files.move(temporary,target);return new Snapshot(target,candidate);}catch(FileAlreadyExistsException ignored){/* 同名导入同时到达时选下一个后缀，不覆盖现有包。 */}
            }
            throw error("resource_pack_failed");
        }finally{Files.deleteIfExists(temporary);}
    }
    private static String digest(Path path) throws IOException {
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private static void activate(Minecraft client,Snapshot snapshot,CompletableFuture<Installed> result){
        PackRepository repository=client.getResourcePackRepository();List<String> previous=new ArrayList<>(repository.getSelectedIds());boolean selecting=false;
        try{
            repository.reload();String id="file/"+snapshot.name();var pack=repository.getPack(id);if(pack==null)throw error("resource_pack_unavailable");
            if(!pack.getCompatibility().isCompatible()){result.complete(new Installed(snapshot.name(),false));return;}
            List<String> selected=new ArrayList<>(repository.getSelectedIds());if(!selected.contains(id))selected.add(id);selecting=true;repository.setSelected(selected);saveSelection(client,repository);
            client.reloadResourcePacks().whenComplete((ignored,error)->client.execute(()->{
                if(error==null&&repository.getSelectedIds().contains(id)){result.complete(new Installed(snapshot.name(),true));return;}
                restoreSelection(client,repository,previous,result);
            }));
        }catch(IOException|RuntimeException e){if(selecting)restoreSelection(client,repository,previous,result);else result.completeExceptionally(e);}
    }
    private static void restoreSelection(Minecraft client,PackRepository repository,List<String> previous,CompletableFuture<Installed> result){
        client.execute(()->{
            try{repository.setSelected(previous);saveSelection(client,repository);client.reloadResourcePacks().whenComplete((ignored,error)->result.completeExceptionally(error("resource_pack_reload_failed")));}
            catch(RuntimeException e){result.completeExceptionally(error("resource_pack_reload_failed"));}
        });
    }
    /** 旧版本返回 File，新版本返回 Path；归一化后保留用户实际资源包目录。 */
    private static Path directory(Minecraft client){Object directory=client.getResourcePackDirectory();return directory instanceof Path path?path:((File)directory).toPath();}
    /** 对齐原版 Options.updateResourcePacks 的选择保存逻辑，并显式等待重载完成。 */
    private static void saveSelection(Minecraft client,PackRepository repository){
        client.options.resourcePacks.clear();client.options.incompatibleResourcePacks.clear();
        for(var pack:repository.getSelectedPacks())if(!pack.isFixedPosition()){
            client.options.resourcePacks.add(pack.getId());if(!pack.getCompatibility().isCompatible())client.options.incompatibleResourcePacks.add(pack.getId());
        }
        client.options.save();
    }
    public static void openResourcePacks(Screen parent,Runnable applied,java.util.function.Consumer<Throwable> failed){
        Minecraft client=Minecraft.getInstance();var repository=client.getResourcePackRepository();repository.reload();List<String> previous=new ArrayList<>(repository.getSelectedIds());
        java.util.function.Consumer<PackRepository> selected=packs->{
            client.setScreen(parent);List<String> expected=new ArrayList<>(packs.getSelectedIds());
            CompletableFuture<Installed> update=new CompletableFuture<>();
            update.whenComplete((ignored,error)->client.execute(()->{if(error==null)applied.run();else failed.accept(error);}));
            try{
                saveSelection(client,packs);if(expected.equals(previous)){update.complete(new Installed("",false));return;}
                client.reloadResourcePacks().whenComplete((ignored,error)->client.execute(()->{
                    if(error==null&&packs.getSelectedIds().containsAll(expected))update.complete(new Installed("",true));
                    else restoreSelection(client,packs,previous,update);
                }));
            }catch(RuntimeException e){restoreSelection(client,packs,previous,update);}
        };
        //#if MC_VERSION >= 11904
        Screen screen=new PackSelectionScreen(repository,selected,directory(client),ComponentHelper.translatable("resourcePack.title"));
        //#elseif MC_VERSION <= 11902
        //$$ Screen screen=new PackSelectionScreen(parent,repository,selected,directory(client).toFile(),ComponentHelper.translatable("resourcePack.title"));
        //#else
        //$$ Screen screen=transitionalScreen(parent,repository,selected,directory(client));
        //#endif
        client.setScreen(screen);
    }
    /** 1.19.3 过渡分支按原生公开签名选构造，避免把 File / Path 或父页面参数变化硬套到该版本。 */
    private static Screen transitionalScreen(Screen parent,PackRepository repository,java.util.function.Consumer<PackRepository> selected,Path directory){
        var title=ComponentHelper.translatable("resourcePack.title");
        try{
            for(var constructor:PackSelectionScreen.class.getConstructors()){
                var types=constructor.getParameterTypes();
                if(types.length==4&&types[0]==PackRepository.class&&types[1]==java.util.function.Consumer.class&&types[3]==net.minecraft.network.chat.Component.class){
                    if(types[2]==Path.class)return (Screen)constructor.newInstance(repository,selected,directory,title);
                    if(types[2]==File.class)return (Screen)constructor.newInstance(repository,selected,directory.toFile(),title);
                }
                if(types.length==5&&types[0]==Screen.class&&types[1]==PackRepository.class&&types[2]==java.util.function.Consumer.class&&types[4]==net.minecraft.network.chat.Component.class){
                    if(types[3]==Path.class)return (Screen)constructor.newInstance(parent,repository,selected,directory,title);
                    if(types[3]==File.class)return (Screen)constructor.newInstance(parent,repository,selected,directory.toFile(),title);
                }
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(error("resource_pack_unavailable"));}
        throw new IllegalStateException(error("resource_pack_unavailable"));
    }
    private static <T> CompletableFuture<T> submit(Callable<T> operation){
        try{return CompletableFuture.supplyAsync(()->{try{return operation.call();}catch(Exception e){throw new CompletionException(e);}},FILES);}
        catch(RejectedExecutionException e){return CompletableFuture.failedFuture(error("resource_pack_failed"));}
    }
    private static IOException error(String reason){return new IOException(reason);}
}
