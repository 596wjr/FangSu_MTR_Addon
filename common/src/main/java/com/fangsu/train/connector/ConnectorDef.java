package com.fangsu.train.connector;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** 两版本共用的不可变配置；无效配置由注册表隔离，不进入绘制路径。 */
public final class ConnectorDef {
    public final String id, side, source, axis, polarity, light, mismatch;
    public final ResourceLocation texture, innerTexture;
    public final double[][] points;
    public final boolean closed, perSegment, cap;
    public final int segments, samples, period, foldCount;
    public final double inset, nominalLength, amplitude, phase, gain, offset;
    public final double[] uRange, vRange, perimeter;

    public ConnectorDef(JsonObject o, String defaultId) {
        id = string(o, "id", defaultId).trim();
        if (id.isEmpty()) throw new IllegalArgumentException("id 不能为空");
        texture = new ResourceLocation(string(o, "texture", ""));
        innerTexture = new ResourceLocation(string(o, "inner_texture", texture.toString()));
        String requestedSide=string(o,"side","both");
        side=requestedSide.equals("outer")?"exterior":requestedSide.equals("inner")?"interior":requestedSide;
        if(!side.equals("exterior") && !side.equals("interior") && !side.equals("both")) throw new IllegalArgumentException("side 必须为 interior/exterior/both");
        light = choice(o, "light", "outdoor", "outdoor", "interior");
        mismatch = choice(o, "id_mismatch", "fallback", "fallback", "skip");
        int requestedSegments = integer(o, "segments", 10, 2, 64);
        inset = Math.max(0, number(o, "inset", 0));
        nominalLength = Math.max(0, number(o, "nominal_length", 0));
        cap = bool(o, "cap", false);
        offset = number(o,"offset",number(o,"exterior_offset",0));
        JsonObject fold = object(o, "fold"), uv = object(o, "uv");
        source = choice(fold, "source", "texture", "texture", "sine", "accordion", "none");
        foldCount = integer(fold, "count", 8, 1, 32);
        // 一个折峰与一个折谷各占一个环间隔，网格必须命中每条折棱。
        segments = source.equals("accordion") ? 2 * foldCount : requestedSegments;
        axis = choice(fold, "axis", "u", "u", "v");
        polarity = choice(fold, "polarity", "bright_out", "bright_out", "bright_in");
        samples = integer(fold, "samples", 64, 8, 256);
        period = integer(fold, "period", 2, 1, 1024);
        amplitude = Math.max(0, number(fold, "amplitude", .04));
        phase = clamp(number(fold, "phase", 0), 0, 1);
        gain = clamp(number(fold, "compress_gain", .5), 0, 2);
        uRange = range(uv, "u_range"); vRange = range(uv, "v_range");
        perSegment = bool(uv, "per_segment", false);
        List<double[]> list = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("points")) {
            double u, v; double tu = Double.NaN, tv = Double.NaN;
            if (el.isJsonArray()) { u = finite(el.getAsJsonArray().get(0)); v = finite(el.getAsJsonArray().get(1)); }
            else {
                JsonObject p = el.getAsJsonObject(); u = finite(p.get("u")); v = finite(p.get("v"));
                if (p.has("uv")) { tu = finite(p.getAsJsonArray("uv").get(0)); tv = finite(p.getAsJsonArray("uv").get(1)); }
            }
            if (Math.abs(u) > .5) ConnectorRegistry.warn(id + ":u", "connector " + id + ": u 超出推荐范围");
            list.add(new double[]{u, clamp(v, 0, 1), tu, tv});
        }
        boolean repeated = list.size() > 1 && distance(list.get(0), list.get(list.size()-1)) < 1e-6;
        if (repeated) list.remove(list.size()-1);
        closed = repeated || bool(o, "closed", true);
        if (list.size() < (closed?3:2)) throw new IllegalArgumentException("闭环至少3点，开口至少2点");
        points = list.toArray(new double[0][]);
        double area = 0;
        for (int i=0; i<points.length; i++) { double[] a=points[i], b=points[(i+1)%points.length]; area += a[0]*b[1]-b[0]*a[1]; }
        if (closed && Math.abs(area) < 1e-10) throw new IllegalArgumentException("截面面积为零");
        perimeter = new double[points.length+1];
        for (int i=1; i<points.length; i++) perimeter[i] = perimeter[i-1] + distance(points[i-1], points[i]);
        perimeter[points.length] = perimeter[points.length-1] + (closed ? distance(points[points.length-1], points[0]) : 0);
        double length = perimeter[points.length];
        if(length<1e-10) throw new IllegalArgumentException("截面长度为零");
        for (int i=0; i<perimeter.length; i++) perimeter[i] /= length;
        if (segments * points.length > 512) ConnectorRegistry.warn(id+":budget", "connector " + id + ": segments × points > 512");
    }
    public int mask() { return side.equals("exterior")?1:side.equals("interior")?2:3; }
    public ResourceLocation textureFor(boolean interior) { return interior && side.equals("both")?innerTexture:texture; }
    private static double distance(double[] a, double[] b) { return Math.hypot(a[0]-b[0], a[1]-b[1]); }
    static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }
    static double finite(JsonElement e) { double x=e.getAsDouble(); if (!Double.isFinite(x)) throw new IllegalArgumentException("非有限数值"); return x; }
    static double number(JsonObject o, String k, double d) { return o.has(k) ? finite(o.get(k)) : d; }
    static String string(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    static boolean bool(JsonObject o, String k, boolean d) { return o.has(k) ? o.get(k).getAsBoolean() : d; }
    static JsonObject object(JsonObject o, String k) { return o.has(k) ? o.getAsJsonObject(k) : new JsonObject(); }
    static String choice(JsonObject o, String k, String d, String... allowed) {
        String s=string(o,k,d); for (String a:allowed) if (a.equals(s)) return s;
        throw new IllegalArgumentException(k + " 无效: " + s);
    }
    static int integer(JsonObject o, String k, int d, int lo, int hi) {
        double n=number(o,k,d); int v=(int)clamp(n,lo,hi);
        if (v != n) ConnectorRegistry.warn(k+":"+n, k+" 已钳制为 "+v);
        return v;
    }
    static double[] range(JsonObject o, String k) { return o.has(k) ? new double[]{finite(o.getAsJsonArray(k).get(0)),finite(o.getAsJsonArray(k).get(1))} : new double[]{0,1}; }
}
