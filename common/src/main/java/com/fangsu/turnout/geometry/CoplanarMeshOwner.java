package com.fangsu.turnout.geometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 共面重叠只保留先提交的一份真实表面，不抬高枕木，也不改变原 OBJ 的 UV/法线。
 * 在任意倾斜平面内做三角形差集，空间网格只查询附近的原面。
 */
public final class CoplanarMeshOwner {
    private static final double PLANE_EPS=1e-6D;
    private final Map<Long,List<Indexed>> cells=new HashMap<>();

    private static final class Indexed {
        final RailMesh.Face face;
        final double nx,ny,nz,d,minX,maxX,minY,maxY,minZ,maxZ;
        final List<RailMesh.Plane> prism=new ArrayList<>();

        Indexed(RailMesh.Face face) {
            this.face=face;
            RailMesh.Vertex a=face.a(),b=face.b(),c=face.c();
            double ux=b.x()-a.x(),uy=b.y()-a.y(),uz=b.z()-a.z();
            double vx=c.x()-a.x(),vy=c.y()-a.y(),vz=c.z()-a.z();
            double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
            double length=Math.max(1e-15D,Math.sqrt(x*x+y*y+z*z));
            nx=x/length;ny=y/length;nz=z/length;d=-nx*a.x()-ny*a.y()-nz*a.z();
            minX=Math.min(a.x(),Math.min(b.x(),c.x()));maxX=Math.max(a.x(),Math.max(b.x(),c.x()));
            minY=Math.min(a.y(),Math.min(b.y(),c.y()));maxY=Math.max(a.y(),Math.max(b.y(),c.y()));
            minZ=Math.min(a.z(),Math.min(b.z(),c.z()));maxZ=Math.max(a.z(),Math.max(b.z(),c.z()));
            RailMesh.Vertex[] vertices={a,b,c};
            for(int i=0;i<3;i++) {
                RailMesh.Vertex p=vertices[i],q=vertices[(i+1)%3];
                double ex=q.x()-p.x(),ey=q.y()-p.y(),ez=q.z()-p.z();
                double px=ny*ez-nz*ey,py=nz*ex-nx*ez,pz=nx*ey-ny*ex;
                double size=Math.max(1e-15D,Math.sqrt(px*px+py*py+pz*pz));px/=size;py/=size;pz/=size;
                prism.add(new RailMesh.Plane(px,py,pz,-px*p.x()-py*p.y()-pz*p.z()));
            }
        }
        boolean coplanar(Indexed other) {
            if(maxX<other.minX-PLANE_EPS||minX>other.maxX+PLANE_EPS||
                    maxY<other.minY-PLANE_EPS||minY>other.maxY+PLANE_EPS||
                    maxZ<other.minZ-PLANE_EPS||minZ>other.maxZ+PLANE_EPS||
                    Math.abs(nx*other.nx+ny*other.ny+nz*other.nz)<0.999999D)return false;
            for(RailMesh.Vertex v:List.of(other.face.a(),other.face.b(),other.face.c()))
                if(Math.abs(nx*v.x()+ny*v.y()+nz*v.z()+d)>PLANE_EPS)return false;
            return true;
        }
    }

    public RailMesh take(RailMesh input) {
        RailMesh out=new RailMesh();List<Indexed> additions=new ArrayList<>();
        for(RailMesh.Face face:input.faces) {
            Indexed current=new Indexed(face);RailMesh visible=new RailMesh();visible.faces.add(face);
            Set<Indexed> nearby=new LinkedHashSet<>();
            for(int x=cell(current.minX-PLANE_EPS);x<=cell(current.maxX+PLANE_EPS);x++)for(int z=cell(current.minZ-PLANE_EPS);z<=cell(current.maxZ+PLANE_EPS);z++)
                nearby.addAll(cells.getOrDefault(key(x,z),List.of()));
            for(Indexed previous:nearby)if(previous.coplanar(current)) {
                visible=visible.subtract(previous.prism,null);
                if(visible.faces.isEmpty())break;
            }
            out.append(visible);additions.add(current);
        }
        // 原面的覆盖区等于此前所有可见面的并集；索引原面能避免裁剪后碎片数持续增长。
        for(Indexed face:additions)for(int x=cell(face.minX-PLANE_EPS);x<=cell(face.maxX+PLANE_EPS);x++)for(int z=cell(face.minZ-PLANE_EPS);z<=cell(face.maxZ+PLANE_EPS);z++)
            cells.computeIfAbsent(key(x,z),ignored->new ArrayList<>()).add(face);
        return out;
    }
    private static int cell(double value){return (int)Math.floor(value);}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
