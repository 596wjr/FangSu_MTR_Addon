package com.fangsu.turnout.geometry;

import com.fangsu.render.sowcerext.model.RawModel;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 静态轨道与独立尖轨组。复合道岔不再受“只有两根尖轨”的渲染接口限制。 */
public record TurnoutMesh(RawModel staticParts,RawModel bladeStraight,RawModel bladeBranch,
                          @Nullable BladeMotion bladeStraightMotion,@Nullable BladeMotion bladeBranchMotion,
                          double bladeAngleRad,int branchSign,boolean sampled,List<BladePart> parts) {
    public TurnoutMesh { parts=List.copyOf(parts); }
    public TurnoutMesh(RawModel fixed,RawModel straight,RawModel branch,BladeMotion sm,BladeMotion bm,double angle,int sign,boolean sampled) {
        this(fixed,straight,branch,sm,bm,angle,sign,sampled,legacy(straight,branch,sm,bm,angle));
    }
    private static List<BladePart> legacy(RawModel a,RawModel b,BladeMotion am,BladeMotion bm,double angle){List<BladePart> out=new ArrayList<>();if(a!=null&&am!=null)out.add(new BladePart(a,am,angle,1,false,false));if(b!=null&&bm!=null)out.add(new BladePart(b,bm,angle,1,true,false));return out;}
    public static final TurnoutMesh EMPTY=new TurnoutMesh(null,null,null,null,null,0,1,false,List.of());
    public record BladeMotion(double pivotX,double pivotY,double pivotZ,float sign){public double angle(double open,double maxAngle){return open*maxAngle*sign;}}
    /** 三开：0=中股，1~7=第一曲股，8~15=第二曲股；其余道岔维持无电/有电二位。 */
    public record BladePart(RawModel model,BladeMotion motion,double maxAngle,int branchState,boolean branchBlade,boolean threeWay) {
        public double openAt(int signal){int selected=threeWay?(signal==0?0:signal<8?1:2):(signal>0?1:0);boolean active=selected==branchState;return branchBlade?(active?0:1):(active?1:0);}
        public double angle(int previous,int signal,double eased){double open=openAt(previous)+(openAt(signal)-openAt(previous))*eased;return motion.angle(open,maxAngle);}
    }
    public double bladeStraightAngle(double open){return bladeStraightMotion==null?0:bladeStraightMotion.angle(open,bladeAngleRad);}
    public double bladeBranchAngle(double open){return bladeBranchMotion==null?0:bladeBranchMotion.angle(1-open,bladeAngleRad);}
    public boolean hasStatic(){return staticParts!=null&&staticParts.getFaceCount()>0;}
    public boolean hasBlades(){return !parts.isEmpty();}
    public int totalFaces(){int count=staticParts==null?0:staticParts.getFaceCount();for(BladePart part:parts)count+=part.model.getFaceCount();return count;}
    public int totalVertices(){int count=staticParts==null?0:staticParts.getVertexCount();for(BladePart part:parts)count+=part.model.getVertexCount();return count;}
}
