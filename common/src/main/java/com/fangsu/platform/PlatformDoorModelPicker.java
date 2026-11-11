package com.fangsu.platform;

import com.fangsu.customItem.BasicModelSelectInfo;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.contents.ContentManager;
import com.fangsu.customItem.contents.ScreendoorGlassContent;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ModelSelectScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;

/** 重复单元直接使用原方速模型选择界面；只修改草稿，不构造世界方块实体。 */
public final class PlatformDoorModelPicker {
    public static void openMain(Screen parent,String kind,String current,Consumer<String> selected){
        String type=kind.equals("board")?"diaoban":"screendoor";
        var options=new ArrayList<ModelSelectInfo>(CustomItems.items.getOrDefault(type,List.of()));
        // 保留资源包中已加载但未列入模型目录的资源，原有目录项仍携带名称和详情。
        var loaded=ContentManager.getInstance().getContent(type);
        if(loaded!=null)loaded.keySet().stream().sorted().forEach(id->fallback(options,id));
        fallback(options,current);
        open(parent,"door_part_model",current,options,selected);
    }
    public static void openVariant(Screen parent,String kind,String model,String current,boolean right,Consumer<String> selected){openVariant(parent,kind,model,current,right,right?1:0,selected);}
    public static void openVariant(Screen parent,String kind,String model,String current,boolean right,int doorSide,Consumer<String> selected){
        List<ModelSelectInfo> supplied;
        if(kind.equals("glass"))supplied=right?ScreendoorGlassContent.loadRightModelSelectInfos(model):ScreendoorGlassContent.loadLeftModelSelectInfos(model);
        else supplied=CustomItems.getModelSelectInfos(model,kind.equals("board")?"content":doorSide==2?"door.flex":doorSide==1?"door.right":"door.left");
        var options=new ArrayList<ModelSelectInfo>();
        if(supplied!=null)for(var info:supplied)if(!kind.equals("glass")||!info.getContent().equals("auto"))options.add(info);
        fallback(options,current);
        open(parent,kind.equals("glass")?(right?"door_right_variant":"door_left_variant"):"door_part_variant",current,options,selected);
    }
    public static Component mainName(String kind,String current){return name(CustomItems.items.getOrDefault(kind.equals("board")?"diaoban":"screendoor",List.of()),current);}
    public static Component variantName(String kind,String model,String current,boolean right,int doorSide){
        List<? extends ModelSelectInfo> list=kind.equals("glass")?(right?ScreendoorGlassContent.loadRightModelSelectInfos(model):ScreendoorGlassContent.loadLeftModelSelectInfos(model)):CustomItems.getModelSelectInfos(model,kind.equals("board")?"content":doorSide==2?"door.flex":doorSide==1?"door.right":"door.left");
        return name(list,current);
    }
    public static Component name(List<? extends ModelSelectInfo> options,String current){if(options!=null)for(var info:options)if(Objects.equals(info.getContent(),current))return ComponentHelper.translatable(info.getText());int slash=current.lastIndexOf('/');return ComponentHelper.literal(slash<0?current:current.substring(slash+1));}
    private static void fallback(List<ModelSelectInfo> options,String id){
        if(id!=null&&!id.isBlank()&&options.stream().noneMatch(info->Objects.equals(info.getContent(),id)))options.add(new BasicModelSelectInfo(id,id));
    }
    private static void open(Screen parent,String title,String current,List<ModelSelectInfo> options,Consumer<String> selected){
        Minecraft.getInstance().setScreen(new ModelSelectScreen(ComponentHelper.translatable("ui.fangsu.curve."+title),null,options,ignored->current,(ignored,value)->selected.accept(value),parent){@Override public boolean isPauseScreen(){return false;}});
    }
    private PlatformDoorModelPicker(){ }
}
