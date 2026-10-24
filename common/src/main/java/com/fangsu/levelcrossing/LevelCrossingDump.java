package com.fangsu.levelcrossing;

import com.fangsu.render.sowcerext.model.Face;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.Vertex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 实机网格导出（诊断用，默认关闭）：把一格的真实网格与「复现所需输入」写到磁盘，
 * 供离线渲染/覆盖扫描定位实机看到的缺口。
 * <p>
 * 开关：JVM 参数 {@code -Dfangsu.levelcrossing.dump=true}。
 * 关闭时<b>不产生任何日志与文件 IO</b>（{@link #ENABLED} 是 static final，调用点先判它）。
 */
public final class LevelCrossingDump {

    /** 开关（默认 false，零开销）。 */
    public static final boolean ENABLED = Boolean.getBoolean("fangsu.levelcrossing.dump");

    /** 导出目录名（相对游戏目录）。 */
    public static final String DIR_NAME = "fangsu_lc_dump";

    private static final String[] GROUPS = {"top", "railTop", "railSide", "ground"};

    private LevelCrossingDump() {
    }

    /** 导出建议文件名：{@code cell_<x>_<y>_<z>}（不含扩展名）。 */
    public static String cellName(int x, int y, int z) {
        return "cell_" + x + "_" + y + "_" + z;
    }

    /**
     * 写 {@code <dir>/<name>.obj} 与 {@code <dir>/<name>.txt}，返回一行摘要（供调用方记日志）。
     * <p>
     * OBJ：按材质分组（top / railTop / railSide / ground），顶点按 (位置, UV) 去重，面用索引引用。
     * TXT：把「复现所需输入」逐项写出（每轨断面/横向校准、铺面高度、俯仰/横滚、四邻是否道口）。
     */
    public static String write(Path dir, String name, RawModel model, CellSnapshot snap,
                              boolean[] neighbours) throws IOException {
        Files.createDirectories(dir);
        ObjStats stats = writeObj(dir.resolve(name + ".obj"), model);
        writeMeta(dir.resolve(name + ".txt"), name, model, snap, neighbours, stats);
        return name + ".obj verts=" + stats.uniqueVerts + " faces=" + stats.faces
                + " tracks=" + (snap == null ? 0 : snap.tracks.size());
    }

    /** 导出统计。 */
    public record ObjStats(int uniqueVerts, int faces, int[] groupTris) {
    }

    /** 按材质的贴图路径把 mesh 归到 4 个组之一（导出顺序稳定，便于比对）。 */
    private static String groupOf(RawMesh mesh) {
        if (mesh == null || mesh.materialProp == null || mesh.materialProp.texture == null) {
            return "top";
        }
        String path = mesh.materialProp.texture.getPath();
        if (path.contains("rail_top")) return "railTop";
        if (path.contains("rail_side")) return "railSide";
        if (path.contains("ground")) return "ground";
        if (path.contains("rail")) return "railSide";
        return "top";
    }

    private static int groupIndex(String name) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i].equals(name)) return i;
        }
        return GROUPS.length - 1;
    }

    private static ObjStats writeObj(Path out, RawModel model) throws IOException {
        StringBuilder vBlock = new StringBuilder();
        StringBuilder vtBlock = new StringBuilder();
        StringBuilder fBlock = new StringBuilder();
        Map<String, Integer> index = new LinkedHashMap<>();
        int[] groupTris = new int[GROUPS.length];
        int uniqueVerts = 0;
        int faces = 0;
        // <b>真实 API</b>：RawModel 的网格表是 meshList（MaterialProp -> RawMesh），
        // 每个 RawMesh 持 vertex 列表与 face 列表（三角面，索引指向 vertices）。
        List<RawMesh> meshes = new java.util.ArrayList<>();
        if (model != null && model.meshList != null) {
            meshes.addAll(model.meshList.values());
        }
        meshes.sort(java.util.Comparator.comparingInt(m -> groupIndex(groupOf(m))));
        for (RawMesh mesh : meshes) {
            String group = groupOf(mesh);
            fBlock.append("o ").append(group).append('\n');
            fBlock.append("g ").append(group).append('\n');
            if (mesh == null || mesh.vertices == null || mesh.faces == null) continue;
            int tri = 0;
            for (Face face : mesh.faces) {
                if (face == null || face.vertices == null || face.vertices.length < 3) continue;
                int[] idx = new int[3];
                boolean ok = true;
                for (int k = 0; k < 3; k++) {
                    int vi = face.vertices[k];
                    if (vi < 0 || vi >= mesh.vertices.size()) {
                        ok = false;
                        break;
                    }
                    Vertex v = mesh.vertices.get(vi);
                    if (v == null || v.position == null) {
                        ok = false;
                        break;
                    }
                    String key = String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f,%.6f",
                            v.position.x(), v.position.y(), v.position.z(), v.u, v.v);
                    Integer hit = index.get(key);
                    if (hit == null) {
                        uniqueVerts++;
                        index.put(key, uniqueVerts);
                        vBlock.append(String.format(Locale.ROOT, "v %.6f %.6f %.6f%n",
                                v.position.x(), v.position.y(), v.position.z()));
                        vtBlock.append(String.format(Locale.ROOT, "vt %.6f %.6f%n", v.u, v.v));
                        hit = uniqueVerts;
                    }
                    idx[k] = hit;
                }
                if (!ok) continue;
                fBlock.append("f ").append(idx[0]).append('/').append(idx[0])
                        .append(' ').append(idx[1]).append('/').append(idx[1])
                        .append(' ').append(idx[2]).append('/').append(idx[2]).append('\n');
                tri++;
                faces++;
            }
            groupTris[groupIndex(group)] += tri;
        }
        StringBuilder sb = new StringBuilder("# fangsu level crossing cell export\n");
        sb.append("# unique verts: ").append(uniqueVerts).append('\n');
        sb.append(vBlock).append(vtBlock).append(fBlock);
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
        return new ObjStats(uniqueVerts, faces, groupTris);
    }

    private static void writeMeta(Path out, String name, RawModel model, CellSnapshot snap,
                                  boolean[] neighbours, ObjStats stats) throws IOException {
        StringBuilder sb = new StringBuilder("# level crossing cell dump\n");
        sb.append("name=").append(name).append('\n');
        sb.append("format=").append(CellSnapshot.FORMAT_VERSION).append('\n');
        if (snap != null) {
            sb.append(String.format(Locale.ROOT, "deckTopY=%.6f%n", snap.deckTopY));
            sb.append(String.format(Locale.ROOT, "groundTopY=%.6f%n", snap.groundTopY));
            sb.append(String.format(Locale.ROOT, "pitchDeg=%.6f%n", snap.pitchDeg));
            sb.append(String.format(Locale.ROOT, "rollDeg=%.6f%n", snap.rollDeg));
            sb.append(String.format(Locale.ROOT, "railTopBias=%.6f%n", snap.railTopBias));
            sb.append("tracks=").append(snap.tracks.size()).append('\n');
            for (int i = 0; i < snap.tracks.size(); i++) {
                TrackSlice t = snap.tracks.get(i);
                sb.append(String.format(Locale.ROOT,
                        "track%d railId=%s primary=%s inner=%.6f outer=%.6f center=%.6f"
                                + " railTopY=%.6f grooveDepth=%.6f gauge=%.6f yaw=%.4f"
                                + " lateralScale=%.6f lateralOffset=%.6f points=%d%n",
                        i, t.railId, t.primary, t.grooveInner, t.grooveOuter, t.grooveCenter,
                        t.railTopY, t.grooveDepth, t.gauge, t.yawDeg,
                        t.lateralScale, t.lateralOffset, t.xs == null ? 0 : t.xs.length));
                StringBuilder line = new StringBuilder("track").append(i).append(" centerline=");
                if (t.xs != null) {
                    for (int k = 0; k < t.xs.length; k++) {
                        line.append(String.format(Locale.ROOT, "(%.6f,%.6f)", t.xs[k],
                                t.zs != null && k < t.zs.length ? t.zs[k] : 0));
                    }
                }
                sb.append(line).append('\n');
            }
        }
        if (neighbours != null) {
            sb.append("neighbourCrossing=")
                    .append(neighbours.length > 0 && neighbours[0]).append(',')
                    .append(neighbours.length > 1 && neighbours[1]).append(',')
                    .append(neighbours.length > 2 && neighbours[2]).append(',')
                    .append(neighbours.length > 3 && neighbours[3]).append('\n');
        }
        if (stats != null) {
            sb.append("uniqueVerts=").append(stats.uniqueVerts).append('\n');
            sb.append("faces=").append(stats.faces).append('\n');
            sb.append("groupTris=");
            for (int i = 0; i < stats.groupTris.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(i < GROUPS.length ? GROUPS[i] : "?").append(':').append(stats.groupTris[i]);
            }
            sb.append('\n');
        }
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
