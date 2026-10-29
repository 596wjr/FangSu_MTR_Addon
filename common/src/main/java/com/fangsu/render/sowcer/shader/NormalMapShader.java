package com.fangsu.render.sowcer.shader;

import com.fangsu.render.ShadersModHandler;
import com.fangsu.render.sowcer.batch.MaterialProp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** 法线材质共用的着色器补丁，同时覆盖自带 GL 批绘制和原版 BufferSource 路径。 */
public final class NormalMapShader {

    private static final Map<String, ShaderInstance> BLAZE_SHADERS = new HashMap<>();
    private static final Map<MaterialKey, RenderType> RENDER_TYPES = new HashMap<>();

    private NormalMapShader() {
    }

    public static void clear() {
        BLAZE_SHADERS.values().forEach(ShaderInstance::close);
        BLAZE_SHADERS.clear();
        RENDER_TYPES.clear();
    }

    public static void loadBlazeShader(ResourceProvider provider, String name) throws IOException {
        BLAZE_SHADERS.put(name, new ShaderInstance(provider, name + "_normal_blaze", DefaultVertexFormat.NEW_ENTITY));
    }

    public static void setupMaterial(ShaderInstance shader, ResourceLocation normal, float strength) {
        // 直接为独立采样器提供纹理，避免占用和改写原版的光照、叠加层纹理槽。
        shader.setSampler("NormalSampler", Minecraft.getInstance().getTextureManager().getTexture(normal));
        if (shader.getUniform("NormalScale") != null) {
            shader.getUniform("NormalScale").set(Float.isFinite(strength) ? Math.max(0, strength) : 1.0f);
        }
    }

    public static RenderType getRenderType(MaterialProp material) {
        if (material.normalTexture == null || !ShadersModHandler.canUseCustomShader()
                || !BLAZE_SHADERS.containsKey(material.shaderName)) return null;
        ResourceLocation texture = material.texture == null ? MaterialProp.WHITE_TEXTURE_LOCATION : material.texture;
        MaterialKey key = new MaterialKey(material.shaderName, texture, material.normalTexture, material.normalScale);
        return RENDER_TYPES.computeIfAbsent(key, NormalRenderType::new);
    }

    // 使用不可变键，脚本 copyForMaterialChanges 后不会污染 RenderType 缓存。
    private record MaterialKey(String shader, ResourceLocation texture, ResourceLocation normal, float strength) {
    }

    private static final class NormalRenderType extends RenderType {
        private NormalRenderType(MaterialKey key) {
            this(key, key.shader().equals("rendertype_entity_translucent_cull")
                    ? BlazeRenderType.entityTranslucentCull(key.texture())
                    : BlazeRenderType.entityCutout(key.texture()));
        }

        private NormalRenderType(MaterialKey key, RenderType base) {
            super("fangsu_normal_" + key.shader(), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, true, key.shader().equals("rendertype_entity_translucent_cull"), () -> {
                        base.setupRenderState();
                        ShaderInstance shader = BLAZE_SHADERS.get(key.shader());
                        RenderSystem.setShader(() -> shader);
                        setupMaterial(shader, key.normal(), key.strength());
                    }, base::clearRenderState);
        }
    }

    public static void patchConfiguration(JsonObject data) {
        JsonObject sampler = new JsonObject();
        sampler.addProperty("name", "NormalSampler");
        data.getAsJsonArray("samplers").add(sampler);
        JsonObject strength = new JsonObject();
        strength.addProperty("name", "NormalScale");
        strength.addProperty("type", "float");
        strength.addProperty("count", 1);
        JsonArray values = new JsonArray();
        values.add(1.0f);
        strength.add("values", values);
        data.getAsJsonArray("uniforms").add(strength);
    }

    public static String removeVertexLighting(String source) {
        String unlit = source.replaceAll(
                "minecraft_mix_light\\(\\s*Light0_Direction\\s*,\\s*Light1_Direction\\s*,\\s*Normal\\s*,\\s*Color\\s*\\)", "Color");
        if (unlit.equals(source)) {
            throw new IllegalArgumentException("Cannot locate entity vertex lighting for normal mapping");
        }
        return unlit;
    }

    public static String patchVertexSource(String source, boolean modelMatrix) {
        int main = source.indexOf("void main");
        int body = source.indexOf('{', main);
        if (main < 0 || body < 0) throw new IllegalArgumentException("Cannot locate vertex shader main");
        String position = modelMatrix ? "(ModelViewMat * ModelMat * vec4(Position, 1.0)).xyz" : "Position";
        String normal = modelMatrix ? "transpose(inverse(mat3(ModelViewMat * ModelMat))) * Normal" : "Normal";
        return source.substring(0, main)
                + "out vec3 normalMapPosition;\nout vec3 normalMapNormal;\n"
                + source.substring(main, body + 1)
                + "\n    normalMapPosition = " + position + ";\n    normalMapNormal = " + normal + ";\n"
                + source.substring(body + 1);
    }

    public static String patchFragmentSource(String source) {
        // 保留原版的透明裁剪、叠加色、光照贴图和雾；仅把方向光移到片元阶段。
        if (!source.contains("#moj_import <light.glsl>")) {
            int firstLine = source.indexOf('\n');
            source = source.substring(0, firstLine + 1) + "#moj_import <light.glsl>\n" + source.substring(firstLine + 1);
        }
        int main = source.indexOf("void main");
        if (main < 0) throw new IllegalArgumentException("Cannot locate fragment shader main");
        String declarations = """
                uniform sampler2D NormalSampler;
                uniform float NormalScale;
                uniform vec3 Light0_Direction;
                uniform vec3 Light1_Direction;
                in vec3 normalMapPosition;
                in vec3 normalMapNormal;

                vec3 fangsu_mapped_normal() {
                    vec3 N = normalMapNormal;
                    if (dot(N, N) < 1e-12) return vec3(0.0, 1.0, 0.0);
                    N = normalize(N);
                    vec3 dx = dFdx(normalMapPosition);
                    vec3 dy = dFdy(normalMapPosition);
                    vec2 tx = dFdx(texCoord0);
                    vec2 ty = dFdy(texCoord0);
                    float determinant = tx.x * ty.y - tx.y * ty.x;
                    // 缺 UV、退化 UV、零强度均保持几何法线，避免 NaN 和闪烁。
                    if (abs(determinant) < 1e-12 || NormalScale <= 0.0) return N;
                    vec3 T = (dx * ty.y - dy * tx.y) / determinant;
                    vec3 B = (dy * tx.x - dx * ty.x) / determinant;
                    T -= N * dot(N, T);
                    if (dot(T, T) < 1e-12 || dot(B, B) < 1e-12) return N;
                    T = normalize(T);
                    // 保留镜像 UV 的手性；flipv 和模型旋转也由当前几何导数自动处理。
                    float handedness = dot(cross(N, T), B) < 0.0 ? -1.0 : 1.0;
                    B = cross(N, T) * handedness;
                    vec3 mapped = textureGrad(NormalSampler, texCoord0, tx, ty).rgb * 2.0 - 1.0;
                    mapped.xy *= NormalScale;
                    if (dot(mapped, mapped) < 1e-12) return N;
                    return normalize(mat3(T, B, N) * mapped);
                }
                """;
        String body = source.substring(main);
        if (!body.contains("vertexColor")) throw new IllegalArgumentException("Cannot locate entity fragment color");
        // 在 discard 之前统一求导，保证透明轮廓附近的片元导数有效。
        int opening = body.indexOf('{');
        body = body.substring(0, opening + 1) + "\n    vec3 mappedNormal = fangsu_mapped_normal();\n" + body.substring(opening + 1);
        body = body.replaceAll("\\bvertexColor\\b",
                "minecraft_mix_light(Light0_Direction, Light1_Direction, mappedNormal, vertexColor)");
        return source.substring(0, main) + declarations + "\n" + body;
    }
}
