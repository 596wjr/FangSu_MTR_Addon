package com.fangsu.drawing.diaoban;

import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.BasicModelSelectInfo;
import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Supplier;

/** 以脚本路径为主键，旧方案 ID 为别名；资源重载完成前保留旧的完整目录。 */
public final class DiaobanDrawManager {
    private static final ResourceLocation SCRIPTS_LOCATION=new ResourceLocation("fangsu:diaoban/diaoban_scripts.json");
    private record Registry(Map<String,DiaobanDrawItem> entries,Map<String,String> aliases) { }
    private static final Map<String,DiaobanDrawItem> JAVA_DRAWINGS=new LinkedHashMap<>();
    private static volatile Registry registry=builtins();
    private static Registry builtins(){
        var entries=new LinkedHashMap<String,DiaobanDrawItem>();var aliases=new LinkedHashMap<String,String>();
        for(String id:List.of("mtr_diaoban_station","mtr_diaoban_route","blank")){
            String path="fangsu:diaoban/"+id+".js";JsonObject settings=new JsonObject(),script=new JsonObject();script.addProperty("max_select",1);settings.add("script_settings",script);
            entries.put(path,new DiaobanDrawItem("fangsu.diaoban."+id,path,"fangsu.diaoban."+id+".description",settings,()->new JsDiaobanDrawing(path)));aliases.put(id,path);
        }
        return snapshot(entries,aliases);
    }
    private static Registry snapshot(Map<String,DiaobanDrawItem> entries,Map<String,String> aliases){return new Registry(Collections.unmodifiableMap(new LinkedHashMap<>(entries)),Map.copyOf(aliases));}
    public static synchronized void preload(){
        var defaults=builtins();var entries=new LinkedHashMap<>(defaults.entries());var aliases=new LinkedHashMap<>(defaults.aliases());
        Set<ResourceLocation> catalogs=new LinkedHashSet<>();catalogs.add(SCRIPTS_LOCATION);
        try{catalogs.addAll(ResourceUtil.listResources(null,"diaoban",List.of("diaoban_scripts.json")));}catch(RuntimeException e){Main.LOGGER.warn("[FangSu] Unable to list extra overhead board catalogs",e);}
        for(var catalog:catalogs)try{
            var loaded=ResourceUtil.loadAsJSON(catalog);if(!loaded.isJsonObject())continue;var json=loaded.getAsJsonObject();if(!json.has("content")||!json.get("content").isJsonArray())continue;
            for(var row:json.getAsJsonArray("content"))try{
                if(!row.isJsonObject())continue;var item=row.getAsJsonObject();String id=item.get("id").getAsString(),text=item.get("text").getAsString();
                String path=new ResourceLocation(item.get("content").getAsString()).toString();if(id.isBlank()||id.length()>256||path.length()>256||path.endsWith(":")||path.contains(".."))throw new IllegalArgumentException("invalid_board_drawing");
                String description=item.has("contentText")?item.get("contentText").getAsString():entries.containsKey(path)?entries.get(path).getContentText():path;
                entries.put(path,new DiaobanDrawItem(text,path,description,item,()->new JsDiaobanDrawing(path)));aliases.put(id,path);
            }catch(RuntimeException e){Main.LOGGER.warn("[FangSu] Ignoring an invalid overhead board drawing in {}",catalog,e);}
        }catch(Exception e){Main.LOGGER.error("[FangSu] Failed to load overhead board catalog {}",catalog,e);}
        entries.putAll(JAVA_DRAWINGS);
        registry=snapshot(entries,aliases);
    }
    public static List<ModelSelectInfo> getDrawOptions(){return List.copyOf(registry.entries().values());}
    public static JsonObject getScriptSettingsByDrawKey(String key){var item=find(key);if(item==null)return new JsonObject();var settings=item.settings();return settings.has("script_settings")&&settings.get("script_settings").isJsonObject()?settings.getAsJsonObject("script_settings").deepCopy():new JsonObject();}
    private static DiaobanDrawItem find(String key) {
        if (key == null) return null;
        var view = registry;
        var direct = view.entries().get(key);
        return direct != null ? direct : view.entries().get(view.aliases().get(key));
    }
    public DiaobanDrawItem getDrawItem(String key){return find(key);}
    public static synchronized void registerJavaDrawing(String text,String key,String description,Supplier<BaseDiaobanDrawing> factory){
        var item=new DiaobanDrawItem(text,key,description,new JsonObject(),factory);
        JAVA_DRAWINGS.put(key,item);
        var view=registry;var entries=new LinkedHashMap<>(view.entries());entries.put(key,item);registry=snapshot(entries,view.aliases());
    }
    public static BaseDiaobanDrawing createDrawing(String key){var item=find(key);return item!=null&&item.supplier()!=null?item.supplier().get():new JsDiaobanDrawing(key);}
    public static final class DiaobanDrawItem extends BasicModelSelectInfo {
        private final Supplier<BaseDiaobanDrawing> supplier;private final JsonObject settings;
        public DiaobanDrawItem(String text,String key,String description,JsonObject settings,Supplier<BaseDiaobanDrawing> supplier){super(text,key,description);this.settings=settings.deepCopy();this.supplier=supplier;}
        public Supplier<BaseDiaobanDrawing> supplier(){return supplier;}
        public JsonObject settings(){return settings.deepCopy();}
    }
    private DiaobanDrawManager(){ }
}
