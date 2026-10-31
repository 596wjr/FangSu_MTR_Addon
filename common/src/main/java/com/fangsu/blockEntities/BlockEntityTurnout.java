package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.network.TurnoutBuildRequestPayload;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.turnout.*;
import com.fangsu.turnout.client.TurnoutRenderHelper;
import com.fangsu.turnout.geometry.TurnoutGeometry;
import com.fangsu.turnout.geometry.TurnoutMesh;
import com.fangsu.turnout.marker.NodeMarker;
import com.fangsu.turnout.marker.NodeMarkerStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** 服务端保存完整绑定，客户端按同一代绑定构建和上传；红石变化只更新尖轨矩阵。 */
public class BlockEntityTurnout extends BaseObjBlockEntity {
    public static final String DEFAULT_RAIL_MODEL="mtr:default";
    public static final int SWITCH_TICKS=8;
    private volatile TurnoutBinding binding;
    private volatile String railModelId=DEFAULT_RAIL_MODEL;
    private volatile int signal,previousSignal;
    private volatile long switchChangedAt;
    private volatile long drawnSwitchTime=Long.MIN_VALUE;
    private volatile boolean building,buildingGeometry;
    private volatile TurnoutMesh mesh;
    private volatile GeometryKey builtKey;
    private volatile long nextRetryTick;
    private volatile boolean drawDirty=true,awaitingUpload;
    private volatile long geometryVersion;
    private java.util.concurrent.Future<?> geometryTask;
    private int retries;
    private volatile boolean warnedGeometryFailure;
    private volatile DynamicModelHolder staticHolder;
    private volatile List<UploadedBlade> blades=List.of();
    private int soundedSignal;
    private record UploadedBlade(DynamicModelHolder holder,TurnoutMesh.BladePart part) { }
    private record GeometryKey(TurnoutBinding binding,String model) { }
    public BlockEntityTurnout(BlockPos pos,BlockState state){this(ModBlocks.BLOCK_ENTITY_TURNOUT.get(),pos,state);}
    protected BlockEntityTurnout(net.minecraft.world.level.block.entity.BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected int getPreviousSignal(){return previousSignal;}

    @Override public void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(binding!=null)tag.put("turnout_binding",binding.toTag());tag.putString("turnout_rail_model",getRailModelId());tag.putInt("turnout_signal",signal);tag.putInt("turnout_previous_signal",previousSignal);tag.putBoolean("turnout_powered",signal>0);tag.putLong("turnout_switch_time",switchChangedAt);}
    @Override public synchronized void load(CompoundTag tag) {
        int oldSignal=signal;long oldTime=switchChangedAt;
        super.load(tag);TurnoutBinding incoming=tag.contains("turnout_binding")?TurnoutBinding.fromTag(tag.getCompound("turnout_binding")):null;
        String model=tag.getString("turnout_rail_model");if(model.isEmpty())model=DEFAULT_RAIL_MODEL;
        if(!Objects.equals(binding,incoming)||!model.equals(railModelId)){invalidateGeometry();}
        binding=incoming;railModelId=model;signal=tag.contains("turnout_signal")?tag.getInt("turnout_signal"):(tag.getBoolean("turnout_powered")?15:0);
        previousSignal=tag.contains("turnout_previous_signal")?tag.getInt("turnout_previous_signal"):signal;switchChangedAt=tag.getLong("turnout_switch_time");building=false;
        if(oldSignal!=signal||oldTime!=switchChangedAt)drawDirty=true;
        if(incoming==null)releaseModels();
    }
    public boolean hasBinding(){return binding!=null;}
    @Nullable public TurnoutBinding getBinding(){return binding;}
    public synchronized void setBinding(@Nullable TurnoutBinding value) {
        if(!Objects.equals(binding,value)){invalidateGeometry();}
        binding=value;building=false;if(value!=null&&value.railModelId()!=null&&!value.railModelId().isEmpty())railModelId=value.railModelId();
        if(value==null)releaseModels();
        setChanged();syncToClient();
    }
    public void clearBinding(){setBinding(null);}
    public String getRailModelId(){return railModelId==null||railModelId.isEmpty()?DEFAULT_RAIL_MODEL:railModelId;}
    public synchronized void setRailModelId(String model){String next=model==null||model.isEmpty()?DEFAULT_RAIL_MODEL:model;if(!next.equals(railModelId)){railModelId=next;invalidateGeometry();}}
    public boolean isBuilding(){return building||buildingGeometry;}
    public void markBuildFinished(){building=false;}
    public void requestRebuild() {
        if(level==null||!level.isClientSide)return;
        List<NodeMarker> markers=binding==null?NodeMarkerStore.getInstance().snapshot():binding.markers();
        List<TurnoutRailGraph.Link> links=new ArrayList<>();
        if(binding!=null)for(TurnoutTrack track:binding.tracks())for(int i=1;i<track.nodes().size();i++)links.add(new TurnoutRailGraph.Link(track.nodes().get(i-1),track.nodes().get(i)));
        building=true;invalidateGeometry();if(binding!=null)TurnoutGeometryBuilder.invalidate(binding,getRailModelId());
        TurnoutBuildRequestPayload.sendToServer(new TurnoutBuildRequestPayload.Payload(worldPosition,markers,getRailModelId(),false,List.copyOf(new LinkedHashSet<>(links))));
    }
    public void requestUnbind(){if(level==null||!level.isClientSide)return;building=true;TurnoutBuildRequestPayload.sendToServer(TurnoutBuildRequestPayload.Payload.ofUnbind(worldPosition,getRailModelId()));}
    // 兼容旧调用入口；初次放置不再依赖客户端 tick 或渲染回调发包。
    public void requestInitialBuild(){ }
    public void clientTick(){ }
    public boolean isPowered(){return signal>0;}
    public void setPowered(boolean powered){setSignal(powered?15:0);}
    public int getSignal(){return signal;}
    public long getSwitchChangedAtGameTime(){return switchChangedAt;}
    public void refreshPowered(){if(level!=null&&!level.isClientSide)setSignal(level.getBestNeighborSignal(worldPosition));}
    @Override public void serverTick(){refreshPowered();}
    private void setSignal(int value){int next=Math.max(0,Math.min(15,value));if(next==signal)return;previousSignal=signal;signal=next;switchChangedAt=level==null?0:level.getGameTime();setChanged();syncToClient();}
    public float getSwitchProgress(float partial){if(level==null||switchChangedAt<=0)return 1;return Math.max(0,Math.min(1,(level.getGameTime()-switchChangedAt+partial)/SWITCH_TICKS));}
    private void syncToClient(){if(level!=null&&!level.isClientSide)markShapeDirty();}
    @Override public InteractionResult useWithWrench(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(level.isClientSide)com.fangsu.client.ClientHooks.openTurnoutConfigScreen(this);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override public InteractionResult whenUseWithBrush(Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return useWithWrench(getBlockState(),level,pos,player,hand,hit);}

    @Override protected boolean isRenderReady() {
        if(drawDirty||awaitingUpload)return true;
        if(level==null||binding==null)return false;
        TurnoutMesh current=mesh;
        if(!buildingGeometry&&(current==null||!current.sampled())&&level.getGameTime()>=nextRetryTick)return true;
        if(drawnSwitchTime!=switchChangedAt)return true;
        if(staticHolder!=null&&!staticHolder.hasUploadedModel())return true;
        for(UploadedBlade blade:blades)if(!blade.holder().hasUploadedModel())return true;
        return false;
    }
    @Override public void whenRendering() {
        drawDirty=false;
        TurnoutBinding current=binding;if(level==null||current==null)return;
        GeometryKey key=new GeometryKey(current,getRailModelId());TurnoutMesh built=mesh;
        boolean needsBuild=built==null||!key.equals(builtKey)||!built.sampled();
        if(needsBuild&&!buildingGeometry&&level.getGameTime()>=nextRetryTick)requestGeometry(key);
        if(awaitingUpload) {
            if(staticHolder!=null&&!staticHolder.hasUploadedModel())return;
            for(UploadedBlade blade:blades)if(!blade.holder().hasUploadedModel())return;
            awaitingUpload=false;
        }
        Matrices pose=TurnoutRenderHelper.turnoutPose(this,current);
        if(staticHolder!=null)scriptContext.drawModel(staticHolder,pose);
        long motionTime=switchChangedAt;int fromSignal=previousSignal,toSignal=signal;
        float progress=getSwitchProgress(TurnoutRenderHelper.partialTick());
        double eased=TurnoutGeometry.smoothstep(progress);
        for(UploadedBlade uploaded:blades) {
            pose.pushPose();TurnoutMesh.BladePart part=uploaded.part();TurnoutMesh.BladeMotion motion=part.motion();
            pose.translate(motion.pivotX(),motion.pivotY(),motion.pivotZ());pose.rotateY((float)part.angle(fromSignal,toSignal,eased));pose.translate(-motion.pivotX(),-motion.pivotY(),-motion.pivotZ());
            scriptContext.drawModel(uploaded.holder(),pose);pose.popPose();
        }
        if(progress>=1&&motionTime==switchChangedAt&&fromSignal==previousSignal&&toSignal==signal)drawnSwitchTime=motionTime;
        if(soundedSignal!=signal){soundedSignal=signal;var sound=TurnoutRenderHelper.switchSoundOrNull();if(sound!=null)scriptContext.playSound(sound,0.6F,1);}
    }
    private synchronized void invalidateGeometry() {
        geometryVersion++;mesh=null;builtKey=null;retries=0;nextRetryTick=0;drawDirty=true;warnedGeometryFailure=false;
        if(geometryTask!=null)com.fangsu.turnout.client.TurnoutGeometryJobs.cancel(geometryTask);
        geometryTask=null;buildingGeometry=false;
    }
    private synchronized void requestGeometry(GeometryKey key) {
        if(buildingGeometry||isRemoved()||level==null||!Objects.equals(key.binding(),binding)||!key.model().equals(getRailModelId()))return;
        long version=geometryVersion;boolean retry=mesh!=null&&!mesh.sampled();buildingGeometry=true;
        geometryTask=com.fangsu.turnout.client.TurnoutGeometryJobs.submit(()->{
            TurnoutMesh result=TurnoutMesh.EMPTY;
            try {
                if(Thread.currentThread().isInterrupted())return;
                if(retry)TurnoutGeometryBuilder.invalidate(key.binding(),key.model());
                result=TurnoutGeometryBuilder.build(level,key.binding(),key.model());
            }catch(java.util.concurrent.CancellationException cancelled){return;}
            catch(RuntimeException failure){if(!warnedGeometryFailure){warnedGeometryFailure=true;Main.LOGGER.warn("[turnout] 异步几何构建失败 pos={}",worldPosition,failure);}}
            finally {
                // 取消和异常也必须释放这一代任务，不能把控制器永久留在“正在构建”。
                TurnoutMesh ready=result;
                net.minecraft.client.Minecraft.getInstance().execute(()->finishGeometry(version,key,ready));
            }
        });
        if(geometryTask==null){buildingGeometry=false;nextRetryTick=level.getGameTime()+20;}
    }
    private synchronized void finishGeometry(long version,GeometryKey key,TurnoutMesh ready) {
        if(version!=geometryVersion||isRemoved())return;
        buildingGeometry=false;geometryTask=null;
        if(!Objects.equals(key.binding(),binding)||!key.model().equals(getRailModelId()))return;
        mesh=ready;builtKey=key;nextRetryTick=level.getGameTime()+Math.min(200,20+Math.min(36,retries++)*5);
        applyGeometry(key,ready);drawDirty=true;
    }
    private void releaseModels() {
        awaitingUpload=false;
        DynamicModelHolder holder=staticHolder;staticHolder=null;if(holder!=null)holder.close();
        List<UploadedBlade> old=blades;blades=List.of();for(UploadedBlade blade:old)blade.holder().close();
    }
    private void applyGeometry(GeometryKey key,TurnoutMesh built) {
        if(isRemoved()||!key.equals(builtKey)||!Objects.equals(key.binding(),binding)||!key.model().equals(getRailModelId())||built.totalFaces()==0)return;
        if(staticHolder==null)staticHolder=new DynamicModelHolder();if(built.hasStatic())staticHolder.uploadLater(built.staticParts());
        List<UploadedBlade> replacement=new ArrayList<>();
        for(TurnoutMesh.BladePart part:built.parts()){DynamicModelHolder holder=new DynamicModelHolder();holder.uploadLater(part.model());replacement.add(new UploadedBlade(holder,part));}
        List<UploadedBlade> old=blades;blades=List.copyOf(replacement);for(UploadedBlade blade:old)blade.holder().close();
        awaitingUpload=true;
    }
    @Override public void setRemoved(){invalidateGeometry();super.setRemoved();if(Main.isPhysicalClient())releaseModels();}
    @Override public String getMainModelKey(){return "turnout";}
    @Override public VoxelShape setCollisionShape(BlockState state){return Shapes.empty();}
    @Override public VoxelShape setShape(BlockState state){return Shapes.empty();}
}
