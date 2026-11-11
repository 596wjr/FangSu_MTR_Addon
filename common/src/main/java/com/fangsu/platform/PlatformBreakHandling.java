package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.items.ModItems;
import com.fangsu.mappings.ComponentHelper;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** 曲线站台的扳手编辑和整组拆除入口。 */
public final class PlatformBreakHandling {
    private static long lastRequest,lastHint;
    public static void init(){
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player,hand,pos,face)->{
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator()||!player.getItemInHand(hand).is(ModItems.ITEM_WRENCH.get()))return EventResult.pass();
            var level=player.getCommandSenderWorld();
            var owner=PlatformOverhangs.owner(level,pos);
            if(owner!=null)pos=owner;
            if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&cell.group!=null&&cell.geometry!=null){
                if(level.isClientSide)CurvedPlatformClient.openWithWrench(cell);
                return EventResult.interruptTrue();
            }
            if(level.isClientSide){
                var cell=PlatformOverlayClient.at(pos);
                if(cell!=null&&cell.group!=null&&!PlatformOverlayClient.covered(pos)){
                    CurvedPlatformClient.openWithWrench(cell);
                    return EventResult.interruptTrue();
                }
            }else if(level instanceof net.minecraft.server.level.ServerLevel server){
                var entry=PlatformOverlays.get(server).entry(pos);
                // 覆盖在站台上的屏蔽门等方块仍使用它们原有的扳手操作。
                if(entry!=null&&!entry.covered()&&entry.matches(level.getBlockState(pos)))return EventResult.interruptTrue();
            }
            return EventResult.pass();
        });
        InteractionEvent.LEFT_CLICK_BLOCK.register((player,hand,pos,face)->{
        var level=player.getCommandSenderWorld();var owner=PlatformOverhangs.owner(level,pos);if(owner!=null)pos=owner;boolean host=level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform;boolean covered=false,overlay=false;
        if(level.isClientSide){overlay=PlatformOverlayClient.at(pos)!=null;covered=PlatformOverlayClient.covered(pos);}
        else if(level instanceof net.minecraft.server.level.ServerLevel server){var entry=PlatformOverlays.get(server).entry(pos);overlay=entry!=null&&entry.matches(level.getBlockState(pos));covered=overlay&&entry.covered();}
        if(!host&&!overlay)return EventResult.pass();
        if(player.isShiftKeyDown()){
            if(level.isClientSide){long now=System.currentTimeMillis();if(now-lastRequest>350){lastRequest=now;CurvedPlatformPackets.breakAt(pos);}}
            else if(player instanceof ServerPlayer actor)CurvedPlatformPackets.requestBreak(actor,pos);
            return EventResult.interruptFalse();
        }
        // 门本体仍可普通拆除，之后由覆盖状态事件恢复顶皮。
        if(covered)return EventResult.pass();
        if(level.isClientSide){long now=System.currentTimeMillis();if(now-lastHint>1500){lastHint=now;player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.curve.shift_break"),true);}}
        return EventResult.interruptFalse();
    });}
    private PlatformBreakHandling(){ }
}
