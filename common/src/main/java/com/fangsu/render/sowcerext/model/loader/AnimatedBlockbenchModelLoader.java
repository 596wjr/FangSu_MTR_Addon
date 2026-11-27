package com.fangsu.render.sowcerext.model.loader;

import com.fangsu.Main;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.animation.AnimatedBlockbenchModel;
import com.fangsu.render.sowcerext.model.animation.AnimatedModelNode;
import com.fangsu.render.sowcerext.model.animation.AnimationClip;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Channels;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Keyframe;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Vector;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AnimatedBlockbenchModelLoader {
    private AnimatedBlockbenchModelLoader() {}

    public static AnimatedBlockbenchModel load(ResourceManager manager, ResourceLocation location, boolean flipV) throws IOException {
        if (!location.getPath().endsWith(".bbmodel")) throw new IOException("Expected .bbmodel: " + location);
        JsonObject root = BlockbenchModelLoader.readRoot(manager, location);
        JsonObject meta = root.getAsJsonObject("meta");
        String format = meta.get("format_version").getAsString();
        if (!format.startsWith("4.") && !format.startsWith("5.")) {
            throw new IOException("Unsupported Blockbench format " + format + ": " + location);
        }
        return new Reader(manager, location, root, flipV).read(format, meta.get("model_format").getAsString());
    }

    private static final class Reader {
        private final ResourceLocation location;
        private final boolean flipV;
        private final JsonObject root;
        private final BlockbenchModelLoader.Geometry geometry;
        private final Map<String, JsonObject> groups = new HashMap<>();
        private final Map<String, JsonObject> elements = new LinkedHashMap<>();
        private final Map<String, JsonObject> outlines = new HashMap<>();
        private final Map<String, Integer> indices = new HashMap<>();
        private final List<AnimatedModelNode> nodes = new ArrayList<>();

        Reader(ResourceManager manager, ResourceLocation location, JsonObject root, boolean flipV) {
            this.location = location;
            this.root = root;
            this.flipV = flipV;
            geometry = BlockbenchModelLoader.geometry(manager, location, root);
            index(array(root, "groups"), groups);
            index(array(root, "elements"), elements);
            index(array(root, "outliner"), outlines);
        }

        AnimatedBlockbenchModel read(String format, String modelFormat) {
            JsonArray children = array(root, "outliner");
            if (children.size() == 0) elements.keySet().forEach(children::add);
            readNode("root", -1, Vector.ZERO, Vector.ZERO, Vector.ZERO, "", children);

            Map<String, AnimationClip> clips = new LinkedHashMap<>();
            for (JsonElement entry : array(root, "animations")) {
                JsonObject animation = entry.getAsJsonObject();
                String name = animation.get("name").getAsString();
                try {
                    AnimationClip clip = readClip(animation, name);
                    if (clips.putIfAbsent(name, clip) != null) throw new IllegalArgumentException("Duplicate clip name");
                } catch (RuntimeException exception) {
                    Main.LOGGER.warn("Animation {} / {}: {}", location, name, exception.getMessage());
                }
            }
            return new AnimatedBlockbenchModel(format, modelFormat, nodes, clips);
        }

        private void readNode(String id, int parent, Vector parentOrigin, Vector origin, Vector rotation,
                              String renderType, JsonArray children) {
            int nodeIndex = nodes.size();
            if (indices.putIfAbsent(id, nodeIndex) != null) throw new IllegalArgumentException("Duplicate or cyclic group " + id);
            RawModel model = new RawModel();
            model.sourceLocation = new ResourceLocation(location.getNamespace(),
                    location.getPath() + (flipV ? "/animated_flip_v/" : "/animated/") + nodeIndex);
            for (JsonElement child : children) {
                if (!child.isJsonPrimitive()) continue;
                JsonObject element = elements.get(child.getAsString());
                if (element != null) geometry.append(model, element, renderType);
            }
            if (!model.meshList.isEmpty()) {
                if (flipV) model.applyUVMirror(false, true);
                model.applyTranslation(-origin.x(), -origin.y(), -origin.z());
                model.applyScale(1f / 16, 1f / 16, 1f / 16);
                model.generateNormals();
                model.distinct();
            }
            nodes.add(new AnimatedModelNode(id, parent, origin.subtract(parentOrigin).multiply(1f / 16),
                    rotation, model.meshList.isEmpty() ? null : model));

            for (JsonElement child : children) {
                JsonObject outline = child.isJsonObject() ? child.getAsJsonObject() : outlines.get(child.getAsString());
                String childId = outline == null ? child.getAsString() : outline.get("uuid").getAsString();
                if (elements.containsKey(childId)) continue;
                JsonObject group = groups.getOrDefault(childId, outline);
                if (group == null) continue;
                JsonArray descendants = outline != null && outline.has("children") ? array(outline, "children") : array(group, "children");
                String name = group.has("name") ? group.get("name").getAsString() : "";
                String childRenderType = BlockbenchModelLoader.renderTypeSuffix(name);
                readNode(childId, nodeIndex, origin, vector(group, "origin"), vector(group, "rotation"),
                        childRenderType == null || childRenderType.isEmpty() ? renderType : childRenderType, descendants);
            }
        }

        private AnimationClip readClip(JsonObject animation, String name) {
            for (String expression : List.of("anim_time_update", "blend_weight", "start_delay", "loop_delay")) {
                if (animation.has(expression) && !animation.get(expression).getAsString().isBlank()) {
                    throw new IllegalArgumentException("Unsupported expression: " + expression);
                }
            }
            Map<Integer, Channels> tracks = new LinkedHashMap<>();
            JsonObject animators = animation.getAsJsonObject("animators");
            for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                Integer index = indices.get(entry.getKey());
                if (index == null) throw new IllegalArgumentException("Unknown animation group " + entry.getKey());
                JsonObject animator = entry.getValue().getAsJsonObject();
                if ((animator.has("rotation_global") && animator.get("rotation_global").getAsBoolean())
                        || (animator.has("quaternion_interpolation") && animator.get("quaternion_interpolation").getAsBoolean())) {
                    throw new IllegalArgumentException("Unsupported animation rotation mode");
                }
                Map<String, TreeMap<Double, Keyframe>> values = Map.of(
                        "position", new TreeMap<>(), "rotation", new TreeMap<>(), "scale", new TreeMap<>());
                for (JsonElement frame : array(animator, "keyframes")) {
                    JsonObject key = frame.getAsJsonObject();
                    String channel = key.get("channel").getAsString();
                    if (!values.containsKey(channel) || !"linear".equals(key.get("interpolation").getAsString())) {
                        throw new IllegalArgumentException("Unsupported channel or interpolation: " + channel);
                    }
                    JsonArray points = array(key, "data_points");
                    if (points.size() != 1) throw new IllegalArgumentException("Expected one data point per keyframe");
                    JsonObject point = points.get(0).getAsJsonObject();
                    Vector value = new Vector(finite(point.get("x")), finite(point.get("y")), finite(point.get("z")));
                    if ("position".equals(channel)) value = value.multiply(1f / 16);
                    double time = key.get("time").getAsDouble();
                    if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Invalid keyframe time");
                    values.get(channel).put(time, new Keyframe(time, value));
                }
                tracks.put(index, new Channels(new ArrayList<>(values.get("position").values()),
                        new ArrayList<>(values.get("rotation").values()), new ArrayList<>(values.get("scale").values())));
            }
            return new AnimationClip(name, animation.get("length").getAsDouble(),
                    animation.has("loop") && "loop".equals(animation.get("loop").getAsString()), tracks);
        }
    }

    private static void index(JsonArray entries, Map<String, JsonObject> target) {
        for (JsonElement entry : entries) {
            if (entry.isJsonObject()) {
                JsonObject object = entry.getAsJsonObject();
                target.put(object.get("uuid").getAsString(), object);
            }
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static Vector vector(JsonObject object, String key) {
        if (!object.has(key)) return Vector.ZERO;
        JsonArray value = object.getAsJsonArray(key);
        return new Vector(finite(value.get(0)), finite(value.get(1)), finite(value.get(2)));
    }

    private static float finite(JsonElement value) {
        float number = value.getAsFloat();
        if (!Float.isFinite(number)) throw new IllegalArgumentException("Non-finite keyframe or transform");
        return number;
    }
}
