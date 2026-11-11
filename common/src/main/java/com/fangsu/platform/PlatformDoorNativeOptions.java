package com.fangsu.platform;

import com.fangsu.blockEntities.*;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ThemedConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.function.Consumer;

/** 直接读取真实方块的 getConfigs，编辑的是无世界、无网络上报的草稿实体。 */
public final class PlatformDoorNativeOptions {
    public static void open(Screen parent,PlatformDoorSettings.Part part,Consumer<Map<String,String>> saved){
        FunctionalObjBlockEntity entity=switch(part.kind()){
            case "door"->new BlockEntityScreendoor(BlockPos.ZERO,ModBlocks.BLOCK_SCREENDOOR.get().defaultBlockState()){
                {((BaseObjBlockEntity)this).mainModel=part.model();subModels.put("subModel",part.variant());extraConfigs.putAll(part.options());whenLoading();}
                @Override public void sendUpdateC2S(){ }
            };
            case "glass"->new BlockEntityScreendoorGlass(BlockPos.ZERO,ModBlocks.BLOCK_SCREENDOOR_GLASS.get().defaultBlockState()){
                {((BaseObjBlockEntity)this).mainModel=part.model();subModels.put("subModelLeft",part.variant());subModels.put("subModelRight",part.secondaryVariant());extraConfigs.putAll(part.options());whenLoading();}
                @Override public void sendUpdateC2S(){ }
            };
            default->new BlockEntityDiaoban(BlockPos.ZERO,ModBlocks.BLOCK_DIAOBAN.get().defaultBlockState()){
                {((BaseObjBlockEntity)this).mainModel=part.model();subModels.put("subModel",part.variant());extraConfigs.putAll(part.options());extraConfigs.putIfAbsent("length",Integer.toString(Math.max(2,(int)Math.round(part.width()/unit(part)))));whenLoading();}
                @Override public void sendUpdateC2S(){ }
            };
        };
        var configs=entity.getConfigs();
        Minecraft.getInstance().setScreen(new ThemedConfigScreen(ComponentHelper.translatable("ui.fangsu.curve.door_native_options")){
            @Override protected void buildFixedWidgets(){int x=getPanelLeft()+6,w=getPanelRight()-x-6;closeButton=primary(addFixedWidget(themedButton(x,height-35,w,18,ComponentHelper.translatable("ui.fangsu.block.close_and_save"),b->onClose())));}
            @Override protected void buildScrollableContent(ContentLayout layout){
                int y=layout.y;for(var config:configs){
                    config.setChangeListener(entry->{if(entry.isSaveOnChange()){entry.save(null);requestRebuild();}});if(!config.isVisible())continue;
                    var widget=config.createWidget(layout.areaLeft,y,layout.labelWidth,layout.fieldWidth).setLabelColor(0xE1E6EC);addRenderableWidget(widget);addEntry(widget,y);y+=widget.getHeight()+5;
                }
            }
            @Override public boolean isPauseScreen(){return false;}
            @Override public void onClose(){
                for(var config:configs)config.save(null);
                var tag=entity.saveWithFullMetadata().getCompound("extraConfig");Map<String,String> options=new LinkedHashMap<>();for(String key:tag.getAllKeys())options.put(key,tag.getString(key));
                saved.accept(PlatformDoorSettings.checkOptions(options));entity.setRemoved();Minecraft.getInstance().setScreen(parent);
            }
        });
    }
    public static double unit(PlatformDoorSettings.Part part){var content=com.fangsu.utils.ContentInfoUtil.getDiaobanContent(part.model(),part.variant());return content==null?.5:Math.max(1,content.getUnit())/16d;}
    private PlatformDoorNativeOptions(){ }
}
