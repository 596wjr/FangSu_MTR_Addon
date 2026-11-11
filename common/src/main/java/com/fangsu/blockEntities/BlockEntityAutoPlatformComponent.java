package com.fangsu.blockEntities;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.platform.PlatformDoorComponent;
import com.fangsu.platform.PlatformDoorShape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
    private volatile boolean target;private boolean previousTarget;
    private float requested,previousProgress;private volatile float progress;
    private Runnable clientDisposer;
    // Indigo 等区块构建线程也会查询形状。更新时发布新快照，不清空正在被读取的缓存。
    private volatile ShapeCache shapes=new ShapeCache(components);
    private static final class ShapeCache {
        private final List<PlatformDoorComponent> components;
        private final VoxelShape[] phases=new VoxelShape[33];
        ShapeCache(List<PlatformDoorComponent> values){components=List.copyOf(values);}
        synchronized VoxelShape get(int phase){
            VoxelShape result=phases[phase];
            if(result==null){result=PlatformDoorShape.create(components,phase/32f);phases[phase]=result;}
            return result;
        }
    }
    public BlockEntityAutoPlatformComponent(BlockPos pos,BlockState state){super(ModBlocks.BLOCK_ENTITY_AUTO_PLATFORM_COMPONENT.get(),pos,state);}
    public List<PlatformDoorComponent> components(){return components;}
    public CompoundTag metadata(){return metadata.copy();}
    public void metadata(CompoundTag value){var next=value==null?new CompoundTag():value.copy();if(!next.equals(metadata)){metadata=next;revision++;}changed();}
    public long revision(){return revision;}
    public void configure(UUID group,List<PlatformDoorComponent> values){
        if(values==null||values.size()>16)throw new IllegalArgumentException("component_limit");var next=List.copyOf(values);
        this.group=group;if(!next.equals(components)){components=next;shapes=new ShapeCache(next);revision++;}changed();
    }
    public void installClientDisposer(Runnable disposer){clientDisposer=disposer;}
    public VoxelShape shape(){int phase=Math.max(0,Math.min(32,Math.round(progress*32)));return shapes.get(phase);}
    public float interpolatedProgress(float partial){return previousProgress+(progress-previousProgress)*Math.max(0,Math.min(1,partial));}
    @Override public boolean getDoorTarget(){return target;}
    @Override public void setDoorTarget(boolean value){target=value;signal();}
    @Override public float getDoorValue(){return progress;}
    @Override public void setDoorValue(float value){if(Float.isFinite(value)){requested=Math.max(0,Math.min(1,value));signal();}}
    private void signal(){if(level!=null)lastSignal=level.getGameTime();}
    public void tick(){
        previousProgress=progress;
        if(level==null||level.isClientSide)return;
        if(level instanceof net.minecraft.server.level.ServerLevel server)com.fangsu.platform.PlatformDoorTrainDriver.observe(server,worldPosition);
        if(level.getGameTime()-lastSignal>20){target=false;requested=0;}
        boolean open=target||requested>=.4f;
        progress=open?Math.min(1,progress+.03f):Math.max(0,progress-.03f);
        if(progress!=previousProgress||previousTarget!=target){previousTarget=target;changed();}
    }
    private void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);tag.putInt("version",1);if(group!=null)tag.putUUID("group",group);
        ListTag entries=new ListTag();for(var component:components)entries.add(component.save());tag.put("components",entries);tag.put("metadata",metadata.copy());tag.putBoolean("doorTarget",target);tag.putFloat("doorValue",progress);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);group=tag.hasUUID("group")?tag.getUUID("group"):null;
        if(tag.contains("components",Tag.TAG_LIST)){
            var entries=tag.getList("components",Tag.TAG_COMPOUND);List<PlatformDoorComponent> next=new ArrayList<>();
            if(entries.size()<=16)for(int i=0;i<entries.size();i++)try{next.add(PlatformDoorComponent.load(entries.getCompound(i)));}catch(IllegalArgumentException ignored){ }
            if(!next.equals(components)){var snapshot=List.copyOf(next);components=snapshot;shapes=new ShapeCache(snapshot);revision++;}
        }
        var nextMetadata=tag.getCompound("metadata").copy();if(!metadata.equals(nextMetadata)){metadata=nextMetadata;revision++;}
        target=tag.getBoolean("doorTarget");previousTarget=target;previousProgress=progress;float value=tag.getFloat("doorValue");progress=Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;
    }
    public CompoundTag serverData(){CompoundTag tag=new CompoundTag();saveAdditional(tag);return tag;}
    @Override public CompoundTag getUpdateTag(){return serverData();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void setRemoved(){if(clientDisposer!=null){clientDisposer.run();clientDisposer=null;}super.setRemoved();}
}
