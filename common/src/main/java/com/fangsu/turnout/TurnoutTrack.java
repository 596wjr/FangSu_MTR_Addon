package com.fangsu.turnout;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;

/** 一条完整轨道链（含首尾和中间 MTR 节点），不包含程序生成的虚构轨道。 */
public record TurnoutTrack(List<BlockPos> nodes) {
    public TurnoutTrack { nodes=List.copyOf(nodes);if(nodes.size()<2||nodes.size()>65)throw new IllegalArgumentException("轨道路径长度不合法"); }
    public BlockPos start(){return nodes.get(0);}
    public BlockPos end(){return nodes.get(nodes.size()-1);}
    public CompoundTag toTag(){CompoundTag tag=new CompoundTag();tag.putLongArray("nodes",nodes.stream().mapToLong(BlockPos::asLong).toArray());return tag;}
    public static TurnoutTrack fromTag(CompoundTag tag){long[] data=tag.getLongArray("nodes");if(data.length<2||data.length>65)throw new IllegalArgumentException("轨道路径长度不合法");List<BlockPos> nodes=new ArrayList<>();for(long value:data)nodes.add(BlockPos.of(value));return new TurnoutTrack(nodes);}
}
