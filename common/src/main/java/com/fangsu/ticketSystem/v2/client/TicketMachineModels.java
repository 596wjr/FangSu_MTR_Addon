package com.fangsu.ticketSystem.v2.client;

import com.fangsu.customItem.*;
import com.fangsu.ticketSystem.v2.TicketMachineConfig;
import com.fangsu.ticketSystem.v2.TicketDefinitions;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** 与闸机共用 custom_blocks.json 目录和 ModelSelectScreen，模型描述只包含外观。 */
public final class TicketMachineModels {
    // 对齐经典款贴图顶部灯窗，方块和物品预览使用同一组尺寸。
    static final double CLASSIC_LED_Y = 1.8603, CLASSIC_LED_Z = -.065;
    static final float CLASSIC_LED_WIDTH = .895f, CLASSIC_LED_HEIGHT = .117f;
    static final int CLASSIC_LED_PIXELS_X = 320, CLASSIC_LED_PIXELS_Y = 42;
    public static List<ModelSelectInfo> options(){return CustomItems.items.getOrDefault("ticketMachine",List.of());}
    public static String selected(TicketMachineConfig config){
        if(!config.modelPreset.isEmpty())return config.modelPreset;
        return config.model.endsWith("ticket_machine_old.bbmodel")?"fangsu:ticketing/machine_old.json"
                :config.model.endsWith("card_machine.bbmodel")?"fangsu:ticketing/card_machine.json":"fangsu:ticketing/machine_new.json";
    }
    public static void apply(TicketMachineConfig target,String resource){
        var json=ResourceUtil.loadAsJSON(new ResourceLocation(resource));
        TicketMachineConfig appearance=TicketDefinitions.JSON.fromJson(json,TicketMachineConfig.class);
        if(appearance==null)throw new IllegalArgumentException("模型描述不可用");
        target.modelPreset=resource;target.model=appearance.model;target.shape=appearance.shape;target.flipV=appearance.flipV;target.modelOffsetZ=appearance.modelOffsetZ;
        target.screenX=appearance.screenX;target.screenY=appearance.screenY;target.screenZ=appearance.screenZ;
        target.screenWidth=appearance.screenWidth;target.screenHeight=appearance.screenHeight;target.screenPitch=appearance.screenPitch;
        target.screenPixelsX=appearance.screenPixelsX;target.screenPixelsY=appearance.screenPixelsY;
    }
    static ResourceLocation screenTexture(TicketMachineConfig config) {
        if (config.modelPreset.isEmpty()) return null;
        var preset = ResourceUtil.loadAsJSON(new ResourceLocation(config.modelPreset));
        if (!preset.isJsonObject()) return null;
        var texture = preset.getAsJsonObject().get("screenTexture");
        if (texture == null || !texture.isJsonPrimitive() || !texture.getAsJsonPrimitive().isString()) return null;
        String path = texture.getAsString();
        return path.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png") ? new ResourceLocation(path) : null;
    }
    private TicketMachineModels(){ }
}
