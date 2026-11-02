package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 从实际轨道定义恢复来源与文件内顺序，不依赖 NTE 注册表的 HashMap 遍历顺序。 */
final class NteRailModelCatalog {
    static final String BUILTIN = "mtrsteamloco:rails/builtin.json";
    static final String OTHER = "fangsu:unlisted_rail_models";

    record Model(String key, Component name, String source, int order) { }
    private record Definition(String source, int order) { }

    private NteRailModelCatalog() { }

    static Map<String, List<Model>> load(Map<String, Component> models) {
        Map<String, Definition> definitions = readDefinitions();
        definitions.put("", new Definition(BUILTIN, -2));
        definitions.put("null", new Definition(BUILTIN, -1));
        List<Model> ordered = new ArrayList<>();
        for (Map.Entry<String, Component> entry : models.entrySet()) {
            Definition definition = definitions.getOrDefault(entry.getKey(), new Definition(OTHER, Integer.MAX_VALUE));
            ordered.add(new Model(entry.getKey(), entry.getValue(), definition.source(), definition.order()));
        }
        ordered.sort(Comparator.comparingInt((Model model) -> model.source().equals(BUILTIN) ? 0 : model.source().equals(OTHER) ? 2 : 1)
                .thenComparing(Model::source, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Model::order)
                .thenComparing(model -> model.name().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Model::key));
        Map<String, List<Model>> result = new LinkedHashMap<>();
        for (Model model : ordered) result.computeIfAbsent(model.source(), ignored -> new ArrayList<>()).add(model);
        return result;
    }

    static String sourceName(String source) {
        return OTHER.equals(source) ? ComponentHelper.translatableString("ui.fangsu.nte_rail.other_sources") : source;
    }

    private static Map<String, Definition> readDefinitions() {
        Map<String, Definition> result = new HashMap<>();
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        //#if MC_VERSION >= 11900
        Map<ResourceLocation, List<Resource>> resources = manager.listResourceStacks("rails",
                location -> location.getNamespace().equals("mtrsteamloco") && location.getPath().endsWith(".json"));
        //#else
        //$$ Map<ResourceLocation, List<Resource>> resources = new HashMap<>();
        //$$ for (ResourceLocation location : manager.listResources("rails", path -> path.endsWith(".json"))) {
        //$$     if (!location.getNamespace().equals("mtrsteamloco")) continue;
        //$$     try { resources.put(location, manager.getResources(location)); }
        //$$     catch (java.io.IOException exception) { Main.LOGGER.warn("[FangSu] Cannot read rail source {}", location, exception); }
        //$$ }
        //#endif
        List<ResourceLocation> files = new ArrayList<>(resources.keySet());
        files.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation file : files) {
            // 与 NTE 一样读取资源栈，保留低优先级包中未被覆盖的模型。
            for (Resource resource : resources.get(file)) {
                //#if MC_VERSION >= 11900
                try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                //#else
                //$$ try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                //#endif
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    JsonObject root = parsed.getAsJsonObject();
                    if (root.has("model")) {
                        String path = file.getPath();
                        String key = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                        result.put(key, new Definition(file.toString(), 0));
                    } else {
                        int order = 0;
                        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                            if (entry.getValue().isJsonObject()) {
                                result.put(entry.getKey().toLowerCase(Locale.ROOT), new Definition(file.toString(), order++));
                            }
                        }
                    }
                } catch (Exception exception) {
                    Main.LOGGER.warn("[FangSu] Cannot read rail source {}", file, exception);
                }
            }
        }
        return result;
    }
}
