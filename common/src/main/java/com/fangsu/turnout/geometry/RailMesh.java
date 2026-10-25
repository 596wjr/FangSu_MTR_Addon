package com.fangsu.turnout.geometry;

import com.fangsu.levelcrossing.geometry.PolyOps;
import com.fangsu.levelcrossing.geometry.Vec2;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 带原材质、UV、法线的三角网格裁切。新顶点插值原属性，切口单独封面。 */
public final class RailMesh {
    private static final double EPS = 1e-7D;

    public record Vertex(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
        public Vertex mix(Vertex b, double t) {
            return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,
                    nx+(b.nx-nx)*t,ny+(b.ny-ny)*t,nz+(b.nz-nz)*t,u+(b.u-u)*t,v+(b.v-v)*t);
        }
        public Vertex at(double px, double py, double pz) { return new Vertex(px,py,pz,nx,ny,nz,u,v); }
        public Vertex normal(double px, double py, double pz) { return new Vertex(x,y,z,px,py,pz,u,v); }
    }
    public record Face(String material, Vertex a, Vertex b, Vertex c) { }
    /** 正半空间是保留区（clip）或待挖去凸体的内部（subtract）。 */
    public record Plane(double x, double y, double z, double d) {
        public double distance(Vertex v) { return x*v.x+y*v.y+z*v.z+d; }
    }
    public record Surface(String material, Function<Vertex, Vertex> uv) { }
    public final List<Face> faces = new ArrayList<>();

    public void triangle(String material, Vertex a, Vertex b, Vertex c) {
        double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
        if(nx*nx+ny*ny+nz*nz>1e-20D) faces.add(new Face(material,a,b,c));
    }
    public void append(RailMesh mesh) { faces.addAll(mesh.faces); }
    public RailMesh reverse() {
        RailMesh out=new RailMesh();
        for(Face f:faces) out.triangle(f.material,f.a,f.c,f.b);
        return out;
    }
    public RailMesh transform(Function<Vertex,Vertex> map) {
        RailMesh out=new RailMesh();
        for(Face f:faces) out.triangle(f.material,map.apply(f.a),map.apply(f.b),map.apply(f.c));
        return out;
    }

    public RailMesh clip(Plane plane, boolean positive, Surface surface) {
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
        RailMesh out=new RailMesh();
        List<Vertex[]> edges=new ArrayList<>();
        for(Face f:faces) {
            List<Vertex> src=List.of(f.a,f.b,f.c),poly=new ArrayList<>(),hits=new ArrayList<>();
            boolean hasPositive=false,hasNegative=false;
            for(Vertex v:src) {double d=plane.distance(v);hasPositive|=d>EPS;hasNegative|=d < -EPS;}
            for(int i=0;i<3;i++) {
                Vertex a=src.get(i),b=src.get((i+1)%3);
                double da=plane.distance(a),db=plane.distance(b);
                boolean ia=positive?da>=-EPS:da<=EPS,ib=positive?db>=-EPS:db<=EPS;
                if(ia) addUnique(poly,a);
                if(ia!=ib) addUnique(poly,a.mix(b,da/(da-db)));
                if(hasPositive&&hasNegative) {
                    if(Math.abs(da)<=EPS) addUnique(hits,a);
                    if(da*db < -EPS*EPS) addUnique(hits,a.mix(b,da/(da-db)));
                }
            }
            if(poly.size()>1&&near(poly.get(0),poly.get(poly.size()-1))) poly.remove(poly.size()-1);
            for(int i=1;i+1<poly.size();i++) out.triangle(f.material,poly.get(0),poly.get(i),poly.get(i+1));
            if(hits.size()==2&&!near(hits.get(0),hits.get(1))) edges.add(new Vertex[]{hits.get(0),hits.get(1)});
        }
        if(surface!=null&&!edges.isEmpty()) cap(out,edges,plane,positive,surface);
        return out;
    }

    /** 凸体差集：逐平面剥出外部，完全落在内部的剩余部分丢弃。 */
    public RailMesh subtract(List<Plane> volume, Surface surface) {
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
        // 先用全部顶点判定是否位于任一平面的外部，避开多数不相交的小轨段。
        for(Plane p:volume) {
            boolean any=false;
            for(Face f:faces) if(p.distance(f.a)>EPS||p.distance(f.b)>EPS||p.distance(f.c)>EPS) {any=true;break;}
            if(!any) return this;
        }
        RailMesh outside=new RailMesh(),inside=this;
        for(Plane p:volume) {
            outside.append(inside.clip(p,false,surface));
            inside=inside.clip(p,true,surface);
            if(inside.faces.isEmpty()) break;
        }
        return outside;
    }

    private static void cap(RailMesh out,List<Vertex[]> edges,Plane plane,boolean positive,Surface surface) {
        List<Vertex> points=new ArrayList<>();
        List<int[]> links=new ArrayList<>();
        for(Vertex[] e:edges) {
            int a=index(points,e[0]),b=index(points,e[1]);
            if(a==b) continue;
            links.add(new int[]{a,b});
        }
        // 原面与补端面的三角化可能形成 T 接缝。先分割共线长边，再按奇偶消掉内部重复边。
        java.util.Map<Long,Integer> counts=new java.util.LinkedHashMap<>();
        for(int[] link:links) {
            Vertex a=points.get(link[0]),b=points.get(link[1]);
            double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z,len2=dx*dx+dy*dy+dz*dz;
            List<Integer> ids=new ArrayList<>(List.of(link[0],link[1]));
            for(int i=0;i<points.size();i++) {
                Vertex v=points.get(i);double t=((v.x-a.x)*dx+(v.y-a.y)*dy+(v.z-a.z)*dz)/len2;
                double ex=v.x-a.x-dx*t,ey=v.y-a.y-dy*t,ez=v.z-a.z-dz*t;
                if(t>EPS&&t<1-EPS&&ex*ex+ey*ey+ez*ez<4e-12D) ids.add(i);
            }
            ids.sort(java.util.Comparator.comparingDouble((Integer i)->{Vertex v=points.get(i);return (v.x-a.x)*dx+(v.y-a.y)*dy+(v.z-a.z)*dz;}));
            for(int i=0;i+1<ids.size();i++) {
                int lo=Math.min(ids.get(i),ids.get(i+1)),hi=Math.max(ids.get(i),ids.get(i+1));
                if(lo!=hi) counts.merge(((long)lo<<32)|(hi&0xffffffffL),1,Integer::sum);
            }
        }
        links.clear();
        for(java.util.Map.Entry<Long,Integer> e:counts.entrySet()) if((e.getValue()&1)!=0)
            links.add(new int[]{(int)(e.getKey()>>>32),(int)(e.getKey()&0xffffffffL)});
        double length=Math.sqrt(plane.x*plane.x+plane.y*plane.y+plane.z*plane.z);
        if(length<EPS) return;
        double direction=positive?-1:1;
        double nx=plane.x/length*direction,ny=plane.y/length*direction,nz=plane.z/length*direction;
        int drop=Math.abs(nx)>Math.abs(ny)?(Math.abs(nx)>Math.abs(nz)?0:2):(Math.abs(ny)>Math.abs(nz)?1:2);
        while(!links.isEmpty()) {
            int[] first=links.remove(links.size()-1);
            List<Integer> ring=new ArrayList<>(List.of(first[0],first[1]));
            int current=first[1];
            boolean closed=false;
            for(int guard=0;guard<=points.size();guard++) {
                int found=-1,next=-1;
                for(int i=0;i<links.size();i++) {
                    int[] link=links.get(i);
                    if(link[0]==current||link[1]==current) {found=i;next=link[0]==current?link[1]:link[0];break;}
                }
                if(found<0) break;
                links.remove(found);
                if(next==ring.get(0)) {closed=true;break;}
                ring.add(next);current=next;
            }
            // 无闭环时不以扇形硬填，避免跨过空洞拉出大白面。
            if(!closed||ring.size()<3) continue;
            List<Vec2> flat=new ArrayList<>();
            for(int id:ring) {Vertex p=points.get(id);flat.add(drop==0?new Vec2(p.y,p.z):drop==1?new Vec2(p.x,p.z):new Vec2(p.x,p.y));}
            for(int[] t:PolyOps.triangulate(flat)) {
                Vertex a=surface.uv.apply(points.get(ring.get(t[0]))).normal(nx,ny,nz);
                Vertex b=surface.uv.apply(points.get(ring.get(t[1]))).normal(nx,ny,nz);
                Vertex c=surface.uv.apply(points.get(ring.get(t[2]))).normal(nx,ny,nz);
                double gx=(b.y-a.y)*(c.z-a.z)-(b.z-a.z)*(c.y-a.y);
                double gy=(b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z);
                double gz=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
                if(gx*nx+gy*ny+gz*nz<0) {Vertex swap=b;b=c;c=swap;}
                out.triangle(surface.material,a,b,c);
            }
        }
    }
    private static int index(List<Vertex> points,Vertex v) {
        for(int i=0;i<points.size();i++) if(near(points.get(i),v)) return i;
        points.add(v);return points.size()-1;
    }
    private static boolean near(Vertex a,Vertex b) {return Math.abs(a.x-b.x)+Math.abs(a.y-b.y)+Math.abs(a.z-b.z)<2e-6D;}
    private static void addUnique(List<Vertex> points,Vertex v) {
        for(Vertex p:points) if(near(p,v)) return;
        points.add(v);
    }
}
