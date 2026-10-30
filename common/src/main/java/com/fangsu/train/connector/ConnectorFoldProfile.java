package com.fangsu.train.connector;

import com.fangsu.utils.ResourceUtil;
import java.awt.image.BufferedImage;
import java.util.*;

/** 只在资源重载后的首次使用读图；失败也缓存，直到重载/世界切换。 */
public final class ConnectorFoldProfile {
    private static final Map<String,float[]> CACHE=new HashMap<>();
    private static final Set<ConnectorDef> FAILED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ConnectorDef,float[]> READY=new IdentityHashMap<>();
    private static final Map<ConnectorDef,double[]> AXES=new IdentityHashMap<>();
    private static long version=-1;
    public static void clear() { CACHE.clear(); FAILED.clear(); READY.clear(); AXES.clear(); version=ResourceUtil.getReloadVersion(); }
    public static float[] get(ConnectorDef d) {
        if (version != ResourceUtil.getReloadVersion()) clear();
        if (FAILED.contains(d)) return null;
        if (READY.containsKey(d)) return READY.get(d);
        try {
            BufferedImage image=ResourceUtil.loadImage(d.texture);
            if (image == null || (d.side.equals("both") && ResourceUtil.loadImage(d.innerTexture) == null)) throw new IllegalArgumentException("纹理不可读");
            String key=d.texture+":"+d.axis+":"+d.samples+":"+d.polarity;
            float[] profile=CACHE.get(key);
            if (profile == null) { profile=sample(image,d); CACHE.put(key,profile); }
            READY.put(d,profile); return profile;
        } catch (Exception e) {
            FAILED.add(d); ConnectorRegistry.warn(d.id+":image","connector " + d.id + " 贴图加载失败，回退原生: " + e.getMessage()); return null;
        }
    }
    static float[] sample(BufferedImage image, ConnectorDef d) {
        float[] a=new float[d.samples]; Arrays.fill(a,Float.NaN);
        int first=-1,last=-1; float min=1,max=0;
        for(int i=0;i<a.length;i++) {
            int x=d.axis.equals("u") ? (int)Math.round(i*(image.getWidth()-1.0)/(a.length-1)) : image.getWidth()/2;
            int y=d.axis.equals("v") ? (int)Math.round(i*(image.getHeight()-1.0)/(a.length-1)) : image.getHeight()/2;
            int p=image.getRGB(x,y); if((p>>>24)<8) continue;
            float l=(float)((.299*((p>>16)&255)+.587*((p>>8)&255)+.114*(p&255))/255);
            a[i]=l; min=Math.min(min,l); max=Math.max(max,l);
            if(first<0) first=i;
            if(last>=0) for(int j=last+1;j<i;j++) a[j]=a[last]+(l-a[last])*(j-last)/(i-last);
            last=i;
        }
        if(first<0 || first==last || max-min<1e-6) { Arrays.fill(a,0); return a; }
        for(int i=0;i<first;i++) a[i]=a[first]; for(int i=last+1;i<a.length;i++) a[i]=a[last];
        for(int i=0;i<a.length;i++) a[i]=((a[i]-min)/(max-min)*2-1)*(d.polarity.equals("bright_in")?-1:1);
        return a;
    }
    /** 缓存非均匀轴向环位置，贴图的每个斜率转折与平台边界均进入网格。 */
    public static double[] axis(ConnectorDef d,float[] profile) {
        double[] cached=AXES.get(d); if(cached!=null) return cached;
        TreeSet<Double> knots=new TreeSet<>();
        for(int j=0;j<=d.segments;j++) knots.add(j/(double)d.segments);
        if(d.source.equals("texture")) {
            double start=d.uRange[0]+d.phase,delta=d.uRange[1]-d.uRange[0];
            if(Math.abs(delta)>1e-12 && Math.abs(delta)<=32) {
                int low=(int)Math.floor(Math.min(start,start+delta)),high=(int)Math.ceil(Math.max(start,start+delta));
                for(int wrap=low;wrap<=high;wrap++) for(int i=0;i<profile.length;i++) {
                    // 合并线性区间；保留长亮/暗平台的两端，不能只取一个极值点。
                    if(i>0 && i<profile.length-1 && Math.abs(profile[i-1]-2*profile[i]+profile[i+1])<1e-5) continue;
                    double t=(wrap+i/(double)(profile.length-1)-start)/delta;
                    if(t>1e-9 && t<1-1e-9) knots.add(t);
                }
            } else if(Math.abs(delta)>32) ConnectorRegistry.warn(d.id+":uv-span","connector UV 重复超过32次，仅使用基础细分");
        }
        // 合并几乎重合的均匀环和贴图转折环，避免零宽四边形。
        java.util.ArrayList<Double> clean=new java.util.ArrayList<>();
        for(double t:knots) if(clean.isEmpty() || t-clean.get(clean.size()-1)>1e-8) clean.add(t);
        double[] result=new double[clean.size()];for(int i=0;i<result.length;i++) result[i]=clean.get(i);
        AXES.put(d,result);return result;
    }
    public static boolean hasVariation(float[] p) {
        for(int i=1;i<p.length;i++) if(Math.abs(p[i]-p[0])>1e-6) return true;
        return false;
    }
    public static double value(ConnectorDef d, float[] a, double t) {
        if(d.source.equals("none")) return 0;
        if(d.source.equals("accordion")) {
            // 凸环(+1) → 凹环(-1) → 凸环(+1)，环间为平面，不平滑折棱。
            // 两个端环固定在原锚点；phase/period 仅用于原有贴图/正弦模式。
            double cycle=t*d.foldCount;
            return 1-4*Math.abs((cycle-Math.floor(cycle))-.5);
        }
        // 默认 period=2 时采样落在波峰/波谷，避免 sin(πj) 让所有环恰好为零。
        if(d.source.equals("sine")) return Math.cos(2*Math.PI*(t*d.segments/d.period+d.phase));
        double p=d.uRange[0]+(d.uRange[1]-d.uRange[0])*t+d.phase;
        if(p<0 || p>1) p-=Math.floor(p);
        p=ConnectorDef.clamp(p,0,1)*(a.length-1); int i=(int)p;
        return a[i]+(a[Math.min(i+1,a.length-1)]-a[i])*(p-i);
    }
}
