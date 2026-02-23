package fabric.cn.zbx1425.sowcerext.model.loader;

import fabric.cn.zbx1425.sowcerext.model.RawModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class ObjModelLoader {

    public static RawModel loadModel(ResourceManager resourceManager, ResourceLocation location, Object unused) {
        RawModel model = new RawModel();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceManager.getResourceOrThrow(location).open()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        model.addVertex(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return model;
    }

    public static Map<String, RawModel> loadModels(ResourceManager resourceManager, ResourceLocation location, Object unused) {
        Map<String, RawModel> map = new HashMap<>();
        map.put("", loadModel(resourceManager, location, unused));
        return map;
    }
}
