package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.NteRailManager;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.extraConfig.*;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.ModNetwork;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.turnout.*;
import com.fangsu.turnout.client.TurnoutRenderHelper;
import com.fangsu.utils.ResourceUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 标记绑定的活动轨道。长度、角度、间距、分段密度均由轨道和模型自动计算。 */
public class BlockEntityRotatingRail extends BlockEntityTurnout implements Syncable {
    private volatile String mode="bend";
    private volatile String railId="pujiang_line_track_only";
    private volatile DynamicModelHolder trackModel;
    private volatile String loadedModel;
    private volatile TurnoutBinding sampledBinding;
    private double modelYOffset;
    private List<Vec3> straight=List.of(),branch=List.of();
    private long retryAt;
    private int soundedSignal;

    public BlockEntityRotatingRail(BlockPos pos,BlockState state){super(ModBlocks.BLOCK_ENTITY_ROTATING_RAIL.get(),pos,state);}
    private boolean rotating(){return getBinding()!=null&&getBinding().markers().size()==4;}
    public String getExtraConfig(String key){return getExtraConfig(key,"");}
    public String getExtraConfig(String key,String fallback){return "railId".equals(key)?railId:"mode".equals(key)?(rotating()?"rotate":mode):fallback;}
    public void setExtraConfig(String key,String value){if("railId".equals(key)){railId=value;reloadModel();sendUpdateC2S();}else if("mode".equals(key))mode="translate".equals(value)?"translate":"bend";}
    @Override public void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putString("mode",mode);tag.putString("railId",railId);}
    @Override public void load(CompoundTag tag){super.load(tag);mode="translate".equals(tag.getString("mode"))?"translate":"bend";railId=tag.contains("railId")?tag.getString("railId"):"pujiang_line_track_only";sampledBinding=null;}
    @Override public void writeC2S(FriendlyByteBuf buf){buf.writeUtf(mode);buf.writeUtf(railId);}
    @Override public void readC2S(FriendlyByteBuf buf){mode="translate".equals(buf.readUtf())?"translate":"bend";railId=buf.readUtf();setChanged();markShapeDirty();}
    public void sendUpdateC2S(){if(level!=null&&level.isClientSide){FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());buf.writeBlockPos(worldPosition);writeC2S(buf);NetworkManager.sendToServer(ModNetwork.BE_SYNC,buf);}setChanged();}
    public void reloadModel(){loadedModel=null;}
    @Override public void requestRebuild(){sampledBinding=null;retryAt=0;super.requestRebuild();}
    @Override public String getMainModelKey(){return "rotatingRail";}
    @Override protected boolean isRenderReady(){return getBinding()!=null;}

    public List<ConfigEntry<?>> getConfigs(){
        List<ConfigEntry<?>> configs=new ArrayList<>();
        // 四红的旋转模式由拓扑唯一决定，不提供任意模式或几何参数输入。
        List<String> modes=rotating()?List.of("rotate"):List.of("bend","translate");
        configs.add(new EnumConfig(ComponentHelper.translatable("ui.fangsu.rotating_rail.mode"),new ConfigSpec("list"),
                modes.stream().map(m->ComponentHelper.translatable("ui.fangsu.rotating_rail.mode."+m)).toList(),
                ()->Math.max(0,modes.indexOf(rotating()?"rotate":mode)),idx->{if(!rotating()){mode=modes.get(idx);sendUpdateC2S();}}).setSaveOnChange(true));
        configs.add(new RunnableConfig(ComponentHelper.translatable("ui.fangsu.rotating_rail.rail"),new ConfigSpec("func"),
                ()->ClientHooks.openRotatingRailModelSelectScreen(this)).setButtonText(ComponentHelper.translatable("ui.fangsu.rotating_rail.rail_select")));
        configs.add(new RunnableConfig(ComponentHelper.translatable("ui.fangsu.rotating_rail.recalculate"),new ConfigSpec("func"),this::requestRebuild));
        return configs;
    }
    @Override public InteractionResult useWithWrench(BlockState state,Level world,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(world.isClientSide)ClientHooks.openRotatingRailConfigScreen(this);return InteractionResult.sidedSuccess(world.isClientSide);}

    @Override public void whenRendering(){
        TurnoutBinding binding=getBinding();if(level==null||binding==null)return;
        if(!binding.equals(sampledBinding)&&level.getGameTime()>=retryAt){
            TurnoutRailGraph graph=TurnoutRailAccess.client(level,worldPosition);
            List<Vec3> a=graph.points(binding.straightPath()),b=graph.points(binding.branchPath());
            if(a.size()<2||b.size()<2){retryAt=level.getGameTime()+20;return;}
            straight=a;branch=b;sampledBinding=binding;retryAt=0;
        }
        if(!binding.equals(sampledBinding))return;
        NteRail rail=NteRailManager.getInstance().getRail(railId);
        String path=rail==null||rail.model()==null||rail.model().isEmpty()?"mtrsteamloco:rails/pujiang_line_track_only.obj":rail.model();
        double spacing=rail==null?0.625:rail.repeatInterval();
        modelYOffset=rail!=null&&rail.modelYOffset()!=null&&Double.isFinite(rail.modelYOffset())?-rail.modelYOffset():0;
        if(!Double.isFinite(spacing)||spacing<0.05)spacing=0.625;
        if(trackModel==null||!Objects.equals(loadedModel,path+"/"+(rail==null||rail.flipV()))){
            try{
                var raw=ResourceUtil.loadModel(new ResourceLocation(path),rail==null||rail.flipV());
                if(raw==null)return;
                if(trackModel==null)trackModel=new DynamicModelHolder();
                trackModel.uploadLater(raw);loadedModel=path+"/"+(rail==null||rail.flipV());
            }catch(Exception e){Main.LOGGER.warn("[RotatingRail] 轨道模型加载失败 {}",path,e);return;}
        }
        if(!trackModel.hasUploadedModel())return;
        double progress=getSwitchProgress(TurnoutRenderHelper.partialTick());
        progress=progress*progress*(3-2*progress);
        double from=getPreviousSignal()>0?0:1,to=getSignal()>0?0:1,t=from+(to-from)*progress;
        if(rotating())drawRotation(t,spacing);
        else if("translate".equals(mode)){
            Vec3 shift=branch.get(branch.size()-1).subtract(straight.get(straight.size()-1));
            // 一条直道 + 一条曲道：不再在曲道所在位置额外绘制第二条直道。
            draw(straight,shift.scale(t-1),spacing);
            draw(branch,shift.scale(t),spacing);
        }else{
            int count=Math.max(2,(int)Math.ceil(Math.max(length(straight),length(branch))/spacing));
            List<Vec3> blended=new ArrayList<>();
            Arc a=new Arc(straight),b=new Arc(branch);
            for(int i=0;i<=count;i++){double u=(double)i/count;blended.add(a.at(u).scale(1-t).add(b.at(u).scale(t)));}
            draw(blended,Vec3.ZERO,spacing);
        }
        if(soundedSignal!=getSignal()){soundedSignal=getSignal();var sound=TurnoutRenderHelper.switchSoundOrNull();if(sound!=null)scriptContext.playSound(sound,0.6F,1);}
    }
    private void drawRotation(double t,double spacing){
        Vec3 a=straight.get(0),b=straight.get(straight.size()-1),c=branch.get(0),d=branch.get(branch.size()-1);
        Vec3 pivot=a.add(b).scale(0.5),v=b.subtract(a),w=d.subtract(c);
        double angle=Math.atan2(v.x*w.z-v.z*w.x,v.x*w.x+v.z*w.z)*t,cos=Math.cos(angle),sin=Math.sin(angle);
        List<Vec3> points=new ArrayList<>();
        for(Vec3 p:straight){Vec3 q=p.subtract(pivot);points.add(pivot.add(q.x*cos-q.z*sin,q.y,q.x*sin+q.z*cos));}
        draw(points,Vec3.ZERO,spacing);
    }
    private void draw(List<Vec3> points,Vec3 shift,double spacing){
        Arc arc=new Arc(points);double total=arc.length();int count=Math.max(1,Math.min(4096,(int)Math.ceil(total/spacing)));
        for(int i=0;i<count;i++){
            Vec3 a=arc.at((double)i/count),b=arc.at((double)(i+1)/count),p=a.add(b).scale(0.5).add(shift),d=b.subtract(a);
            Matrices pose=new Matrices();
            // 抵消基类的半格中心偏移，与道岔一样按世界轨道锚点摆放。
            pose.translate((float)(p.x-worldPosition.getX()-0.5),(float)(p.y-worldPosition.getY()+modelYOffset),(float)(p.z-worldPosition.getZ()-0.5));
            pose.rotateY((float)Math.atan2(d.x,d.z));
            pose.rotateX((float)-Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));
            scriptContext.drawModel(trackModel,pose);
        }
    }
    private record Arc(List<Vec3> points,double[] distances) {
        Arc(List<Vec3> points){this(points,new double[points.size()]);for(int i=1;i<points.size();i++)distances[i]=distances[i-1]+points.get(i).distanceTo(points.get(i-1));}
        double length(){return distances[distances.length-1];}
        Vec3 at(double u){double distance=length()*u;int i=Arrays.binarySearch(distances,distance);if(i>=0)return points.get(i);i=-i-1;if(i<=0)return points.get(0);if(i>=points.size())return points.get(points.size()-1);double span=distances[i]-distances[i-1];return span<=1e-9?points.get(i):points.get(i-1).add(points.get(i).subtract(points.get(i-1)).scale((distance-distances[i-1])/span));}
    }
    private static double length(List<Vec3> points){return new Arc(points).length();}
    @Override public void setRemoved(){super.setRemoved();if(trackModel!=null){trackModel.close();trackModel=null;}}
}
