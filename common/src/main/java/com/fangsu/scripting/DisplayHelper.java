package com.fangsu.scripting;

import com.fangsu.MainClient;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;

public class DisplayHelper {
    private JsonObject cfg;
    public GraphicsTexture texture;
    private boolean ownsTexture;
    private ModelCluster baseModel;
    private Graphics2D graphics;
    private AffineTransform emptyTransform;
    private Map<String, AffineTransform> slotTransforms;
    public ModelCluster model;

    private DisplayHelper() {
    }

    public DisplayHelper(JsonObject cfg) {
        if (cfg == null) return;

        this.cfg = cfg;
        this.texture = null;
        this.ownsTexture = false;

        int version = cfg.get("version").getAsInt();
        if (version == 1) {
            String renderType = cfg.has("renderType") ? cfg.get("renderType").getAsString() : "interior";
            RawMeshBuilder meshBuilder = new RawMeshBuilder(4, renderType, new ResourceLocation("minecraft:textures/misc/white.png"));
            meshBuilder.color(255, 255, 255, 255);

            JsonArray slots = cfg.getAsJsonArray("slots");
            JsonArray texSizeArr = cfg.getAsJsonArray("texSize");
            int texWidth = texSizeArr.get(0).getAsInt();
            int texHeight = texSizeArr.get(1).getAsInt();

            for (JsonElement slotElem : slots) {
                JsonObject slotCfg = slotElem.getAsJsonObject();
                JsonArray texArea = slotCfg.getAsJsonArray("texArea");
                int taX = texArea.get(0).getAsInt();
                int taY = texArea.get(1).getAsInt();
                int taW = texArea.get(2).getAsInt();
                int taH = texArea.get(3).getAsInt();

                double[][] realUV = new double[4][2];
                realUV[0][0] = (double) taX / texWidth;
                realUV[0][1] = (double) taY / texHeight;
                realUV[1][0] = (double) taX / texWidth;
                realUV[1][1] = (double) (taY + taH) / texHeight;
                realUV[2][0] = (double) (taX + taW) / texWidth;
                realUV[2][1] = (double) (taY + taH) / texHeight;
                realUV[3][0] = (double) (taX + taW) / texWidth;
                realUV[3][1] = (double) taY / texHeight;

                JsonArray offsets = slotCfg.has("offsets") ? slotCfg.getAsJsonArray("offsets") : null;
                if (offsets == null) {
                    offsets = new JsonArray();
                    JsonArray zeroOffset = new JsonArray();
                    zeroOffset.add(0);
                    zeroOffset.add(0);
                    zeroOffset.add(0);
                    offsets.add(zeroOffset);
                }

                JsonArray positions = slotCfg.getAsJsonArray("pos");

                for (JsonElement offsetElem : offsets) {
                    JsonArray offset = offsetElem.getAsJsonArray();
                    double offX = offset.get(0).getAsDouble();
                    double offY = offset.get(1).getAsDouble();
                    double offZ = offset.get(2).getAsDouble();

                    for (JsonElement posElem : positions) {
                        JsonArray posCfg = posElem.getAsJsonArray();
                        for (int i = 0; i < 4; i++) {
                            JsonArray vertexArr = posCfg.get(i).getAsJsonArray();
                            double vx = vertexArr.get(0).getAsDouble() + offX;
                            double vy = vertexArr.get(1).getAsDouble() + offY;
                            double vz = vertexArr.get(2).getAsDouble() + offZ;
                            meshBuilder.vertex(vx, vy, vz)
                                    .normal(0, 1, 0)
                                    .uv((float) realUV[i][0], (float) realUV[i][1])
                                    .endVertex();
                        }
                    }
                }
            }

            RawModel rawModel = new RawModel();
            rawModel.append(meshBuilder.getMesh());
            rawModel.triangulate();
            Minecraft.getInstance().executeBlocking(() ->
                    this.baseModel = MainClient.modelManager.uploadVertArrays(rawModel)
            );
        } else {
            throw new IllegalArgumentException("Unknown version: " + version);
        }
    }

    public DisplayHelper create() {
        return create(null);
    }

    public DisplayHelper create(GraphicsTexture sharedTexture) {
        DisplayHelper instance = new DisplayHelper();
        instance.cfg = this.cfg;
        instance.baseModel = this.baseModel;

        int version = cfg.get("version").getAsInt();
        if (version == 1) {
            if (sharedTexture != null) {
                instance.texture = sharedTexture;
                instance.ownsTexture = false;
            } else {
                JsonArray texSizeArr = cfg.getAsJsonArray("texSize");
                instance.texture = new GraphicsTexture(texSizeArr.get(0).getAsInt(), texSizeArr.get(1).getAsInt());
                instance.ownsTexture = true;
            }
            instance.graphics = instance.texture.graphics;

            instance.emptyTransform = instance.graphics.getTransform();
            instance.slotTransforms = new HashMap<>();

            JsonArray slots = cfg.getAsJsonArray("slots");
            for (JsonElement slotElem : slots) {
                JsonObject slotCfg = slotElem.getAsJsonObject();
                JsonArray texArea = slotCfg.getAsJsonArray("texArea");
                int taX = texArea.get(0).getAsInt();
                int taY = texArea.get(1).getAsInt();
                int taW = texArea.get(2).getAsInt();
                int taH = texArea.get(3).getAsInt();

                instance.graphics.transform(AffineTransform.getTranslateInstance(taX, taY));

                if (slotCfg.has("paintingSize")) {
                    JsonArray paintSize = slotCfg.getAsJsonArray("paintingSize");
                    double sx = (double) taW / paintSize.get(0).getAsDouble();
                    double sy = (double) taH / paintSize.get(1).getAsDouble();
                    instance.graphics.transform(AffineTransform.getScaleInstance(sx, sy));
                }

                String slotName = slotCfg.get("name").getAsString();
                instance.slotTransforms.put(slotName, instance.graphics.getTransform());
                instance.graphics.setTransform(instance.emptyTransform);
            }

            instance.model = this.baseModel.copyForMaterialChanges();
            instance.model.replaceAllTexture(instance.texture.identifier);
        } else {
            throw new IllegalArgumentException("Unknown version: " + version);
        }
        return instance;
    }

    public void upload() {
        if (ownsTexture) {
            texture.upload();
        }
    }

    public void close() {
        if (ownsTexture) {
            texture.close();
        }
    }

    public Graphics2D graphics() {
        graphics.setTransform(emptyTransform);
        return graphics;
    }

    public Graphics2D graphicsFor(String slotName) {
        AffineTransform transform = slotTransforms.get(slotName);
        if (transform != null) {
            graphics.setTransform(transform);
        }
        return graphics;
    }
}