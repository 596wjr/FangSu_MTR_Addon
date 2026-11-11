package com.fangsu.platform;

import java.util.*;

/** 编辑立面只展开重复单元，不依赖世界；自动补齐玻璃同样有可编辑的占位构件。 */
public final class PlatformDoorElevation {
    public record Tile(int index,int repeat,PlatformDoorSettings.Part part,String label,boolean generated,double center,double width) { }
    /** 拖动只改变沿线位置和离地高度；保留模型、尺寸和左右副模型。 */
    public static PlatformDoorSettings.Part translate(PlatformDoorSettings.Part part,double along,double up,double length,boolean fine){
        if(!Double.isFinite(along+up+length)||length<.05||length>128)throw new IllegalArgumentException("invalid_door_translation");
        double step=fine?1d/64:1d/16;
        double offset=Math.max(0,Math.min(length,Math.rint((part.offset()+along)/step)*step));
        double vertical=Math.max(0,Math.min(8,Math.rint((part.vertical()+up)/step)*step));
        return new PlatformDoorSettings.Part(part.kind(),offset,part.lateral(),vertical,part.width(),part.height(),part.model(),part.variant(),part.automatic(),part.secondaryVariant(),part.options());
    }
    public static List<Tile> tiles(double length,List<PlatformDoorSettings.Part> parts,boolean fillGlass){
        var result=new ArrayList<Tile>();
        for(int repeat=0;repeat<2;repeat++){
            double start=repeat*length;
            if(fillGlass){
                var intervals=new ArrayList<double[]>();
                for(var part:parts)if(!part.kind().equals("board")&&Math.abs(part.lateral())<.001&&part.vertical()<.001)
                    intervals.add(new double[]{Math.max(0,part.offset()-part.width()/2),Math.min(length,part.offset()+part.width()/2)});
                intervals.sort(Comparator.comparingDouble(v->v[0]));double cursor=0;
                for(var interval:intervals){gap(result,cursor,Math.max(cursor,interval[0]),start,repeat);cursor=Math.max(cursor,interval[1]);}
                gap(result,cursor,length,start,repeat);
            }
            for(int index=0;index<parts.size();index++){
                var part=parts.get(index);int count=part.kind().equals("door")?(int)Math.rint(part.width()):1;
                for(int leaf=0;leaf<count;leaf++)result.add(new Tile(index,repeat,part,part.kind().equals("board")?"HP":part.kind().equals("glass")?"G":leaf==0?"D <":"D >",false,start+part.offset()-part.width()/2+part.width()*(leaf+.5)/count,part.width()/count));
            }
        }
        return List.copyOf(result);
    }
    private static void gap(List<Tile> result,double from,double to,double start,int repeat){
        if(to-from<.05)return;int count=(int)Math.ceil(to-from);
        for(int i=0;i<count;i++){
            double width=(to-from)/count,center=from+(i+.5)*width;
            var part=new PlatformDoorSettings.Part("glass",center,0,0,width,2,PlatformDoorSettings.DOOR_MODEL,"glass",true,"glass");
            result.add(new Tile(-1,repeat,part,"G",true,start+center,width));
        }
    }
    private PlatformDoorElevation(){ }
}
