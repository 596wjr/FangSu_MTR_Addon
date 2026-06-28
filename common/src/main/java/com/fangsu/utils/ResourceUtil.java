package com.fangsu.utils;

import com.fangsu.Main;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.loader.ObjModelLoader;
import com.fangsu.scripting.GraphicsTexture;
import com.google.gson.*;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ResourceUtil {
    private static final Map<String, Object> register = new ConcurrentHashMap<>();

    /**
     * 后台线程池，用于异步加载 OBJ 模型文件，避免首次放置方块时阻塞主线程导致卡顿。
     */
    private static final ExecutorService MODEL_LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fangsu-model-loader");
        t.setDaemon(true);
        return t;
    });
    private static ResourceManager resourceManager;

    public static final ResourceLocation ERROR_IMAGE = new ResourceLocation("fangsu:textures/gui/error.png");

    /**
     * 从文件加载字符串数组
     */
    public static String[] loadStringAsArray(File file) throws IOException {
        String GlobalRegisterKey = "File" + file.toPath() + "@StringArray";
        if (register.containsKey(GlobalRegisterKey)) {
            return (String[]) register.get(GlobalRegisterKey);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        register.put(GlobalRegisterKey, lines.toArray(new String[0]));
        return lines.toArray(new String[0]);
    }

    /**
     * 从资源包加载字符串数组
     */
    public static String[] loadStringAsArray(ResourceLocation location) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + "@StringArray";
        if (register.containsKey(GlobalRegisterKey)) {
            return (String[]) register.get(GlobalRegisterKey);
        }
        List<String> lines = new ArrayList<>();
        if (resourceManager == null) {
            throw new IOException("ResourceManager is null");
        }

        ;
        try {
            Resource res = getResource(resourceManager, location);
            try (InputStream is = openStream(res);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line.trim());
                }
            }
        } catch (IOException e) {
            Main.LOGGER.warn("Resource not found: {}", location);
            throw e;
        }
        register.put(GlobalRegisterKey, lines.toArray(new String[0]));
        return lines.toArray(new String[0]);
    }


    public static byte[] loadResourceBytes(ResourceLocation location) throws IOException {
        if (resourceManager == null) {
            return null;
        }

        try {
            Resource res = getResource(resourceManager, location);
            try (InputStream is = openStream(res)) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }


    /**
     * 从文件加载字符串
     */
    public static String loadString(File file) throws IOException {
        return String.join("\n", loadStringAsArray(file));
    }

    /**
     * 从资源包加载字符串
     */
    public static String loadString(ResourceLocation location) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + "@String";
        if (register.containsKey(GlobalRegisterKey)) {
            return (String) register.get(GlobalRegisterKey);
        }
        try {
            Resource resource = getResource(resourceManager, location);
            try (InputStream is = openStream(resource)) {
                byte[] bytes = is.readAllBytes();
                String s = new String(bytes, StandardCharsets.UTF_8);
                register.put(GlobalRegisterKey, s);
                return s;
            }
        } catch (IOException e) {
            String s = "";
            register.put(GlobalRegisterKey, s);
            return s;
        }
    }

    /**
     * 从文件加载图像
     */
    public static BufferedImage loadImage(File file) throws IOException {
        String GlobalRegisterKey = "File" + file.toPath() + "@BufferedImage";
        if (register.containsKey(GlobalRegisterKey)) {
            return (BufferedImage) register.get(GlobalRegisterKey);
        }
        BufferedImage image = ImageIO.read(file);
        register.put(GlobalRegisterKey, image);
        return image;
    }

    /**
     * 从资源包加载图像
     */
    public static BufferedImage loadImage(ResourceLocation location) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + "@BufferedImage";
        if (register.containsKey(GlobalRegisterKey)) {
            return (BufferedImage) register.get(GlobalRegisterKey);
        }

        if (resourceManager == null) {
            throw new IOException("ResourceManager is null");
        }
        ;
        try {
            Resource res = getResource(resourceManager, location);
            try (InputStream is = openStream(res)) {
                byte[] imageData = is.readAllBytes();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
                register.put(GlobalRegisterKey, image);
                return image;
            }
        } catch (IOException e) {
            Main.LOGGER.warn("Image resource not found: {}", location);
            throw e;
        }
    }

    public static RawModel loadModel(ResourceLocation location, Boolean flipV) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + (flipV ? "_flipV" : "") + "@RawModel";
        if (register.containsKey(GlobalRegisterKey)) {
            return (RawModel) register.get(GlobalRegisterKey);
        }
        if (resourceManager == null) {
            throw new IOException("ResourceManager is null");
        }
        RawModel model = ObjModelLoader.loadModel(resourceManager, location, null);
        if (flipV) model.applyUVMirror(false, true);
        register.put(GlobalRegisterKey, model);
        return model;
    }

    public static Map<String, RawModel> loadPartedModel(ResourceLocation location, Boolean flipV) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + (flipV ? "_flipV" : "") + "@PartedModel";
        if (register.containsKey(GlobalRegisterKey)) {
            return (Map<String, RawModel>) register.get(GlobalRegisterKey);
        }
        if (resourceManager == null) {
            throw new IOException("ResourceManager is null");
        }
        Map<String, RawModel> models = ObjModelLoader.loadModels(resourceManager, location, null);
        if (flipV)
            for (Map.Entry<String, RawModel> entry : models.entrySet()) {
                RawModel model = entry.getValue();
                model.applyUVMirror(false, true);
            }
        register.put(GlobalRegisterKey, models);
        return models;
    }

    public static DynamicModelHolder loadDmh(ResourceLocation location, Boolean flipV) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + (flipV ? "_flipV" : "") + "@Dmh";
        DynamicModelHolder existing = (DynamicModelHolder) register.get(GlobalRegisterKey);
        if (existing != null) {
            return existing;
        }
        DynamicModelHolder dmh = new DynamicModelHolder();
        existing = (DynamicModelHolder) register.putIfAbsent(GlobalRegisterKey, dmh);
        if (existing != null) {
            return existing;
        }

        // 异步加载模型，避免首次放置方块时阻塞主线程
        MODEL_LOADER.submit(() -> {
            try {
                RawModel model = loadModel(location, flipV);
                dmh.uploadLater(model);
            } catch (IOException e) {
                Main.LOGGER.error("Failed to load model async: {}", location, e);
            }
        });

        return dmh;
    }

    public static Map<String, DynamicModelHolder> loadPartedDmh(ResourceLocation location, Boolean flipV) throws IOException {
        String GlobalRegisterKey = "Identifier" + location.toString() + (flipV ? "_flipV" : "") + "@PartedDmh";
        @SuppressWarnings("unchecked")
        Map<String, DynamicModelHolder> existing = (Map<String, DynamicModelHolder>) register.get(GlobalRegisterKey);
        if (existing != null) {
            return existing;
        }
        Map<String, DynamicModelHolder> map = new ConcurrentHashMap<>();
        existing = (Map<String, DynamicModelHolder>) register.putIfAbsent(GlobalRegisterKey, map);
        if (existing != null) {
            return existing;
        }

        // 异步加载模型，避免首次放置方块时阻塞主线程
        MODEL_LOADER.submit(() -> {
            try {
                Map<String, RawModel> models = loadPartedModel(location, flipV);
                for (Map.Entry<String, RawModel> entry : models.entrySet()) {
                    RawModel model = entry.getValue();
                    DynamicModelHolder dmh = new DynamicModelHolder();
                    dmh.uploadLater(model);
                    map.put(entry.getKey(), dmh);
                }
            } catch (IOException e) {
                Main.LOGGER.error("Failed to load parted model async: {}", location, e);
            }
        });

        return map;
    }

    public static Font loadFont(ResourceLocation location) {
        String key = "Identifier" + location + "@Font";
        if (register.containsKey(key)) {
            return (Font) register.get(key);
        }

        try {
            byte[] data = loadResourceBytes(location);
            if (data == null || data.length == 0) return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, bais);
                register.put(key, font);
                return font;
            }
        } catch (IOException | FontFormatException e) {
            Main.LOGGER.error("Failed to load font: {}", location, e);
        }

        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    /**
     * 检查资源是否存在
     */
    public static boolean hasResources(ResourceLocation location) {
        if (resourceManager == null) return false;
        try {
            //#if MC_VERSION >= 11900
            return resourceManager.getResource(location).isPresent();
            //#else
            //$$ resourceManager.getResource(location); return true;
            //#endif
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将BufferedImage保存为PNG文件
     */
    public static void saveImageAsPNG(BufferedImage image, File outputFile) throws IOException {
        ImageIO.write(image, "PNG", outputFile);
    }

    /**
     * 创建纯色图像
     */
    public static BufferedImage createSolidColorImage(int width, int height, Color color) {
        String GlobalRegisterKey = "SolidColorImage" + width + "x" + height;
        if (register.containsKey(GlobalRegisterKey)) {
            return (BufferedImage) register.get(GlobalRegisterKey);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        register.put(GlobalRegisterKey, image);
        return image;
    }

    /**
     * 创建纯色图像
     */
    public static GraphicsTexture createSolidColorGT(int width, int height, Color color) {
        String GlobalRegisterKey = "SolidColorImage" + width + "x" + height + "_" + color.toString();
        if (register.containsKey(GlobalRegisterKey)) {
            return (GraphicsTexture) register.get(GlobalRegisterKey);
        }
        GraphicsTexture gt = new GraphicsTexture(width, height);
        Graphics2D g2d = gt.graphics;
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        register.put(GlobalRegisterKey, gt);
        gt.upload();
        return gt;
    }

    public static GraphicsTexture createSolidColorGT(int width, int height, int color) {
        String GlobalRegisterKey = "SolidColorImage" + width + "x" + height + "_" + color;
        if (register.containsKey(GlobalRegisterKey)) {
            return (GraphicsTexture) register.get(GlobalRegisterKey);
        }
        Color c = new Color(color);
        GraphicsTexture gt = new GraphicsTexture(width, height);
        Graphics2D g2d = gt.graphics;
        g2d.setColor(c);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        register.put(GlobalRegisterKey, gt);
        gt.upload();
        return gt;
    }

    public static @NotNull JsonElement simpleLoadAsJson(ResourceLocation location) {
        String GlobalRegisterKey = "Identifier" + location.toString() + "@SimpleJson";
        if (register.containsKey(GlobalRegisterKey)) {
            return (JsonElement) register.get(GlobalRegisterKey);
        }
        String loaded;
        try {
            loaded = loadString(location);
        } catch (IOException e) {
            loaded = "{}";
        }
        JsonElement json = Main.JSON_PARSER.parse(loaded);
        register.put(GlobalRegisterKey, json);
        return json;
    }

    /**
     * 从所有资源包加载并合并JSON文件
     * 合并规则：第一层对象合并属性，第一层数组合并元素，更深层直接覆盖
     *
     * @param location 资源位置
     * @return 合并后的JsonElement
     */
    public static @NotNull JsonElement loadAsJSON(ResourceLocation location) {
        String GlobalRegisterKey = "Identifier" + location.toString() + "@Json";
        if (register.containsKey(GlobalRegisterKey)) {
            return (JsonElement) register.get(GlobalRegisterKey);
        }

        if (resourceManager == null) {
            // Server-side: silently return empty, don't log error
            return new JsonObject();
        }

        List<Resource> resources;

        try {
            // 获取所有资源包中的该资源
            //#if MC_VERSION >= 11900
            resources = resourceManager.getResourceStack(location);
            //#else
            //$$ resources = java.util.Collections.singletonList(resourceManager.getResource(location));
            //#endif
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to get resources for {}: {} (returning empty object)", location, e.getMessage());
            return new JsonObject();
        }

        if (!hasResources(location)) {
            Main.LOGGER.warn("No resources found for: {} (returning empty object)", location);
            return new JsonObject();
        }

        Main.debug("Found {} resources for: {}", resources.size(), location);

        Gson gson = Main.GSON;
        JsonElement mergedResult = null;

        // 按资源包优先级从低到高处理（Minecraft返回的顺序是从低优先级到高优先级）
        for (Resource resource : resources) {
            try (InputStream is = openStream(resource);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                JsonElement currentJson = gson.fromJson(reader, JsonElement.class);

                if (mergedResult == null) {
                    // 第一个资源，直接使用
                    mergedResult = currentJson.deepCopy();
                } else {
                    // 合并当前JSON到结果中
                    mergedResult = mergeJsonElements(mergedResult, currentJson);
                }

            } catch (Exception e) {
                Main.LOGGER.warn("Failed to parse JSON from resource {}: {}", location, e.getMessage());
            }
        }

        if (mergedResult != null) {
            register.put(GlobalRegisterKey, mergedResult);
            return mergedResult;
        }
        JsonElement result = JsonNull.INSTANCE;
        register.put(GlobalRegisterKey, result);
        return result;
    }

    // ============ 版本兼容的 Resource 辅助方法 ============

    //#if MC_VERSION >= 11900
    private static Resource getResource(ResourceManager manager, ResourceLocation loc) throws IOException {
        return manager.getResource(loc).orElseThrow(() -> new IOException("Resource not found: " + loc));
    }

    private static InputStream openStream(Resource res) throws IOException {
        return res.open();
    }
    //#else
    //$$private static Resource getResource(ResourceManager manager, ResourceLocation loc) throws IOException {
    //$$    return manager.getResource(loc);
    //$$}
    //$$
    //$$private static InputStream openStream(Resource res) throws IOException {
    //$$    return res.getInputStream();
    //$$}
    //#endif

    /**
     * 合并两个JsonElement，遵循合并规则
     * 1. 第一层对象：合并属性，相同属性名则覆盖
     * 2. 第一层数组：合并元素
     * 3. 更深层：直接覆盖
     */
    private static JsonElement mergeJsonElements(JsonElement base, JsonElement overlay) {
        if (base == null) return overlay;
        if (overlay == null) return base;

        // 类型不匹配，使用覆盖层
        if (base.isJsonObject() && overlay.isJsonObject()) {
            return mergeJsonObjects(base.getAsJsonObject(), overlay.getAsJsonObject(), 1);
        } else if (base.isJsonArray() && overlay.isJsonArray()) {
            return mergeJsonArrays(base.getAsJsonArray(), overlay.getAsJsonArray(), 1);
        } else {
            // 类型不同，直接使用覆盖层
            return overlay.deepCopy();
        }
    }

    /**
     * 合并JsonObject，depth表示当前深度
     */
    private static JsonObject mergeJsonObjects(JsonObject base, JsonObject overlay, int depth) {
        JsonObject result = base.deepCopy();

        for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
            String key = entry.getKey();
            JsonElement overlayValue = entry.getValue();

            if (result.has(key)) {
                // 键已存在，根据深度决定合并策略
                JsonElement baseValue = result.get(key);

                if (depth == 1) {
                    // 第一层，递归合并
                    result.add(key, mergeJsonElements(baseValue, overlayValue));
                } else {
                    // 更深层，直接覆盖
                    result.add(key, overlayValue.deepCopy());
                }
            } else {
                // 新键，直接添加
                result.add(key, overlayValue.deepCopy());
            }
        }

        return result;
    }

    /**
     * 合并JsonArray，depth表示当前深度
     */
    private static JsonArray mergeJsonArrays(JsonArray base, JsonArray overlay, int depth) {
        JsonArray result = new JsonArray();

        // 深度为1时合并数组元素
        if (depth == 1) {
            // 添加基础数组的所有元素
            for (JsonElement element : base) {
                result.add(element.deepCopy());
            }

            // 添加覆盖数组的所有元素
            for (JsonElement element : overlay) {
                result.add(element.deepCopy());
            }
        } else {
            // 更深层，直接使用覆盖数组
            for (JsonElement element : overlay) {
                result.add(element.deepCopy());
            }
        }

        return result;
    }

    public static String hashTo12Chars(String input) {
        // 1. 计算 Java 内置哈希（int 32 位）
        int hash = java.util.Objects.hashCode(input);

        // 2. 也可以用 MurmurHash3 输出 long，更均匀
        // long hash = com.google.common.hash.Hashing.murmur3_128().hashString(input, StandardCharsets.UTF_8).asLong();

        // 3. 转成无符号 long
        long unsigned = hash & 0xFFFFFFFFL;

        // 4. 转成 12 位的 Base36（数字+字母）
        String s = Long.toString(unsigned, 36);

        // 5. 如果不足 12 位，左补 '0'
        return String.format("%12s", s).replace(' ', '0');
    }

    public static boolean isInitialized() {
        return resourceManager != null;
    }

    public static void init(ResourceManager mgr) {
        resourceManager = mgr;
        register.clear();
    }
}