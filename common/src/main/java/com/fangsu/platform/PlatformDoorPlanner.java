package com.fangsu.platform;

import java.util.*;
import static com.fangsu.platform.CurvedPlatformPlanner.Point;

/** 纯几何：偏移基线按弧长重复；门使用刚性弦长，固定玻璃逐短段随曲线补齐。 */
public final class PlatformDoorPlanner {
    public record Anchor(int x,int y,int z) { }
    public record Piece(String kind,double dx,double dy,double dz,double yaw,double width,double height,String model,String variant,Map<String,String> options) { }
    public record Plan(Map<Anchor,List<Piece>> cells,List<Point> points,int doors,int glass,List<String> issues) {public boolean executable(){return issues.isEmpty()&&!cells.isEmpty();}}
    private record Path(List<Point> p,double[] s,double length){
        Point at(double distance){int i=Arrays.binarySearch(s,distance);if(i>=0)return p.get(i);i=Math.max(1,Math.min(p.size()-1,-i-1));double t=Math.max(0,Math.min(1,(distance-s[i-1])/(s[i]-s[i-1])));return p.get(i-1).interpolate(p.get(i),t);}
        double yaw(double distance){Point a=at(Math.max(0,distance-.05)),b=at(Math.min(length,distance+.05));return Math.atan2(-(b.z()-a.z()),b.x()-a.x());}
    }
    private record Interval(double start,double end) { }
    private record Adjustment(double start,double end,double low,double high) { }
    private record Instance(double start,PlatformDoorSettings.Unit unit) { }
    private record Mapping(List<Interval> rigid,double factor,double offset){double at(double distance){double fixed=0;for(var span:rigid)fixed+=Math.max(0,Math.min(distance,span.end())-Math.max(0,span.start()));return offset+fixed+(distance-fixed)*factor;}}
    private static final class Builder {
        final Map<Anchor,List<Piece>> cells=new LinkedHashMap<>();final List<Point> points=new ArrayList<>();final List<String> issues=new ArrayList<>();int doors,glass,count;
        void add(String kind,Point center,double yaw,double width,double height,String model,String variant){add(kind,center,yaw,width,height,model,variant,Map.of());}
        void add(String kind,Point center,double yaw,double width,double height,String model,String variant,Map<String,String> options){
            if(++count>4096)throw new IllegalArgumentException("door_component_limit");Anchor anchor=new Anchor((int)Math.floor(center.x()),(int)Math.floor(center.y()),(int)Math.floor(center.z()));
            var list=cells.computeIfAbsent(anchor,k->new ArrayList<>());if(list.size()>=16)throw new IllegalArgumentException("door_anchor_limit");list.add(new Piece(kind,center.x()-anchor.x()-.5,center.y()-anchor.y(),center.z()-anchor.z()-.5,yaw,width,height,model,variant,options));if(!kind.endsWith("_proxy"))outline(kind,center,yaw,width,height);if(kind.equals("door"))doors++;if(kind.equals("glass"))glass++;
        }
        /** 预览使用真实构件包围框的十二条边，不再把吊板当作两米高的门框。 */
        private void outline(String kind,Point center,double yaw,double width,double height){
            double cos=Math.cos(yaw),sin=Math.sin(yaw),depth=kind.equals("board")?5d/16:1d/16;
            Point[] bottom=new Point[4],top=new Point[4];double[] horizontal={-width/2,width/2,width/2,-width/2},normal={-depth/2,-depth/2,depth/2,depth/2};
            for(int i=0;i<4;i++){
                double x=center.x()+cos*horizontal[i]+sin*normal[i],z=center.z()-sin*horizontal[i]+cos*normal[i];
                bottom[i]=new Point(x,center.y(),z);top[i]=new Point(x,center.y()+height,z);
            }
            for(int i=0;i<4;i++){edge(bottom[i],bottom[(i+1)%4]);edge(top[i],top[(i+1)%4]);edge(bottom[i],top[i]);}
        }
        private void edge(Point a,Point b){int steps=Math.max(1,(int)Math.ceil(Math.hypot(a.distance(b),a.y()-b.y())/.5));for(int i=0;i<steps;i++)points.add(a.interpolate(b,i/(double)steps));}
        /** 协议仍最多传 4096 个点；超限时均匀取样整段，不能截掉后半段站台。 */
        List<Point> previewPoints(){
            if(points.size()<=4096)return List.copyOf(points);List<Point> sampled=new ArrayList<>(4096);
            for(int i=0;i<4096;i++)sampled.add(points.get((int)((i+.5)*points.size()/4096d)));return List.copyOf(sampled);
        }
        void issue(String key){if(!issues.contains(key))issues.add(key);}
    }
    public static Plan plan(List<Point> samples,double edgeOffset,double floor,boolean left,PlatformDoorSettings settings){
        if(samples.size()<2||samples.size()>8193)throw new IllegalArgumentException("invalid_door_path");if(settings.reverse()){samples=new ArrayList<>(samples);Collections.reverse(samples);left=!left;}
        Builder out=new Builder();List<Point> base=new ArrayList<>();double y=samples.get(0).y(),offset=(edgeOffset+.5+settings.baselineOffset())*(left?1:-1);
        for(int i=0;i<samples.size();i++){
            Point p=samples.get(i),a=samples.get(Math.max(0,i-1)),b=samples.get(Math.min(samples.size()-1,i+1));if(Math.abs(p.y()-y)>.01)out.issue("slope");double dx=b.x()-a.x(),dz=b.z()-a.z(),n=Math.hypot(dx,dz);if(n<1e-8)continue;base.add(new Point(p.x()+dz/n*offset,floor,p.z()-dx/n*offset));
        }
        if(base.size()<2)throw new IllegalArgumentException("invalid_door_path");double[] mileage=new double[base.size()];for(int i=1;i<base.size();i++){double d=base.get(i).distance(base.get(i-1));if(d<1e-7)out.issue("sharp_curve");mileage[i]=mileage[i-1]+Math.max(d,1e-7);}Path path=new Path(base,mileage,mileage[mileage.length-1]);if(path.length()>2304)throw new IllegalArgumentException("door_path_limit");
        var standard=new PlatformDoorSettings.Unit(settings.unitLength(),settings.components());
        double available=path.length()-settings.firstOffset();if(available<=0){out.issue("empty");return new Plan(Map.of(),List.of(),0,0,List.copyOf(out.issues));}
        double ends=(settings.firstUnit()==null?0:settings.firstUnit().length())+(settings.lastUnit()==null?0:settings.lastUnit().length());int endCount=(settings.firstUnit()==null?0:1)+(settings.lastUnit()==null?0:1);
        if(settings.units()>0&&settings.units()<endCount)return new Plan(Map.of(),List.of(),0,0,List.of("too_few_units"));
        int units=settings.units()==0?Math.max(endCount,(int)Math.ceil(Math.max(0,available-ends)/settings.unitLength())+endCount):settings.units();units=Math.min(2304,Math.max(1,units));
        List<Instance> instances=new ArrayList<>();double total=0;
        for(int u=0;u<units;u++){var unit=u==0&&settings.firstUnit()!=null?settings.firstUnit():u==units-1&&settings.lastUnit()!=null?settings.lastUnit():standard;instances.add(new Instance(total,unit));total+=unit.length();}
        List<Interval> rigid=new ArrayList<>();for(var instance:instances)for(var part:instance.unit().components())if(part.kind().equals("door"))rigid.add(new Interval(instance.start()+part.offset()-part.width()/2,instance.start()+part.offset()+part.width()/2));
        rigid.sort(Comparator.comparingDouble(Interval::start));double solid=0,end=0;for(var span:rigid){if(span.start()<end-1e-5)out.issue("overlap");solid+=Math.max(0,Math.min(total,span.end())-Math.max(end,span.start()));end=Math.max(end,span.end());}
        if(settings.fitLength()&&total-solid<.00001&&Math.abs(available-total)>.00001)return new Plan(Map.of(),List.of(),0,0,List.of("no_flexible"));
        double factor=settings.fitLength()&&total-solid>=.00001?(available-solid)/(total-solid):1;
        if(factor<=.01){out.issue("too_short");return new Plan(Map.of(),List.of(),0,0,List.copyOf(out.issues));}
        Mapping mapping=new Mapping(rigid,factor,settings.firstOffset());
        List<Interval> occupied=new ArrayList<>();List<Adjustment> joins=new ArrayList<>();
        // 门宽保持原样；玻璃及其上方吊板按同一分段映射分担长度差。
        for(int pass=0;pass<2;pass++)for(var instance:instances)for(var original:instance.unit().components()){
            if(original.kind().equals("door")!=(pass==0))continue;
            double source=instance.start()+original.offset(),center=pass==0?mapping.at(source):joined(source,joins,mapping,total,settings.fitLength()?path.length():settings.firstOffset()+total);if(center<0||center>path.length())continue;
            var part=original;double nativeDepth=part.kind().equals("door")?13d/32:part.kind().equals("glass")?9d/32:11d/32;
            if(part.kind().equals("door")){
                double low=part.width()/2,high=Math.min(Math.min(center,path.length()-center),low*1.5+.25);if(high<low)continue;
                if(path.at(center-high).distance(path.at(center+high))+1e-6<part.width()){out.issue("sharp_curve");continue;}
                for(int n=0;n<45;n++){double mid=(low+high)/2;if(path.at(center-mid).distance(path.at(center+mid))<part.width())low=mid;else high=mid;}
                double half=(low+high)/2;joins.add(new Adjustment(source-part.width()/2,source+part.width()/2,center-half,center+half));joins.sort(Comparator.comparingDouble(Adjustment::start));Point a=path.at(center-half),b=path.at(center+half);double yaw=Math.atan2(-(b.z()-a.z()),b.x()-a.x());
                if(angle(yaw,path.yaw(center-half))>.35||angle(yaw,path.yaw(center+half))>.35)out.issue("sharp_curve");
                int leaves=(int)Math.rint(part.width());for(int leaf=0;leaf<leaves;leaf++){Point p=a.interpolate(b,(leaf+.5)/leaves);p=shift(p,yaw,(part.lateral()-nativeDepth)*(left?1:-1),part.vertical());boolean leftLeaf=left?leaf>=leaves/2.0:leaf<leaves/2.0;
                    String side=part.options().getOrDefault("isAuto","true").equals("true")?(leftLeaf?"left.":"right."):switch(part.options().getOrDefault("doorSide","0")){case "1"->"right.";case "2"->"flex.";default->"left.";};
                    out.add("door",p,yaw+(left?Math.PI:0),1,2,part.model(),side+part.variant(),part.options());}
                if(Math.abs(part.lateral())<.001&&part.vertical()<.001)occupied.add(new Interval(center-half,center+half));
            }else{
                if(part.kind().equals("board")){
                    double targetEnd=settings.fitLength()?path.length():settings.firstOffset()+total;
                    double from=Math.max(0,joined(source-part.width()/2,joins,mapping,total,targetEnd));
                    double to=Math.min(path.length(),joined(source+part.width()/2,joins,mapping,total,targetEnd));
                    if(to-from<.005)continue;
                    int pieces=Math.max(1,(int)Math.ceil((to-from-1e-8)/8));
                    for(int k=0;k<pieces;k++){
                        double aa=from+(to-from)*k/pieces,bb=from+(to-from)*(k+1)/pieces;
                        Point a=shift(path.at(aa),path.yaw(aa),(part.lateral()-nativeDepth)*(left?1:-1),part.vertical());
                        Point b=shift(path.at(bb),path.yaw(bb),(part.lateral()-nativeDepth)*(left?1:-1),part.vertical());
                        double width=a.distance(b);
                        if(width>=.005)out.add("board",a.interpolate(b,.5),Math.atan2(-(b.z()-a.z()),b.x()-a.x())+(left?Math.PI:0),width,part.height(),part.model(),part.variant(),part.options());
                    }
                }
                else{
                    double from=Math.max(0,joined(source-part.width()/2,joins,mapping,total,settings.fitLength()?path.length():settings.firstOffset()+total)),to=Math.min(path.length(),joined(source+part.width()/2,joins,mapping,total,settings.fitLength()?path.length():settings.firstOffset()+total));flexible(path,from,to,part,left,out);
                    if(Math.abs(part.lateral())<.001&&part.vertical()<.001)occupied.add(new Interval(from,to));
                }
            }
        }
        occupied.sort(Comparator.comparingDouble(Interval::start));double cursor=settings.firstOffset();for(var interval:occupied){if(interval.start()<cursor-1e-4)out.issue("overlap");if(settings.fillGlass())fill(path,cursor,interval.start(),out,left);cursor=Math.max(cursor,interval.end());}if(settings.fillGlass())fill(path,cursor,path.length(),out,left);
        if(out.cells.isEmpty())out.issue("empty");addCollisionCells(out);Map<Anchor,List<Piece>> cells=new LinkedHashMap<>();out.cells.forEach((p,c)->cells.put(p,List.copyOf(c)));return new Plan(Collections.unmodifiableMap(cells),out.previewPoints(),out.doors,out.glass,List.copyOf(out.issues));
    }
    /** 多格物理形状按世界格补宿主，门滑动全过程及高处吊板都可选中和碰撞。 */
    private static void addCollisionCells(Builder out){
        var original=new LinkedHashMap<Anchor,List<Piece>>();out.cells.forEach((a,p)->original.put(a,List.copyOf(p)));
        for(var row:original.entrySet())for(var p:row.getValue()){
            double cx=row.getKey().x()+.5+p.dx(),cy=row.getKey().y()+p.dy(),cz=row.getKey().z()+.5+p.dz(),cos=Math.cos(p.yaw()),sin=Math.sin(p.yaw());
            double depth=p.kind().equals("board")?5d/16:1d/16,ex=Math.abs(cos)*p.width()/2+Math.abs(sin)*depth/2,ez=Math.abs(sin)*p.width()/2+Math.abs(cos)*depth/2;
            double slide=p.kind().equals("door")&&!p.variant().startsWith("flex.")?(p.variant().startsWith("right")?1:-1):0,xx=cx+cos*slide,zz=cz-sin*slide;
            int x0=(int)Math.floor(Math.min(cx,xx)-ex-1d/16+1e-7),x1=(int)Math.floor(Math.max(cx,xx)+ex+1d/16-1e-7),z0=(int)Math.floor(Math.min(cz,zz)-ez-1d/16+1e-7),z1=(int)Math.floor(Math.max(cz,zz)+ez+1d/16-1e-7);
            for(int y=(int)Math.floor(cy+1e-7);y<=(int)Math.floor(cy+p.height()-1e-7);y++)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                Anchor a=new Anchor(x,y,z);if(a.equals(row.getKey()))continue;
                var list=out.cells.computeIfAbsent(a,k->new ArrayList<>());if(list.size()>=16||++out.count>4096)throw new IllegalArgumentException("door_anchor_limit");
                list.add(new Piece(p.kind()+"_proxy",cx-x-.5,cy-y,cz-z-.5,p.yaw(),p.width(),p.height(),p.model(),p.variant(),p.options()));
            }
        }
    }
    private static double joined(double source,List<Adjustment> joins,Mapping mapping,double total,double targetEnd){
        double previous=0,target=mapping.offset();
        for(var span:joins){
            if(source<span.start())return target+(span.low()-target)*(source-previous)/Math.max(.000001,span.start()-previous);
            if(source<=span.end())return span.low()+(span.high()-span.low())*(source-span.start())/Math.max(.000001,span.end()-span.start());
            previous=span.end();target=span.high();
        }
        return target+(targetEnd-target)*(source-previous)/Math.max(.000001,total-previous);
    }
    private static void flexible(Path path,double from,double to,PlatformDoorSettings.Part part,boolean left,Builder out){
        if(to-from<.005)return;int count=Math.max(1,(int)Math.ceil((to-from)/.75));
        for(int k=0;k<count;k++){
            double aa=from+(to-from)*k/count,bb=from+(to-from)*(k+1)/count;
            double depth=part.kind().equals("board")?11d/32:9d/32;
            Point a=shift(path.at(aa),path.yaw(aa),(part.lateral()-depth)*(left?1:-1),part.vertical()),b=shift(path.at(bb),path.yaw(bb),(part.lateral()-depth)*(left?1:-1),part.vertical());
            double yaw=Math.atan2(-(b.z()-a.z()),b.x()-a.x()),width=a.distance(b);if(width<.005)continue;
            out.add(part.kind(),a.interpolate(b,.5),yaw+(left?Math.PI:0),width,part.height(),part.model(),part.kind().equals("glass")?(part.automatic()?"auto":"pair")+"|"+part.variant()+"|"+part.secondaryVariant():part.variant(),part.options());
        }
    }
    private static Point shift(Point p,double yaw,double lateral,double vertical){return new Point(p.x()-Math.sin(yaw)*lateral,p.y()+vertical,p.z()-Math.cos(yaw)*lateral);}
    private static double angle(double a,double b){return Math.abs(Math.atan2(Math.sin(a-b),Math.cos(a-b)));}
    private static void fill(Path path,double from,double to,Builder out,boolean left){flexible(path,from,to,new PlatformDoorSettings.Part("glass",0,0,0,1,2,PlatformDoorSettings.DOOR_MODEL,"glass",true,"glass"),left,out);}
    private PlatformDoorPlanner(){ }
}
