package com.fangsu.render.model.loader;

import com.fangsu.render.model.RawModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.Map;

public class ObjModelLoader {
    public static RawModel loadModel(ResourceManager resourceManager, ResourceLocation location, Object unused) {
        return new RawModel();
    }

    public static Map<String, RawModel> loadModels(ResourceManager resourceManager, ResourceLocation location, Object unused) {
        Map<String, RawModel> models = new HashMap<>();
        models.put("main", new RawModel());
        return models;
    }
}
