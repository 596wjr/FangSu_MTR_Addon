package com.fangsu.utils;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItemHelper {
    /**
     * 校验主模型配置，若为空则回填默认值。
     */
    public static String checkMainModel(BaseObjBlockEntity entity, String defaultMainModel) {
        if (entity.mainModel == null) {
            entity.mainModel = defaultMainModel;
            return defaultMainModel;
        }
        return entity.mainModel;
    }

    /**
     * 校验子模型配置，若未配置则回填默认值。
     */
    public static String checkSubModel(BaseObjBlockEntity entity, String key, String defaultMainModel) {
        if (entity.subModels == null) {
            entity.subModels = new HashMap<>();
            entity.subModels.put(key, defaultMainModel);
            return defaultMainModel;
        } else if (entity.subModels.containsKey(key)) {
            return entity.subModels.get(key);
        } else {
            entity.subModels.put(key, defaultMainModel);
            return defaultMainModel;
        }
    }
}
