package com.fangsu.platform;

import dev.potato.curvedplatform.geometry.*;
import java.util.*;
import java.lang.ref.*;

/** 方速单格宿主适配：薄顶皮及外挑片归属主体，原始几何 JAR 保持不变。 */
public final class PlatformCellMerger {
    public static final int MAX_PARTS=8;
    public record Part(int dx,int dz,CellGeometry geometry){
        public Part{if(Math.abs(dx)>1||Math.abs(dz)>1||dx==0&&dz==0||geometry==null)throw new IllegalArgumentException("invalid_platform_part");}
    }
    // 原几何 record 严格限制局部坐标；身份弱引用不改变它，也不长期持有已卸载的世界网格。
    private static final ReferenceQueue<CellGeometry> DEAD=new ReferenceQueue<>();
    private static final Map<Key,List<Part>> PARTS=new HashMap<>();
    private static final class Key extends WeakReference<CellGeometry>{
        final int hash;Key(CellGeometry cell,ReferenceQueue<CellGeometry> queue){super(cell,queue);hash=System.identityHashCode(cell);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
    }
    private static void cleanup(){Reference<? extends CellGeometry> key;while((key=DEAD.poll())!=null)PARTS.remove(key);}
    public static synchronized List<Part> parts(CellGeometry cell){cleanup();return cell==null?List.of():PARTS.getOrDefault(new Key(cell,null),List.of());}
    public static synchronized void parts(CellGeometry cell,List<Part> parts){
        cleanup();if(parts.size()>MAX_PARTS)throw new IllegalArgumentException("platform_part_limit");
        if(parts.isEmpty())PARTS.remove(new Key(cell,null));else PARTS.put(new Key(cell,DEAD),List.copyOf(parts));
    }
    public static CellGeometry copyParts(CellGeometry from,CellGeometry to){parts(to,parts(from));return to;}
    private static final double SKIN=2.44140625E-4,TOLERANCE=1e-9;
    public static boolean upperSkin(CellGeometry cell){
        return !cell.volumes().isEmpty()&&cell.faces().stream().allMatch(f->f.direction()==1)&&cell.volumes().stream().allMatch(v->v.maxY()-v.minY()<=SKIN+TOLERANCE)&&cell.volumes().stream().mapToDouble(CellGeometry.Footprint::minY).min().orElse(1)==0;
    }
    public static Map<CellGeometry.CellPos,CellGeometry> merge(Map<CellGeometry.CellPos,CellGeometry> source){
        Map<CellGeometry.CellPos,CellGeometry> result=new LinkedHashMap<>();
        source.forEach((pos,cell)->{if(!upperSkin(cell))result.put(pos,lowerThinLayer(cell));});
        source.forEach((pos,cell)->{
            if(!upperSkin(cell))return;
            var below=new CellGeometry.CellPos(pos.x(),pos.y()-1,pos.z());var base=result.get(below);
            List<CellGeometry.Face> faces=new ArrayList<>();List<CellGeometry.Footprint> volumes=new ArrayList<>();List<CellGeometry.Box> physics=new ArrayList<>();
            if(base!=null){faces.addAll(base.faces());volumes.addAll(base.volumes());physics.addAll(base.collisionBoxes());}
            // 上游 texture=4 的向上面是原生地面补集，不能当站台画出来。
            var skinFaces=cell.conforming()?cell.faces():MeshPartitioner.partition(cell.faces(),true);
            for(var face:skinFaces)if(face.texture()!=4)faces.add(new CellGeometry.Face(face.vertices().stream().map(v->new CellGeometry.Vertex(v.x(),1,v.z(),v.u(),v.v())).toList(),face.texture(),face.direction()));
            for(var footprint:cell.volumes())volumes.add(new CellGeometry.Footprint(footprint.polygon(),1-SKIN,1));
            for(var box:cell.collisionBoxes())physics.add(new CellGeometry.Box(box.minX(),1-SKIN,box.minZ(),box.maxX(),1,box.maxZ()));
            double solid=volumes.stream().mapToDouble(v->PolygonClip.area2(v.polygon())*(v.maxY()-v.minY())).sum();
            var combined=new CellGeometry(faces,volumes,solid,true,physics.isEmpty(),physics,false);if(base!=null)copyParts(base,combined);result.put(below,combined);
        });
        collectOverhangs(result);return Collections.unmodifiableMap(result);
    }
    /** 下半砖的顶皮已与主体同格，也向下收入，顶面与碰撞都停在原来的 0.5 高度。 */
    private static CellGeometry lowerThinLayer(CellGeometry cell){
        List<CellGeometry.Footprint> volumes=cell.volumes().stream().map(v->thin(v.minY(),v.maxY())?new CellGeometry.Footprint(v.polygon(),Math.max(0,v.minY()-SKIN),v.minY()):v).toList();
        List<CellGeometry.Box> physics=cell.collisionBoxes().stream().map(b->thin(b.minY(),b.maxY())?new CellGeometry.Box(b.minX(),Math.max(0,b.minY()-SKIN),b.minZ(),b.maxX(),b.minY(),b.maxZ()):b).toList();
        var faces=cell.conforming()?cell.faces():MeshPartitioner.partition(cell.faces(),false);
        return copyParts(cell,new CellGeometry(faces,volumes,cell.solidVolume(),true,physics.isEmpty(),physics,cell.replaceableDecoration()));
    }
    /** 无下方实体的外挑薄片由相邻主体绘制，不再为了这一片表面放置一个方块。 */
    public static boolean thinOnly(CellGeometry cell){return !cell.volumes().isEmpty()&&cell.volumes().stream().allMatch(v->v.maxY()-v.minY()<=SKIN+TOLERANCE)&&cell.faces().stream().allMatch(f->f.direction()==1);}
    private static void collectOverhangs(Map<CellGeometry.CellPos,CellGeometry> cells){
        var bodies=new HashSet<CellGeometry.CellPos>();cells.forEach((pos,cell)->{if(!thinOnly(cell))bodies.add(pos);});
        for(var row:new ArrayList<>(cells.entrySet())){
            var pos=row.getKey();var skin=row.getValue();if(!thinOnly(skin))continue;
            CellGeometry.CellPos selected=null;double best=Double.POSITIVE_INFINITY;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;var target=new CellGeometry.CellPos(pos.x()+dx,pos.y(),pos.z()+dz);if(!bodies.contains(target))continue;
                var body=cells.get(target);if(parts(body).size()>=MAX_PARTS)continue;
                double distance=dx*dx+dz*dz;if(distance<best){selected=target;best=distance;}
            }
            // 纯薄片选区没有主体可依附时，保留原位置作为稳定兜底。
            if(selected==null)continue;var body=cells.get(selected);var attached=new ArrayList<>(parts(body));attached.add(new Part(pos.x()-selected.x(),pos.z()-selected.z(),skin));parts(body,attached);cells.remove(pos);
        }
    }
    private static boolean thin(double low,double high){return Math.abs(low-.5)<TOLERANCE&&high-low<=SKIN+TOLERANCE;}
    private PlatformCellMerger(){ }
}
