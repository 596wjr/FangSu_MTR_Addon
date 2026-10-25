package com.fangsu.turnout.geometry;

import com.fangsu.Main;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「素材部位」读取层：把 {@code assets/mtrsteamloco/fsrail/main_tpX.obj} 按 {@code o} 拆成
 * <b>钢轨 / 枕木 / 道钉</b> 三组三角形，并且<b>逐顶点保留 position / normal / uv / 材质</b>。
 *
 * <h2>为什么必须带原版法线与 UV（上一轮的教训）</h2>
 * fsrail 的 rail 用两个材质区分部位（{@code rail_top} 顶面 / {@code rail_side} 侧面），视觉上完全靠
 * <b>UV 与法线</b>表达「工字钢」。上一版只取顶点位置、UV/法线全部重算，结果把工字钢画成了
 * 「几根细棕色弧形管」、枕木被画成「斜着飞的大木板」——法线重算会翻转明暗、UV 重算会把
 * 侧面贴图糊满整块，两者叠加就完全不是素材的样子。
 * <p>所以现在：<b>UV 原样保留、法线用 OBJ 的 vn（缺失时退回面法线），平铺时只变换 position 与 normal</b>。</p>
 *
 * <h2>贴图</h2>
 * 材质 → 贴图的映射直接读同目录的 {@code fsrail.mtl}（{@code newmtl} + {@code map_Kd}），
 * 再按「RL 路径 == assets 相对路径」的约定（与平交路口 {@code LevelCrossingStyles} 一致）解析成
 * {@link ResourceLocation}；解析不到就返回 null，调用方回退平交路口的钢轨贴图。
 *
 * <h2>坐标与单位</h2>
 * 模型空间单位是「格」，{@code y = 0} 就是轨道模型原点平面（= {@code anchor.y}）；
 * {@code x} = 横向、{@code z} = 沿轨方向。
 *
 * <h2>缓存与容错</h2>
 * 按模型路径缓存；解析失败只 warn 一次并返回 null，调用方回退程序化工字钢断面。
 */
public final class FsrailParts {

    /** 素材默认路径（轨道模型缺失 / 不是 fsrail 时用它）。 */
    public static final String DEFAULT_MODEL = "fangsu:turnout/builtin/mtr_default.obj";

    /** 素材目录（贴图与 mtl 都在这里）。 */
    private static final String ASSET_NAMESPACE = "mtrsteamloco";
    private static final String ASSET_DIR = "fsrail";

    /** 解析器保留 OBJ 原始 UV；最终写入时遵循该轨道 JSON 的 flipV/flipTextureV。 */
    public static final boolean FLIP_V = false;

    /** 一个顶点：位置 + 法线 + UV（全部来自 OBJ）。 */
    public record Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
    }

    /** 一个三角形 + 它的材质名（用于贴图分桶）。 */
    public record Tri(Vertex a, Vertex b, Vertex c, String material) {
    }

    /** 一个部位：三角形表 + y 范围（用于高度/预算判断）。 */
    public record Part(String name, List<Tri> tris, float minY, float maxY) {

        public int triangleCount() {
            return tris == null ? 0 : tris.size();
        }
    }

    /**
     * 一份素材：三个部位 + 材质→贴图。
     *
     * @param textures 材质名 → 贴图（解析不到的材质不在表里）
     */
    public record Asset(String source, Part rail, Part sleeper, Part spikes,
                        Part railLeft, Part railRight,
                        Map<String, ResourceLocation> textures) {

        public boolean isEmpty() {
            return rail == null && sleeper == null && spikes == null;
        }

        /**
         * 取某一侧的那根钢轨（{@code side > 0} = 模型 +x 侧）。
         * <p>「尖轨 = 把轨道在 heel 处切开」需要按侧单独平铺：只有该侧的前段是尖轨，另一侧始终固定。</p>
         */
        public Part railSide(int side) {
            Part part = side > 0 ? railRight : railLeft;
            return part != null ? part : rail;
        }

        /** 该材质的贴图；没有就返回 null（调用方回退）。 */
        @Nullable
        public ResourceLocation textureOf(String material) {
            if (material == null || textures == null) return null;
            return textures.get(material);
        }
    }

    private static final Map<String, Asset> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private FsrailParts() {
    }

    /**
     * 按轨道模型路径加载素材部位。
     *
     * @param modelResource 轨道的模型资源（如 {@code mtrsteamloco:fsrail/main_tp2.obj}）；null / 空串时用 MTR 普通轨道；非空路径按原路径读取
     * @return 素材；读不到返回 null（调用方回退程序化断面）
     */
    public static Asset load(String modelResource) {
        String path = resolvePath(modelResource);
        Asset hit = CACHE.get(path);
        if (hit != null) return hit.isEmpty() ? null : hit;
        Asset asset;
        try {
            asset = parse(path);
        } catch (RuntimeException | StackOverflowError e) {
            warnOnce(path, "素材解析失败 " + path + " ex=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            asset = new Asset(path, null, null, null, null, null, new HashMap<>());
        }
        CACHE.put(path, asset);
        return asset.isEmpty() ? null : asset;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** 模型路径 → classpath 资源路径。 */
    public static String resolvePath(String modelResource) {
        String model = modelResource == null ? "" : modelResource.trim();
        if (model.isEmpty()) {
            model = DEFAULT_MODEL;
        }
        String namespace = ASSET_NAMESPACE;
        String path = model;
        int colon = model.indexOf(':');
        if (colon >= 0) {
            namespace = model.substring(0, colon);
            path = model.substring(colon + 1);
        }
        if (!path.endsWith(".obj")) {
            path = path + ".obj";
        }
        return "/assets/" + namespace + "/" + path;
    }

    // ==================== OBJ 解析 ====================

    private static Asset parse(String resourcePath) {
        InputStream stream = FsrailParts.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            warnOnce(resourcePath, "找不到素材 " + resourcePath + "，回退程序化断面");
            return new Asset(resourcePath, null, null, null, null, null, new HashMap<>());
        }
        List<float[]> positions = new ArrayList<>(1024);
        List<float[]> normals = new ArrayList<>(1024);
        List<float[]> uvs = new ArrayList<>(1024);
        List<List<Tri>> groups = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        String material = "";
        String mtlLib = "";
        List<Tri> current = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
                String[] token = trimmed.split("\\s+");
                switch (token[0]) {
                    case "v" -> {
                        if (token.length >= 4) {
                            positions.add(new float[]{f(token[1]), f(token[2]), f(token[3])});
                        }
                    }
                    case "vn" -> {
                        if (token.length >= 4) {
                            normals.add(new float[]{f(token[1]), f(token[2]), f(token[3])});
                        }
                    }
                    case "vt" -> {
                        if (token.length >= 3) {
                            uvs.add(new float[]{f(token[1]), f(token[2])});
                        }
                    }
                    case "usemtl" -> material = token.length >= 2 ? token[1] : "";
                    case "mtllib" -> mtlLib = token.length >= 2 ? token[1] : "";
                    case "o" -> {
                        current = new ArrayList<>();
                        groups.add(current);
                        groupNames.add(token.length >= 2 ? token[1] : "");
                    }
                    case "f" -> {
                        if (current == null || token.length < 4) break;
                        int[] corners = new int[token.length - 1];
                        for (int i = 0; i < corners.length; i++) corners[i] = i + 1;
                        for (int i = 1; i + 1 < corners.length; i++) {
                            addTriangle(current, positions, normals, uvs,
                                    parseCorner(token[corners[0]]),
                                    parseCorner(token[corners[i]]),
                                    parseCorner(token[corners[i + 1]]), material);
                        }
                    }
                    default -> {
                        // s / Ka / Kd 等不需要
                    }
                }
            }
        } catch (Exception e) {
            warnOnce(resourcePath, "素材读取异常 " + resourcePath + " ex=" + e.getClass().getSimpleName());
            return new Asset(resourcePath, null, null, null, null, null, new HashMap<>());
        }

        Part rail = null;
        Part sleeper = null;
        Part spikes = null;
        for (int i = 0; i < groups.size(); i++) {
            String name = groupNames.get(i) == null ? "" : groupNames.get(i).toLowerCase(Locale.ROOT);
            Part part = toPart(groupNames.get(i), groups.get(i));
            if (part == null) continue;
            if (name.contains("zhenmu") || name.contains("sleeper")) sleeper = part;
            else if (name.contains("daoding") || name.contains("spike") || name.contains("fastener")) spikes = part;
            else if (name.contains("rail")) rail = part;
        }
        Part railRight = splitRail(rail, true);
        Part railLeft = splitRail(rail, false);
        Map<String, ResourceLocation> textures = loadMaterialTextures(resourcePath, mtlLib);
        Asset asset = new Asset(resourcePath, rail, sleeper, spikes, railLeft, railRight, textures);
        Main.LOGGER.info("[turnout] fsrail parts {} rail={}t zhenmu={}t daoding={}t materials={}",
                resourcePath, tris(rail), tris(sleeper), tris(spikes), textures.keySet());
        return asset;
    }

    private static int tris(Part part) {
        return part == null ? 0 : part.triangleCount();
    }

    /**
     * 把 rail 部位按<b>模型 x 的符号</b>拆成两根单轨（+x / -x）。
     * <p>素材的 rail 对象里同时含左右两根轨（|x| ≈ 0.59~0.82），「在 heel 处把轨道切开」必须能按侧单独取，
     * 否则没法只让分歧侧的那根前段动起来。</p>
     *
     * @param positive true = 取 +x 侧，false = 取 -x 侧
     */
    @Nullable
    private static Part splitRail(@Nullable Part rail, boolean positive) {
        if (rail == null || rail.tris() == null) return null;
        List<Tri> out = new ArrayList<>();
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Tri tri : rail.tris()) {
            double cx = (tri.a().x() + tri.b().x() + tri.c().x()) / 3.0D;
            if (positive ? cx <= 0.05D : cx >= -0.05D) continue;
            out.add(tri);
            minY = Math.min(minY, Math.min(tri.a().y(), Math.min(tri.b().y(), tri.c().y())));
            maxY = Math.max(maxY, Math.max(tri.a().y(), Math.max(tri.b().y(), tri.c().y())));
        }
        return out.isEmpty() ? null : new Part(rail.name() + (positive ? "_right" : "_left"), out, minY, maxY);
    }

    /** {@code f 12/34/56} → {vIndex, vtIndex, vnIndex}（<b>全部转成 0 基</b>；缺失为 -1）。 */
    private static int[] parseCorner(String token) {
        String[] split = token.split("/", -1);
        int v = split.length > 0 ? objIndex(split[0]) : -1;
        int t = split.length > 1 ? objIndex(split[1]) : -1;
        int n = split.length > 2 ? objIndex(split[2]) : -1;
        return new int[]{v, t, n};
    }

    /** OBJ 索引（1 基）→ 0 基；空串/非法返回 -1。{@code v}、{@code vt}、{@code vn} 三者都是 1 基。 */
    private static int objIndex(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            return (int) Float.parseFloat(s) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void addTriangle(List<Tri> out, List<float[]> positions, List<float[]> normals,
                                    List<float[]> uvs, int[] ca, int[] cb, int[] cc, String material) {
        Vertex a = vertex(positions, normals, uvs, ca);
        Vertex b = vertex(positions, normals, uvs, cb);
        Vertex c = vertex(positions, normals, uvs, cc);
        if (a == null || b == null || c == null) return;
        // OBJ 没给法线：用面法线补齐（保证每顶点都有朝外的法线）
        if (zeroNormal(a) || zeroNormal(b) || zeroNormal(c)) {
            float[] n = faceNormal(a, b, c);
            if (n == null) return;
            a = withNormal(a, n);
            b = withNormal(b, n);
            c = withNormal(c, n);
        }
        out.add(new Tri(a, b, c, material));
    }

    @Nullable
    private static Vertex vertex(List<float[]> positions, List<float[]> normals, List<float[]> uvs, int[] corner) {
        int vi = corner[0];
        if (vi < 0 || vi >= positions.size()) return null;
        float[] p = positions.get(vi);
        float nx = 0, ny = 0, nz = 0;
        int ni = corner[2];
        if (ni >= 0 && ni < normals.size()) {
            float[] n = normals.get(ni);
            nx = n[0];
            ny = n[1];
            nz = n[2];
        }
        float u = 0, v = 0;
        int ti = corner[1];
        if (ti >= 0 && ti < uvs.size()) {
            float[] t = uvs.get(ti);
            u = t[0];
            // 原始 UV 保留，避免解析器与模型配置重复或错误地翻 V
            v = FLIP_V ? 1.0F - t[1] : t[1];
        }
        return new Vertex(p[0], p[1], p[2], nx, ny, nz, u, v);
    }

    private static boolean zeroNormal(Vertex v) {
        return Math.abs(v.nx()) < 1e-6F && Math.abs(v.ny()) < 1e-6F && Math.abs(v.nz()) < 1e-6F;
    }

    private static Vertex withNormal(Vertex v, float[] n) {
        return new Vertex(v.x(), v.y(), v.z(), n[0], n[1], n[2], v.u(), v.v());
    }

    /** 面法线（模型空间，右手：cross(b-a, c-a)）。 */
    @Nullable
    private static float[] faceNormal(Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x(), uy = b.y() - a.y(), uz = b.z() - a.z();
        double vx = c.x() - a.x(), vy = c.y() - a.y(), vz = c.z() - a.z();
        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(len > 1e-12D)) return null;
        return new float[]{(float) (nx / len), (float) (ny / len), (float) (nz / len)};
    }

    private static Part toPart(String name, List<Tri> tris) {
        if (tris == null || tris.isEmpty()) return null;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Tri tri : tris) {
            minY = Math.min(minY, Math.min(tri.a().y(), Math.min(tri.b().y(), tri.c().y())));
            maxY = Math.max(maxY, Math.max(tri.a().y(), Math.max(tri.b().y(), tri.c().y())));
        }
        return new Part(name, tris, minY, maxY);
    }

    // ==================== 材质 → 贴图 ====================

    /**
     * 读同目录的 mtl（{@code newmtl} + {@code map_Kd}），解析成材质名 → 贴图。
     * <p>贴图按「RL 路径 == assets 相对路径」解析（与 {@code LevelCrossingStyles} 同一约定）；
     * 找不到的两条候选路径都探测一次，都没有就跳过该材质（调用方用平交路口钢轨贴图兜底）。</p>
     */
    private static Map<String, ResourceLocation> loadMaterialTextures(String resourcePath, String mtlLib) {
        Map<String, ResourceLocation> out = new LinkedHashMap<>();
        if (mtlLib == null || mtlLib.isEmpty()) return out;
        int slash = resourcePath.lastIndexOf('/');
        String mtlPath = (slash < 0 ? "/" : resourcePath.substring(0, slash + 1)) + mtlLib;
        InputStream stream = FsrailParts.class.getResourceAsStream(mtlPath);
        if (stream == null) {
            warnOnce(mtlPath, "找不到材质文件 " + mtlPath + "，贴图回退平交路口钢轨贴图");
            return out;
        }
        String current = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
                String[] token = trimmed.split("\\s+");
                if ("newmtl".equals(token[0])) {
                    current = token.length >= 2 ? token[1] : null;
                } else if ("map_Kd".equals(token[0]) && current != null && token.length >= 2) {
                    ResourceLocation texture = resolveTexture(resourcePath, token[token.length - 1]);
                    if (texture != null) out.put(current, texture);
                }
            }
        } catch (Exception e) {
            warnOnce(mtlPath, "材质文件读取异常 " + mtlPath);
        }
        return out;
    }

    /** 贴图文件名 → ResourceLocation（两条候选路径各探测一次，避免贴图路径约定不同导致丢贴图）。 */
    @Nullable
    private static ResourceLocation resolveTexture(String objPath, String fileName) {
        String file = fileName.replace('\\', '/');
        int namespaceEnd = objPath.indexOf('/', "/assets/".length());
        String namespace = objPath.substring("/assets/".length(), namespaceEnd);
        String folder = objPath.substring(namespaceEnd + 1, objPath.lastIndexOf('/') + 1);
        String relative = folder + file;
        if (classpathExists("/assets/" + namespace + "/" + relative))
            return new ResourceLocation(namespace, relative);
        String prefixed = "textures/" + relative;
        if (classpathExists("/assets/" + namespace + "/" + prefixed))
            return new ResourceLocation(namespace, prefixed);
        warnOnce("tex-" + relative, "素材贴图缺失 " + relative);
        return null;
    }

    private static boolean classpathExists(String path) {
        InputStream stream = FsrailParts.class.getResourceAsStream(path);
        if (stream == null) return false;
        try {
            stream.close();
        } catch (Exception ignored) {
        }
        return true;
    }

    private static float f(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0.0F;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            Main.LOGGER.warn("[turnout] {}", message);
        }
    }
}
