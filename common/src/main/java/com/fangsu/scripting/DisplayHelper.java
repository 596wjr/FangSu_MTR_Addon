package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Vector3f;
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
    private DynamicModelHolder baseModel;
    private Graphics2D graphics;
    private AffineTransform emptyTransform;
    private Map<String, AffineTransform> slotTransforms;
    public DynamicModelHolder model;

    private DisplayHelper() {
    }

    public DisplayHelper(JsonObject cfg) {
        if (cfg == null) return;

        this.cfg = cfg;
        this.texture = null;
        this.ownsTexture = false;

        if (!cfg.has("version")) {
            Main.LOGGER.warn("DisplayHelper: JSON config missing 'version' field");
            return;
        }
        int version = cfg.get("version").getAsInt();
        if (version == 1) {
            String renderType = cfg.has("renderType") ? cfg.get("renderType").getAsString() : "interior";
            RawMeshBuilder meshBuilder = new RawMeshBuilder(4, renderType, new ResourceLocation("minecraft:textures/misc/white.png"));
            meshBuilder.color(255, 255, 255, 255);

            if (!cfg.has("slots") || !cfg.has("texSize")) {
                Main.LOGGER.warn("DisplayHelper: JSON config missing 'slots' or 'texSize' field");
                return;
            }
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
            this.baseModel = new DynamicModelHolder();
            this.baseModel.uploadLater(rawModel);
        } else {
            throw new IllegalArgumentException("Unknown version: " + version);
        }
    }

    public DisplayHelper create() {
        return create(null, false);
    }

    public DisplayHelper createWithinGt() {
        return create(null, true);
    }

    public DisplayHelper create(GraphicsTexture sharedTexture) {
        return create(sharedTexture, false);
    }

    public DisplayHelper create(GraphicsTexture sharedTexture, boolean withinGt) {
        DisplayHelper instance = new DisplayHelper();
        instance.cfg = this.cfg;
        instance.baseModel = this.baseModel;

        if (cfg == null || !cfg.has("version")) {
            Main.LOGGER.warn("DisplayHelper.create(): JSON config missing 'version' field");
            return instance;
        }
        int version = cfg.get("version").getAsInt();
        if (version == 1) {
            if (withinGt) {
                instance.ownsTexture = false;
            } else if (sharedTexture != null) {
                instance.texture = sharedTexture;
                instance.ownsTexture = false;
            } else {
                if (!cfg.has("texSize")) {
                    Main.LOGGER.warn("DisplayHelper.create(): JSON config missing 'texSize' field");
                    return instance;
                }
                JsonArray texSizeArr = cfg.getAsJsonArray("texSize");
                instance.texture = new GraphicsTexture(texSizeArr.get(0).getAsInt(), texSizeArr.get(1).getAsInt());
                instance.ownsTexture = true;
            }
            instance.graphics = instance.texture.graphics;

            instance.emptyTransform = instance.graphics.getTransform();
            instance.slotTransforms = new HashMap<>();

            if (!cfg.has("slots")) {
                Main.LOGGER.warn("DisplayHelper.create(): JSON config missing 'slots' field");
                return instance;
            }
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

            if (baseModel != null && baseModel.getUploadedModel() != null) {
                instance.model = baseModel;
                instance.model.getUploadedModel().replaceAllTexture(instance.texture.identifier);
            }
        } else {
            throw new IllegalArgumentException("Unknown version: " + version);
        }
        return instance;
    }

    public void changeSharedGt(GraphicsTexture sharedTexture) {
        ownsTexture = false;
        texture = sharedTexture;
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

    private static double[][] getCubeVertices(float[] p1, float[] p2, float[] center,
                                              float rx, float ry, float rz) {
        float rxRad = (float) (rx * Math.PI / 180f);
        float ryRad = (float) (ry * Math.PI / 180f);
        float rzRad = (float) (rz * Math.PI / 180f);

        Vector3f c = new Vector3f(center[0], center[1], center[2]);

        if (p1[1] == p2[1]) {
            Vector3f v1 = new Vector3f(p1[0], p1[1], p1[2]);
            Vector3f v2 = new Vector3f(p2[0], p1[1], p1[2]);
            Vector3f v3 = new Vector3f(p2[0], p2[1], p2[2]);
            Vector3f v4 = new Vector3f(p1[0], p2[1], p2[2]);

            v1.sub(c);
            v1.rotX(rxRad);
            v1.rotY(ryRad);
            v1.rotZ(rzRad);
            v1.add(c);

            v2.sub(c);
            v2.rotX(rxRad);
            v2.rotY(ryRad);
            v2.rotZ(rzRad);
            v2.add(c);

            v3.sub(c);
            v3.rotX(rxRad);
            v3.rotY(ryRad);
            v3.rotZ(rzRad);
            v3.add(c);

            v4.sub(c);
            v4.rotX(rxRad);
            v4.rotY(ryRad);
            v4.rotZ(rzRad);
            v4.add(c);

            return new double[][]{
                    {v1.x(), v1.y(), v1.z()},
                    {v2.x(), v2.y(), v2.z()},
                    {v3.x(), v3.y(), v3.z()},
                    {v4.x(), v4.y(), v4.z()}
            };
        } else if (p1[0] == p2[0]) {
            Vector3f v1 = new Vector3f(p1[0], p1[1], p1[2]);
            Vector3f v2 = new Vector3f(p1[0], p2[1], p1[2]);
            Vector3f v3 = new Vector3f(p2[0], p2[1], p2[2]);
            Vector3f v4 = new Vector3f(p2[0], p1[1], p2[2]);

            v1.sub(c);
            v1.rotX(rxRad);
            v1.rotY(ryRad);
            v1.rotZ(rzRad);
            v1.add(c);

            v2.sub(c);
            v2.rotX(rxRad);
            v2.rotY(ryRad);
            v2.rotZ(rzRad);
            v2.add(c);

            v3.sub(c);
            v3.rotX(rxRad);
            v3.rotY(ryRad);
            v3.rotZ(rzRad);
            v3.add(c);

            v4.sub(c);
            v4.rotX(rxRad);
            v4.rotY(ryRad);
            v4.rotZ(rzRad);
            v4.add(c);

            return new double[][]{
                    {v1.x(), v1.y(), v1.z()},
                    {v2.x(), v2.y(), v2.z()},
                    {v3.x(), v3.y(), v3.z()},
                    {v4.x(), v4.y(), v4.z()}
            };
        } else {
            throw new IllegalArgumentException("指定对角顶点不与地面垂直或平行：顶点一 [" +
                    p1[0] + "," + p1[1] + "," + p1[2] +
                    "]，顶点二 [" + p2[0] + "," + p2[1] + "," + p2[2] + "]");
        }
    }
}