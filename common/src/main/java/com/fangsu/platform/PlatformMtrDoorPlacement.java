package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;

/** MTR 多格 PSD/APG 物品绕过 BlockItem，需要单独接入顶皮覆盖。 */
public final class PlatformMtrDoorPlacement {
    private static boolean door(UseOnContext context){return context.getItemInHand().getItem() instanceof mtr.item.ItemPSDAPGBase;}
    public static UseOnContext adjust(UseOnContext context){
        if(!door(context)||context.getPlayer()==null||context.getPlayer().isShiftKeyDown()||context.getClickedFace()!=Direction.UP)return context;
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell)||!cell.floorSkin()||cell.outline().max(Direction.Axis.Y)>2.44150625E-4)return context;
        // MTR 随后自己做 clickedPos.offset(side)，所以改成点击顶皮下的支承格。
        return new UseOnContext(context.getPlayer(),context.getHand(),new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false));
    }
    /** null 表示未涉及站台顶皮，完全交回 MTR 原来的检查。 */
    public static Boolean blocked(UseOnContext context,int width,int height,Block blacklist,boolean replaceable){
        if(!door(context))return null;
        var level=context.getLevel();var start=context.getClickedPos().relative(context.getClickedFace());var facing=context.getHorizontalDirection();boolean touched=false,blocked=false;
        for(int x=0;x<width;x++){
            var column=start.relative(facing.getClockWise(),x);
            if(blacklist!=null&&(level.getBlockState(column.below()).is(blacklist)||level.getBlockState(column.above(height)).is(blacklist)))blocked=true;
            for(int y=0;y<height;y++){
                BlockPos pos=column.above(y);var state=level.getBlockState(pos);
                if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&cell.coverable()){
                    touched=true;if(level instanceof ServerLevel server&&(PlatformLayouts.get(server).deleting(cell.group)||CurvedPlatformPackets.busy(server,cell.group)||context.getPlayer()==null||!server.mayInteract(context.getPlayer(),pos)))blocked=true;
                }else{
                    boolean canReplace=state.isAir();
                    if(replaceable){
                        //#if MC_VERSION >= 11904
                        canReplace=state.canBeReplaced();
                        //#else
                        //$$ canReplace=state.getMaterial().isReplaceable();
                        //#endif
                    }
                    if(!canReplace)blocked=true;
                }
            }
        }
        return touched?blocked:null;
    }
    private PlatformMtrDoorPlacement(){ }
}
