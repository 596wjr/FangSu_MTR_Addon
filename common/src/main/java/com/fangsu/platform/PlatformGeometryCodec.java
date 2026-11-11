package com.fangsu.platform;

import dev.potato.curvedplatform.geometry.CellGeometry;
import net.minecraft.nbt.*;
import java.util.*;

/** 方速存档适配：保留上游几何的双精度顶点、UV、物理盒与装饰属性。 */
public final class PlatformGeometryCodec {
    private static ListTag numbers(double... values){ListTag list=new ListTag();for(double value:values)list.add(DoubleTag.valueOf(value));return list;}
    public static CompoundTag write(CellGeometry g){
        CompoundTag tag=new CompoundTag();ListTag faces=new ListTag(),volumes=new ListTag(),boxes=new ListTag();
        for(var face:g.faces()){
            CompoundTag f=new CompoundTag();f.putInt("texture",face.texture());f.putInt("direction",face.direction());ListTag vertices=new ListTag();
            for(var v:face.vertices())vertices.add(numbers(v.x(),v.y(),v.z(),v.u(),v.v()));f.put("vertices",vertices);faces.add(f);
        }
        for(var volume:g.volumes()){
            CompoundTag v=new CompoundTag();v.putDouble("low",volume.minY());v.putDouble("high",volume.maxY());ListTag polygon=new ListTag();
            for(var p:volume.polygon())polygon.add(numbers(p.x(),p.z()));v.put("polygon",polygon);volumes.add(v);
        }
        tag.put("faces",faces);tag.put("volumes",volumes);tag.putDouble("solid",g.solidVolume());tag.putBoolean("conforming",g.conforming());
        tag.putBoolean("collisionless",g.collisionlessBase());tag.putBoolean("decoration",g.replaceableDecoration());
        if(g.physicalBoxes()!=null){for(var b:g.physicalBoxes())boxes.add(numbers(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));tag.put("physical",boxes);}
        var attached=com.fangsu.platform.PlatformCellMerger.parts(g);
        if(!attached.isEmpty()){ListTag parts=new ListTag();for(var part:attached){CompoundTag row=new CompoundTag();row.putInt("dx",part.dx());row.putInt("dz",part.dz());row.put("mesh",write(part.geometry()));parts.add(row);}tag.put("parts",parts);}
        return tag;
    }
    public static CellGeometry read(CompoundTag tag){
        var result=readBase(tag);ListTag rows=tag.getList("parts",Tag.TAG_COMPOUND);if(rows.size()>PlatformCellMerger.MAX_PARTS)throw new IllegalArgumentException("platform_part_limit");
        List<PlatformCellMerger.Part> parts=new ArrayList<>();for(int i=0;i<rows.size();i++){var row=rows.getCompound(i);var mesh=row.getCompound("mesh");if(mesh.contains("parts"))throw new IllegalArgumentException("nested_platform_parts");var geometry=readBase(mesh);if(!PlatformCellMerger.thinOnly(geometry))throw new IllegalArgumentException("not_platform_skin");parts.add(new PlatformCellMerger.Part(row.getInt("dx"),row.getInt("dz"),geometry));}
        PlatformCellMerger.parts(result,parts);return result;
    }
    private static CellGeometry readBase(CompoundTag tag){
        ListTag rawFaces=tag.getList("faces",Tag.TAG_COMPOUND),rawVolumes=tag.getList("volumes",Tag.TAG_COMPOUND);
        if(rawFaces.size()>CellGeometry.MAX_FACES||rawVolumes.size()>CellGeometry.MAX_VOLUMES)throw new IllegalArgumentException("geometry_limit");
        List<CellGeometry.Face> faces=new ArrayList<>();List<CellGeometry.Footprint> volumes=new ArrayList<>();
        for(int i=0;i<rawFaces.size();i++){
            var f=rawFaces.getCompound(i);var raw=f.getList("vertices",Tag.TAG_LIST);if(raw.size()>8)throw new IllegalArgumentException("geometry_limit");
            List<CellGeometry.Vertex> vertices=new ArrayList<>();for(int n=0;n<raw.size();n++){ListTag v=(ListTag)raw.get(n);if(v.size()!=5)throw new IllegalArgumentException();vertices.add(new CellGeometry.Vertex(v.getDouble(0),v.getDouble(1),v.getDouble(2),v.getDouble(3),v.getDouble(4)));}
            faces.add(new CellGeometry.Face(vertices,f.getInt("texture"),f.getInt("direction")));
        }
        for(int i=0;i<rawVolumes.size();i++){
            var v=rawVolumes.getCompound(i);var raw=v.getList("polygon",Tag.TAG_LIST);if(raw.size()>8)throw new IllegalArgumentException("geometry_limit");
            List<CellGeometry.Vec2> polygon=new ArrayList<>();for(int n=0;n<raw.size();n++){ListTag p=(ListTag)raw.get(n);if(p.size()!=2)throw new IllegalArgumentException();polygon.add(new CellGeometry.Vec2(p.getDouble(0),p.getDouble(1)));}
            volumes.add(new CellGeometry.Footprint(polygon,v.getDouble("low"),v.getDouble("high")));
        }
        List<CellGeometry.Box> boxes=null;
        if(tag.contains("physical",Tag.TAG_LIST)){
            ListTag raw=tag.getList("physical",Tag.TAG_LIST);if(raw.size()>4096)throw new IllegalArgumentException("geometry_limit");boxes=new ArrayList<>();
            for(int i=0;i<raw.size();i++){ListTag b=(ListTag)raw.get(i);if(b.size()!=6)throw new IllegalArgumentException();double x=b.getDouble(0),y=b.getDouble(1),z=b.getDouble(2),xx=b.getDouble(3),yy=b.getDouble(4),zz=b.getDouble(5);
                if(!Double.isFinite(x+y+z+xx+yy+zz)||x< -1e-6||y< -1e-6||z< -1e-6||xx>1.000001||yy>1.000001||zz>1.000001||x>xx||y>yy||z>zz)throw new IllegalArgumentException();if(x<xx&&y<yy&&z<zz)boxes.add(new CellGeometry.Box(Math.max(0,x),Math.max(0,y),Math.max(0,z),Math.min(1,xx),Math.min(1,yy),Math.min(1,zz)));}
        }
        double solid=tag.getDouble("solid");if(!Double.isFinite(solid))throw new IllegalArgumentException();
        return new CellGeometry(faces,volumes,solid,tag.getBoolean("conforming"),tag.getBoolean("collisionless"),boxes,tag.getBoolean("decoration"));
    }
}
