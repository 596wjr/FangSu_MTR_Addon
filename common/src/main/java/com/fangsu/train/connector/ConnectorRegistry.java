package com.fangsu.train.connector;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.event.events.client.ClientTickEvent;
import java.util.*;

/** 方速来源优先；两种车型格式同时读取，共享表只在其所属根对象内解析。 */
public final class ConnectorRegistry {
    private static final Map<String, ConnectorSet> TRAINS = new LinkedHashMap<>();
    private static final Map<String, ConnectorSet> TEXTURES = new HashMap<>();
    private static final Map<String, ConnectorDef> DEFINITIONS = new HashMap<>();
    private static final List<double[]> DIMENSIONS = new ArrayList<>();
    private static final List<ConnectorSet> DIMENSION_DEFS = new ArrayList<>();
    private static final Map<ConnectorDef, Double> NOMINAL = new IdentityHashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static final Set<String> CLAIMED = new HashSet<>();
    private static final List<Runnable> RESET = new ArrayList<>();
    private static boolean installed;
    private static Object lastWorld;
    private ConnectorRegistry() {}
    public static void warn(String key, String message) { if (WARNED.add(key)) Main.LOGGER.warn("[FangSu/Connector] {}", message); }
    public static void onReset(Runnable reset) { RESET.add(reset); }
    public static void resetRuntime() {
        NOMINAL.clear(); WARNED.clear(); ConnectorFoldProfile.clear();
        RESET.forEach(Runnable::run);
    }
    public static void reload() {
        resetRuntime(); TRAINS.clear(); TEXTURES.clear(); CLAIMED.clear(); DEFINITIONS.clear(); DIMENSIONS.clear(); DIMENSION_DEFS.clear();
        if (!installed) {
            installed=true;
            ClientTickEvent.CLIENT_POST.register(client -> {
                if (lastWorld != client.level) { lastWorld=client.level; resetRuntime(); }
            });
        }
        for (String path : new String[]{"fangsu:custom_trains.json", "mtr:mtr_custom_resources.json"}) {
            try {
                JsonElement el=ResourceUtil.loadAsJSON(new ResourceLocation(path));
                if (el != null && el.isJsonObject()) read(el.getAsJsonObject());
            } catch (Exception e) { warn(path, "无法读取 " + path + ": " + e.getMessage()); }
        }
        Main.LOGGER.info("[FangSu/Connector] 已加载 {} 个车型定义、{} 个贴图键", TRAINS.size(), TEXTURES.size());
    }
    private static void read(JsonObject root) {
        JsonObject shared=root.has("connectors") && root.get("connectors").isJsonObject() ? root.getAsJsonObject("connectors") : new JsonObject();
        if (root.has("vehicles") && root.get("vehicles").isJsonArray()) for (JsonElement e:root.getAsJsonArray("vehicles")) {
            if (e.isJsonObject()) { JsonObject o=e.getAsJsonObject(); if (o.has("id")) add(o.get("id").getAsString(),o,shared); }
        }
        if (root.has("custom_trains") && root.get("custom_trains").isJsonObject()) for (Map.Entry<String,JsonElement> e:root.getAsJsonObject("custom_trains").entrySet()) {
            if (e.getValue().isJsonObject()) add(e.getKey(),e.getValue().getAsJsonObject(),shared);
        }
    }
    private static void add(String key, JsonObject train, JsonObject shared) {
        if (!train.has("connector") || !CLAIMED.add(key)) return;
        try {
            ConnectorSet set=ConnectorSet.parse(train.get("connector"),key,shared,DEFINITIONS);
            if(set==null) return;
            TRAINS.put(key,set);
            // 优先用车型原生 model properties 的识别键。内饰共享贴图不能反向接管无配置车型。
            boolean indexed=false;
            if(train.has("models") && train.get("models").isJsonArray()) for(JsonElement model:train.getAsJsonArray("models")) {
                if(!model.isJsonObject() || !model.getAsJsonObject().has("modelPropertiesResource")) continue;
                String path=model.getAsJsonObject().get("modelPropertiesResource").getAsString();
                if(!path.endsWith(".json")) path+=".json";
                JsonElement resource=ResourceUtil.loadAsJSON(new ResourceLocation(path));
                if(resource==null || !resource.isJsonObject()) continue;
                JsonObject props=resource.getAsJsonObject();
                String texture=ConnectorDef.string(props,"gangwayOuterSideResource","");
                if(texture.isEmpty()) texture=ConnectorDef.string(props,"gangwayInnerSideResource","");
                if(!texture.isEmpty()) { if(!texture.endsWith(".png")) texture+=".png"; index(texture,set);indexed=true; }
            }
            if(!indexed) for(ConnectorDef d:set.definitions) if((d.mask()&ConnectorSet.EXTERIOR)!=0) index(d.texture.toString(),set);
            // MTR4 无 gangway 贴图时，以车长及 model properties 的四个尺寸作精确兜底。
            if(train.has("length") && train.has("models") && train.get("models").isJsonArray()) for(JsonElement model:train.getAsJsonArray("models")) {
                if(!model.isJsonObject()) continue;
                JsonObject m=model.getAsJsonObject();
                if(!m.has("modelPropertiesResource")) continue;
                String path=m.get("modelPropertiesResource").getAsString();
                if(!path.endsWith(".json")) path+=".json";
                JsonElement props=ResourceUtil.loadAsJSON(new ResourceLocation(path));
                if(props==null || !props.isJsonObject()) continue;
                JsonObject prop=props.getAsJsonObject();
                if(!ConnectorDef.string(prop,"gangwayOuterSideResource","").isEmpty() || !ConnectorDef.string(prop,"gangwayInnerSideResource","").isEmpty()) continue;
                double[] dims={ConnectorDef.finite(train.get("length")),ConnectorDef.number(prop,"gangwayWidth",0),ConnectorDef.number(prop,"gangwayHeight",0),ConnectorDef.number(prop,"gangwayYOffset",0),ConnectorDef.number(prop,"gangwayZOffset",0)};
                boolean duplicate=false;
                for(int i=0;i<DIMENSIONS.size();i++) if(Arrays.equals(DIMENSIONS.get(i),dims)) {
                    duplicate=true;
                    if(!DIMENSION_DEFS.get(i).signature.equals(set.signature)) warn("dimensions:"+Arrays.toString(dims),"风挡尺寸键冲突，保留首次定义 " + DIMENSION_DEFS.get(i).signature);
                    break;
                }
                if(!duplicate) { DIMENSIONS.add(dims); DIMENSION_DEFS.add(set); }
            }
        } catch (Exception e) { warn("definition:"+key,"车型 " + key + " 的 connector 无效，回退原生: " + e.getMessage()); }
    }
    private static void index(String texture,ConnectorSet set) {
        ConnectorSet old=TEXTURES.putIfAbsent(texture,set);
        if(old!=null && !old.signature.equals(set.signature)) warn("texture:"+texture,"风挡识别贴图冲突，保留首次配置: "+texture);
    }
    public static ConnectorSet train(String key) { return TRAINS.get(key); }
    public static ConnectorSet texture(String key) { return TEXTURES.get(key); }
    public static ConnectorSet dimensions(double length,double width,double height,double y,double z) {
        for(int i=0;i<DIMENSIONS.size();i++) { double[] a=DIMENSIONS.get(i); if(a[0]==length && a[1]==width && a[2]==height && a[3]==y && a[4]==z) return DIMENSION_DEFS.get(i); }
        return null;
    }
    public static Set<String> trainKeys() { return Collections.unmodifiableSet(CLAIMED); }
    public static double nominal(ConnectorDef def, double distance) {
        if(def.nominalLength>0) return def.nominalLength;
        if(!Double.isFinite(distance) || distance<1e-4) return distance;
        return NOMINAL.computeIfAbsent(def,k->distance);
    }
}
