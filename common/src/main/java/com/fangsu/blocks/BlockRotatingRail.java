package com.fangsu.blocks;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityRotatingRail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** 复用道岔的放置事务、工具选中、无碰撞和红石同步。 */
public class BlockRotatingRail extends BlockTurnout {
    @Override public BaseObjBlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlockEntityRotatingRail(pos,state);}
}
