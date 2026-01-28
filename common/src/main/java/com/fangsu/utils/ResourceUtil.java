package com.fangsu.utils;

import com.fangsu.Main;
import com.google.gson.*;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class ResourceUtil {
    private static final Map<String, Object> register = new HashMap<>();

    /**
     * 从文件加载字符串数组
     */
    public static String[] loadStringAsArray(File file) throws IOException {
        String GlobalRegisterKey="File"+ file.toPath()+"@StringArray";
        if(register.containsKey(GlobalRegisterKey)){return (String[])register.get(GlobalRegisterKey);}
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        register.put(GlobalRegisterKey,lines.toArray(new String[0]));
        return lines.toArray(new String[0]);
    }

    /**
     * 从资源包加载字符串数组
     */
    public static String[] loadStringAsArray(ResourceLocation location) throws IOException {
        String GlobalRegisterKey="Identifier"+location.toString()+"@StringArray";
        if(register.containsKey(GlobalRegisterKey)){return (String[])register.get(GlobalRegisterKey);}
        List<String> lines = new ArrayList<>();
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();;
        Optional<Resource> resource = resourceManager.getResource(location);

        if (resource.isPresent()) {
            try (InputStream is = resource.get().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line.trim());
                }
            }
        } else {
            Main.LOGGER.warn("Resource not found: {}", location);
            throw new IOException("Resource not found: " + location);
        }
        register.put(GlobalRegisterKey,lines.toArray(new String[0]));
        return lines.toArray(new String[0]);
    }


    public static InputStream loadInputStream(ResourceLocation location) throws IOException {
        String GlobalRegisterKey="Identifier"+location.toString()+"@loadInputStream";
        if(register.containsKey(GlobalRegisterKey)){return (InputStream) register.get(GlobalRegisterKey);}
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();;
        Optional<Resource> resource = resourceManager.getResource(location);

        InputStream stream = null;

        if (resource.isPresent()) {
            try (InputStream is = resource.get().open();
                 ) {
                stream= new BufferedInputStream(is);
            }
        } else {
            Main.LOGGER.warn("Resource not found: {}", location);
            throw new IOException("Resource not found: " + location);
        }
        register.put(GlobalRegisterKey,stream);
        return stream;
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
        return String.join("\n", loadStringAsArray(location));
    }

    /**
     * 从文件加载图像
     */
    public static BufferedImage loadImage(File file) throws IOException {
        String GlobalRegisterKey="File"+ file.toPath()+"@BufferedImage";
        if(register.containsKey(GlobalRegisterKey)){return (BufferedImage)register.get(GlobalRegisterKey);}
        BufferedImage image = ImageIO.read(file);
        register.put(GlobalRegisterKey,image);
        return image;
    }

    /**
     * 从资源包加载图像
     */
    public static BufferedImage loadImage(ResourceLocation location) throws IOException {
        String GlobalRegisterKey="Identifier"+ location.toString()+"@BufferedImage";
        if(register.containsKey(GlobalRegisterKey)){return (BufferedImage)register.get(GlobalRegisterKey);}

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();;
        Optional<Resource> resource = resourceManager.getResource(location);

        if (resource.isPresent()) {
            try (InputStream is = resource.get().open()) {
                byte[] imageData = is.readAllBytes();
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
                register.put(GlobalRegisterKey,image);
                return image;
            }
        } else {
            Main.LOGGER.warn("Image resource not found: {}", location);
            throw new IOException("Image resource not found: " + location);
        }
    }

    /**
     * 检查资源是否存在
     */
    public static boolean hasResources(ResourceLocation location) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
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
        String GlobalRegisterKey="SolidColorImage"+width+"x"+height;
        if(register.containsKey(GlobalRegisterKey)){return (BufferedImage)register.get(GlobalRegisterKey);}
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        register.put(GlobalRegisterKey,image);
        return image;
    }

    /**
     * 从所有资源包加载并合并JSON文件
     * 合并规则：第一层对象合并属性，第一层数组合并元素，更深层直接覆盖
     *
     * @param location 资源位置
     * @return 合并后的JsonElement，如果所有资源包都没有该文件返回null
     */
    public static JsonElement loadAsJSON(ResourceLocation location) {

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();;
        List<Resource> resources = new ArrayList<>();

        try {
            // 获取所有资源包中的该资源
            resources = resourceManager.getResourceStack(location);
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to get resources for {}: {}", location, e.getMessage());
            return null;
        }

        if (!hasResources(location)) {
            Main.LOGGER.info("No resources found for: {}", location);
            return null;
        }

        Main.LOGGER.info("Found {} resources for: {}", resources.size(), location);

        Gson gson = new GsonBuilder().setLenient().create();
        JsonElement mergedResult = null;

        // 按资源包优先级从低到高处理（Minecraft返回的顺序是从低优先级到高优先级）
        for (Resource resource : resources) {
            try (InputStream is = resource.open();
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

        return mergedResult;
    }

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
}