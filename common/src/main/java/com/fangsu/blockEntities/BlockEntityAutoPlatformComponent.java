package com.fangsu.blockEntities;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.platform.PlatformDoorComponent;
import com.fangsu.platform.PlatformDoorLayouts;
import com.fangsu.platform.PlatformDoorShape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;

/** 门、固定玻璃和吊板共用一个实体；资源对象只存在客户端渲染委托中。 */
public final class BlockEntityAutoPlatformComponent extends BlockEntity implements IPlatformDoor {
    public UUID group;
    private volatile List<PlatformDoorComponent> components=List.of(new PlatformDoorComponent("glass",0,0,0,0,1,2,"fangsu:screendoor/kaba.json","glass"));
    private CompoundTag metadata=new CompoundTag();
    private long revision,lastSignal;
    private volatile boolean target,centralLocked;
    private boolean automaticTarget,opening;
    private float requested,previousProgress;private volatile float progress;
    private ClientRenderState clientRenderState;

    public interface ClientRenderState {
        void close();
    }
    // Indigo 等区块构建线程也会查询形状。更新时发布新快照，不清空正在被读取的缓存。
    private volatile ShapeCache shapes=new ShapeCache(components);
    private static final class ShapeCache {
        private final List<PlatformDoorComponent> components;
        private final VoxelShape[] phases=new VoxelShape[66];
        ShapeCache(List<PlatformDoorComponent> values){components=List.copyOf(values);}
        synchronized VoxelShape get(int phase,boolean centralLocked){
            int index=phase+(centralLocked?33:0);
            VoxelShape result=phases[index];
            if(result==null){result=PlatformDoorShape.create(components,phase/32f,centralLocked);phases[index]=result;}
            return result;
        }
    }
    public BlockEntityAutoPlatformComponent(BlockPos pos,BlockState state){super(ModBlocks.BLOCK_ENTITY_AUTO_PLATFORM_COMPONENT.get(),pos,state);}
    public List<PlatformDoorComponent> components(){return components;}
    public CompoundTag metadata(){return metadata.copy();}
    public static CompoundTag compactMetadata(CompoundTag source){
        var result=new CompoundTag();
        if(source!=null)for(String key:List.of("first","last","drawScript","routes")){
            var value=source.get(key);
            if(value!=null)result.put(key,value.copy());
        }
        return result;
    }
    public long revision(){return revision;}
    public void configure(UUID group,List<PlatformDoorComponent> values,CompoundTag metadata){
        if(values==null||values.size()>16)throw new IllegalArgumentException("component_limit");var next=List.copyOf(values);
        var nextMetadata=compactMetadata(metadata);
        if(Objects.equals(this.group,group)&&next.equals(components)&&nextMetadata.equals(this.metadata))return;
        this.group=group;
        if(!next.equals(components)){components=next;shapes=new ShapeCache(next);}
        this.metadata=nextMetadata;
        revision++;
        changed();
    }
    public ClientRenderState clientRenderState(){return clientRenderState;}
    public void setClientRenderState(ClientRenderState state){
        if(clientRenderState==state)return;
        var previous=clientRenderState;
        clientRenderState=state;
        if(previous!=null)previous.close();
    }
    public VoxelShape shape(){int phase=Math.max(0,Math.min(32,Math.round(progress*32)));return shapes.get(phase,centralLocked);}
    private long progressUpdateTick;
    public float interpolatedProgress(float partial){
        double elapsed=level==null?1:level.getGameTime()-progressUpdateTick+partial;
        return previousProgress+(progress-previousProgress)*(float)Math.max(0,Math.min(1,elapsed));
    }
    @Override public boolean getDoorTarget(){return target;}
    public boolean isCentralLocked(){return centralLocked;}
    public boolean getAutomaticDoorTarget(){return automaticTarget;}
    @Override public void setDoorTarget(boolean value){automaticTarget=value;signal();}
    @Override public float getDoorValue(){return progress;}
    @Override public void setDoorValue(float value){if(Float.isFinite(value)){requested=Math.max(0,Math.min(1,value));signal();}}
    private void signal(){if(level!=null)lastSignal=level.getGameTime();}
    public void tick(){
        if(level==null)return;
        if(level.isClientSide){
            if(progressUpdateTick==level.getGameTime())return;
            previousProgress=progress;
            progress=advance(progress,opening);
            progressUpdateTick=level.getGameTime();
            return;
        }
        previousProgress=progress;
        if(!(level instanceof ServerLevel server))return;
        com.fangsu.platform.PlatformDoorTrainDriver.observe(server,worldPosition);
        if(level.getGameTime()-lastSignal>20){automaticTarget=false;requested=0;}
        var central=group==null?null:PlatformDoorLayouts.get(server).centralControl(group);
        boolean locked=central!=null;
        boolean nextTarget=locked?central.open():automaticTarget;
        boolean open=nextTarget||(!locked&&requested>=.4f);
        progress=advance(progress,open);
        if(opening!=open||target!=nextTarget||centralLocked!=locked){
            opening=open;target=nextTarget;centralLocked=locked;changed();
        }
        else if(progress!=previousProgress&&(progress==0||progress==1))setChanged();
    }
    private static float advance(float value,boolean open){return open?Math.min(1,value+.03f):Math.max(0,value-.03f);}
    private void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);tag.putInt("version",1);if(group!=null)tag.putUUID("group",group);
        ListTag entries=new ListTag();for(var component:components)entries.add(component.save());tag.put("components",entries);tag.put("metadata",metadata.copy());tag.putBoolean("doorTarget",target);tag.putBoolean("doorOpening",opening);tag.putFloat("doorValue",progress);
        if(centralLocked)tag.putBoolean("centralLocked",true);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);UUID oldGroup=group;group=tag.hasUUID("group")?tag.getUUID("group"):null;
        if(tag.contains("components",Tag.TAG_LIST)){
            var entries=tag.getList("components",Tag.TAG_COMPOUND);List<PlatformDoorComponent> next=new ArrayList<>();
            if(entries.size()<=16)for(int i=0;i<entries.size();i++)try{next.add(PlatformDoorComponent.load(entries.getCompound(i)));}catch(IllegalArgumentException ignored){ }
            if(!next.equals(components)){var snapshot=List.copyOf(next);components=snapshot;shapes=new ShapeCache(snapshot);revision++;}
        }
        var nextMetadata=compactMetadata(tag.getCompound("metadata"));if(!metadata.equals(nextMetadata)){metadata=nextMetadata;revision++;}
        target=tag.getBoolean("doorTarget");
        centralLocked=tag.getBoolean("centralLocked");
        automaticTarget=!centralLocked&&target;
        opening=tag.contains("doorOpening",Tag.TAG_BYTE)?tag.getBoolean("doorOpening"):target;
        float value=tag.getFloat("doorValue"),next=Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;
        if(level==null||next==progress||!Objects.equals(oldGroup,group))previousProgress=next;
        else previousProgress=interpolatedProgress(0);
        if(level!=null)progressUpdateTick=level.getGameTime();
        progress=next;
    }
    public CompoundTag serverData(){CompoundTag tag=new CompoundTag();saveAdditional(tag);return tag;}
    @Override public CompoundTag getUpdateTag(){
        var tag=serverData();
        var visual=tag.getCompound("metadata");
        visual.remove("first");visual.remove("last");
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void setRemoved(){setClientRenderState(null);super.setRemoved();}
}
