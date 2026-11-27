package com.fangsu.train.parts;

import com.fangsu.Main;
import com.fangsu.render.sowcerext.model.animation.AnimatedModel;
import com.fangsu.render.sowcerext.model.animation.AnimationClip;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Vector;
import com.fangsu.render.sowcerext.model.loader.AnimatedBlockbenchModelLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class TrainPartsRegistry {
    public record Asset(AnimatedModel model, AnimationClip clip) {}

    public record Mount(Asset asset, Vector position, Vector rotation, Set<Integer> cars) {
        public boolean matches(int car, int carCount) {
            // 车厢号从 1 开始，-1 表示末车。
            return cars.isEmpty() || cars.contains(car) || car == carCount && cars.contains(-1);
        }
    }

    public record Bogie(Mount mount, double blocksPerCycle) {}
    public record Pantograph(Mount mount, String id, String group, double raiseProgress) {}

    public record Parts(Map<Integer, List<Bogie>> bogies, Map<String, List<Pantograph>> pantographs) {
        public Bogie bogie(int car, int carCount, int slot) {
            for (Bogie bogie : bogies.getOrDefault(slot, List.of())) {
                if (bogie.mount().matches(car, carCount)) return bogie;
            }
            return null;
        }
    }

    private static final String MTR_RESOURCES = "mtr:mtr_custom_resources.json";
    private static volatile Map<String, Parts> entries = Map.of();
    private TrainPartsRegistry() {}

    public static Parts get(String vehicleType) { return entries.get(vehicleType); }

    public static void reload(ResourceManager manager) {
        Map<String, Parts> result = new HashMap<>();
        PartLibrary library = new PartLibrary(manager);
        for (String path : List.of(MTR_RESOURCES, "fangsu:custom_trains.json")) {
            try {
                JsonElement json = com.fangsu.utils.ResourceUtil.loadAsJSON(new ResourceLocation(path));
                if (!json.isJsonObject()) continue;
                JsonObject root = json.getAsJsonObject();
                if (path.equals(MTR_RESOURCES)) library.builtinResources = root;
                if (root.has("custom_trains") && root.get("custom_trains").isJsonObject()) {
                    root.getAsJsonObject("custom_trains").entrySet().forEach(entry -> {
                        String id = "mtr_custom_train_" + entry.getKey();
                        Parts parts = read(library, root, id, entry.getValue());
                        if (parts == null) return;
                        result.put(id, parts);
                        for (String suffix : List.of("_cab_1", "_cab_2", "_cab_3", "_trailer")) result.put(id + suffix, parts);
                    });
                }
                for (JsonElement value : array(root, "vehicles")) {
                    if (!value.isJsonObject() || !value.getAsJsonObject().has("id")) continue;
                    String id = value.getAsJsonObject().get("id").getAsString();
                    Parts parts = read(library, root, id, value);
                    if (parts != null) result.put(id, parts);
                }
            } catch (RuntimeException exception) {
                Main.LOGGER.warn("Train parts configuration {}: {}", path, exception.getMessage());
            }
        }
        entries = Map.copyOf(result);
        TrainPartRenderer.reset();
    }

    private static Parts read(PartLibrary library, JsonObject root, String id, JsonElement value) {
        if (!value.isJsonObject()) return null;
        JsonObject vehicle = value.getAsJsonObject();
        if (!vehicle.has("bogie") && !vehicle.has("pantograph")) return null;

        Map<Integer, List<Bogie>> bogies = new HashMap<>();
        readSlots(library, root, vehicle, id, PartKind.BOGIE, (part, installation) -> {
            int slot = installation.get("slot").getAsInt();
            if (slot < 0 || slot > 2) throw new IllegalArgumentException("Invalid bogie slot " + slot);
            Mount mount = mount(part.asset(), installation, "offset");
            List<Bogie> variants = bogies.computeIfAbsent(slot, ignored -> new ArrayList<>());
            requireDistinct(mount, variants.stream().map(Bogie::mount).toList(), "bogie slot " + slot);
            variants.add(new Bogie(mount, part.blocksPerCycle()));
        });

        Map<String, List<Pantograph>> pantographs = new LinkedHashMap<>();
        readSlots(library, root, vehicle, id, PartKind.PANTOGRAPH, (part, installation) -> {
            String bow = installation.get("name").getAsString();
            String group = installation.has("group") ? installation.get("group").getAsString() : "";
            if (!com.fangsu.train.data.TrainDataSnapshot.validName(bow)
                    || !group.isEmpty() && !com.fangsu.train.data.TrainDataSnapshot.validName(group)) {
                throw new IllegalArgumentException("Invalid pantograph name or group");
            }
            double raiseProgress = installation.has("raise_progress") ? installation.get("raise_progress").getAsDouble() : 1;
            if (!Double.isFinite(raiseProgress) || raiseProgress < 0 || raiseProgress > 1) {
                throw new IllegalArgumentException("raise_progress must be between 0 and 1");
            }
            Mount mount = mount(part.asset(), installation, "pos");
            List<Pantograph> variants = pantographs.computeIfAbsent(bow, ignored -> new ArrayList<>());
            requireDistinct(mount, variants.stream().map(Pantograph::mount).toList(), "pantograph " + bow);
            variants.add(new Pantograph(mount, bow, group, raiseProgress));
        });

        // 指定车厢的安装项优先，未指定车厢的安装项供其余车厢共用。
        bogies.replaceAll((slot, variants) -> sorted(variants, bogie -> bogie.mount().cars().isEmpty()));
        pantographs.replaceAll((bow, variants) -> sorted(variants, part -> part.mount().cars().isEmpty()));
        return new Parts(Map.copyOf(bogies), Collections.unmodifiableMap(pantographs));
    }

    /** 共享部件表和风挡一样位于本文件根对象，安装槽位随车型配置。 */
    private static void readSlots(PartLibrary library, JsonObject root, JsonObject vehicle, String trainId,
                                  PartKind kind, BiConsumer<PartDefinition, JsonObject> apply) {
        JsonElement configuration = vehicle.get(kind.field);
        if (configuration == null || configuration.isJsonNull()) return;
        Iterable<JsonElement> definitions = configuration.isJsonArray()
                ? configuration.getAsJsonArray() : List.of(configuration);
        JsonElement table = root.get(kind.table);
        JsonObject shared = table != null && table.isJsonObject() ? table.getAsJsonObject() : new JsonObject();
        for (JsonElement entry : definitions) {
            PartDefinition part;
            JsonArray slots;
            String partId = "";
            try {
                JsonObject selection = entry.getAsJsonObject();
                partId = selection.get("id").getAsString();
                slots = selection.getAsJsonArray("slots");
                if (slots == null || slots.size() == 0) throw new IllegalArgumentException("Expected installation slots");
                part = library.get(partId, shared, kind);
            } catch (IOException | RuntimeException exception) {
                Main.LOGGER.warn("Train {} {} {}: {}", trainId, kind.field, partId, exception.getMessage());
                continue;
            }
            for (int index = 0; index < slots.size(); index++) {
                try {
                    apply.accept(part, slots.get(index).getAsJsonObject());
                } catch (RuntimeException exception) {
                    Main.LOGGER.warn("Train {} {} {} slot {}: {}", trainId, kind.field, partId, index, exception.getMessage());
                }
            }
        }
    }

    private static <T> List<T> sorted(List<T> variants, Predicate<T> defaults) {
        return variants.stream().sorted(Comparator.comparing(defaults::test)).toList();
    }

    private static void requireDistinct(Mount mount, List<Mount> previous, String name) {
        for (Mount other : previous) {
            if (mount.cars().isEmpty() && other.cars().isEmpty()
                    || mount.cars().stream().anyMatch(other.cars()::contains)) {
                throw new IllegalArgumentException("Overlapping car selection for " + name);
            }
        }
    }

    private static Mount mount(Asset asset, JsonObject installation, String positionKey) {
        Set<Integer> cars = new HashSet<>();
        for (JsonElement car : array(installation, "cars")) {
            int number = car.getAsInt();
            if (number != -1 && (number < 1 || number > 999)) throw new IllegalArgumentException("Invalid car number");
            cars.add(number);
        }
        return new Mount(asset, vector(installation, positionKey), vector(installation, "rotation"), Set.copyOf(cars));
    }

    private enum PartKind {
        BOGIE("bogie", "bogies", "animation"),
        PANTOGRAPH("pantograph", "pantographs", "raise_animation");

        final String field;
        final String table;
        final String animationKey;

        PartKind(String field, String table, String animationKey) {
            this.field = field;
            this.table = table;
            this.animationKey = animationKey;
        }
    }

    private record PartDefinition(Asset asset, double blocksPerCycle) {}
    private record ModelKey(ResourceLocation location, boolean flipV) {}
    private record DefinitionKey(PartKind kind, ModelKey model, String clip, double blocksPerCycle) {}

    /** 车型安装位置只从安装项读取，部件定义和动画模型在本轮资源加载中共享。 */
    private static final class PartLibrary {
        private final ResourceManager manager;
        private final Map<ModelKey, AnimatedModel> models = new HashMap<>();
        private final Map<DefinitionKey, PartDefinition> definitions = new HashMap<>();
        private JsonObject builtinResources = new JsonObject();

        PartLibrary(ResourceManager manager) { this.manager = manager; }

        PartDefinition get(String id, JsonObject shared, PartKind kind) throws IOException {
            JsonElement definition = shared.get(id);
            // 旧式车型也可以选用主资源表的共享部件，当前文件同名定义优先。
            if (definition == null && builtinResources.get(kind.table) instanceof JsonObject builtin) {
                definition = builtin.get(id);
            }
            if (definition == null || !definition.isJsonObject()) {
                throw new IllegalArgumentException("Unknown " + kind.table + " entry: " + id);
            }
            return readDefinition(definition.getAsJsonObject(), kind);
        }

        private PartDefinition readDefinition(JsonObject definition, PartKind kind) throws IOException {
            ResourceLocation location = new ResourceLocation(definition.get("model").getAsString());
            String name = definition.get(kind.animationKey).getAsString();
            double distance = kind == PartKind.BOGIE ? definition.get("blocks_per_cycle").getAsDouble() : 0;
            if (kind == PartKind.BOGIE && (!Double.isFinite(distance) || distance <= 0)) {
                throw new IllegalArgumentException("Invalid blocks_per_cycle");
            }
            boolean flipV = definition.has("flipV") && definition.get("flipV").getAsBoolean();
            ModelKey modelKey = new ModelKey(location, flipV);
            DefinitionKey key = new DefinitionKey(kind, modelKey, name, distance);
            PartDefinition part = definitions.get(key);
            if (part != null) return part;
            AnimatedModel model = models.get(modelKey);
            if (model == null) {
                model = AnimatedBlockbenchModelLoader.load(manager, location, flipV);
                models.put(modelKey, model);
            }
            AnimationClip clip = model.findClip(name);
            if (clip == null || clip.length() <= 0 || model.nodes().stream().noneMatch(node -> node.geometry() != null)) {
                throw new IllegalArgumentException("Missing or empty model/clip " + location + " / " + name);
            }
            if (kind == PartKind.BOGIE && !clip.loop()) throw new IllegalArgumentException("Bogie clip must loop");
            part = new PartDefinition(new Asset(model, clip), distance);
            definitions.put(key, part);
            return part;
        }
    }

    private static Vector vector(JsonObject object, String key) {
        if (!object.has(key)) return Vector.ZERO;
        JsonArray value = array(object, key);
        if (value.size() != 3) throw new IllegalArgumentException(key + " requires three coordinates");
        float x = value.get(0).getAsFloat(), y = value.get(1).getAsFloat(), z = value.get(2).getAsFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) throw new IllegalArgumentException("Invalid " + key);
        return new Vector(x, y, z);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }
}
