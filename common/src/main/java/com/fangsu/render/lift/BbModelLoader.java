package com.fangsu.render.lift;

import com.fangsu.render.sowcer.batch.MaterialProp;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.Face;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.Vertex;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench (.bbmodel) 加载器：把元素(cube)按 outliner 的组名(可嵌套)聚合成
 * {@link Map&lt;String, RawModel&gt;}，键为组名，值为该组包含的所有立方体网格。
 *
 * <p>UV 采用 Blockbench 默认的"盒展开"（uv_offset + from/to），并按 {@code resolution}
 * 归一化到 [0,1]。每面两个三角形，法线自动生成。
 */
public class BbModelLoader {

    private BbModelLoader() {
    }

    public static Map<String, RawModel> loadModels(ResourceLocation location) throws IOException {
        JsonElement raw = ResourceUtil.loadAsJSON(location);
        if (raw == null || !raw.isJsonObject()) {
            throw new IOException("bbmodel not found or invalid: " + location);
        }
        JsonObject root = raw.getAsJsonObject();

        float texW = 64, texH = 64;
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject res = root.getAsJsonObject("resolution");
            texW = res.has("width") ? res.get("width").getAsFloat() : 64;
            texH = res.has("height") ? res.get("height").getAsFloat() : 64;
        }

        Map<String, JsonObject> elementsByUuid = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                if (el.has("uuid")) elementsByUuid.put(el.get("uuid").getAsString(), el);
            }
        }

        Map<String, List<JsonObject>> groupElements = new LinkedHashMap<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            int unnamed = 0;
            for (JsonElement entry : root.getAsJsonArray("outliner")) {
                JsonObject group = entry.getAsJsonObject();
                String name = group.has("name") ? group.get("name").getAsString() : ("group" + (unnamed++));
                List<JsonObject> elements = new ArrayList<>();
                collectElements(group, elementsByUuid, elements);
                groupElements.put(name, elements);
            }
        } else {
            groupElements.put("all", new ArrayList<>(elementsByUuid.values()));
        }

        Map<String, RawModel> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<JsonObject>> entry : groupElements.entrySet()) {
            RawModel model = new RawModel();
            for (JsonObject element : entry.getValue()) {
                RawMesh mesh = buildBox(element, texW, texH);
                if (mesh != null) model.append(mesh);
            }
            if (!model.meshList.isEmpty()) {
                model.generateNormals();
                model.distinct();
                result.put(entry.getKey(), model);
            }
        }
        return result;
    }

    private static void collectElements(JsonObject outlinerNode, Map<String, JsonObject> elementsByUuid, List<JsonObject> out) {
        if (!outlinerNode.has("children") || !outlinerNode.get("children").isJsonArray()) return;
        for (JsonElement child : outlinerNode.getAsJsonArray("children")) {
            if (!child.isJsonObject()) continue;
            JsonObject childObj = child.getAsJsonObject();
            if (childObj.has("children")) {
                collectElements(childObj, elementsByUuid, out);
            } else if (childObj.has("uuid")) {
                JsonObject element = elementsByUuid.get(childObj.get("uuid").getAsString());
                if (element != null) out.add(element);
            }
        }
    }

    private static RawMesh buildBox(JsonObject element, float texW, float texH) {
        double[] from = {0, 0, 0}, to = {1, 1, 1}, uv = {0, 0};
        getArray(from, element, "from");
        getArray(to, element, "to");
        getArray(uv, element, "uv_offset");

        float x0 = (float) from[0], y0 = (float) from[1], z0 = (float) from[2];
        float dx = (float) (to[0] - from[0]);
        float dy = (float) (to[1] - from[1]);
        float dz = (float) (to[2] - from[2]);
        if (dx == 0 || dy == 0 || dz == 0) return null;

        float u0 = (float) uv[0] / texW;
        float v0 = (float) uv[1] / texH;
        float du = dx / texW, dv = dy / texH, dw = dz / texW, dh = dz / texH;

        RawMesh mesh = new RawMesh(new MaterialProp());
        mesh.materialProp.texture = null;
        mesh.materialProp.attrState.setColor(255, 255, 255, 255);

        float x1 = x0 + dx, y1 = y0 + dy, z1 = z0 + dz;

        // Blockbench 盒展开：uv 偏移顺序 north, south, west, east, top, bottom
        // north(-Z): 起始 (u0, v0)
        // south(+Z): 起始 (u0+du*texW, v0)
        // west(-X): 起始 (u0+(du+dw)*texW, v0)
        // east(+X): 起始 (u0+(du+2*dw)*texW, v0)
        // top(+Y):  起始 (u0+(2*du+2*dw)*texW, v0)
        // bottom(-Y): 起始 (u0+(2*du+2*dw+du)*texW, v0)
        addQuad(mesh, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,  0, 0, -1, u0, v0, du, dv);                       // north
        addQuad(mesh, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1,  0, 0, 1,  u0 + du + dw, v0, du, dv);             // south
        addQuad(mesh, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0,  u0 + du + 2 * dw, v0, dw, dv);         // west
        addQuad(mesh, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,  1, 0, 0,  u0 + du + 2 * dw + dh, v0, dw, dv);     // east
        addQuad(mesh, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,  0, 1, 0,  u0 + 2 * du + 2 * dw, v0, du, dh);      // top
        addQuad(mesh, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,  0, -1, 0, u0 + 2 * du + 2 * dw + dh, v0, du, dh); // bottom
        return mesh;
    }

    /** 追加一个四边形：4 个顶点 + 2 个三角面。p0..p3 逆时针（法线朝外）。 */
    private static void addQuad(RawMesh mesh, float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3,
                                float nx, float ny, float nz, float u, float v, float su, float sv) {
        int base = mesh.vertices.size();
        mesh.vertices.add(vertex(x0, y0, z0, nx, ny, nz, u, v));
        mesh.vertices.add(vertex(x1, y1, z1, nx, ny, nz, u + su, v));
        mesh.vertices.add(vertex(x2, y2, z2, nx, ny, nz, u + su, v + sv));
        mesh.vertices.add(vertex(x3, y3, z3, nx, ny, nz, u, v + sv));
        mesh.faces.add(new Face(new int[]{base, base + 1, base + 2}));
        mesh.faces.add(new Face(new int[]{base, base + 2, base + 3}));
    }

    private static Vertex vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        Vertex vert = new Vertex(new Vector3f(x, y, z), new Vector3f(nx, ny, nz));
        vert.u = u;
        vert.v = v;
        return vert;
    }

    private static void getArray(double[] target, JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (int i = 0; i < target.length && i < arr.size(); i++) {
                target[i] = arr.get(i).getAsDouble();
            }
        }
    }
}
