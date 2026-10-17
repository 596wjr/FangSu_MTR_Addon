package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.config.FangSuConfig;
import com.fangsu.creativeTabs.ModCreativeTabs;
import com.fangsu.events.ModEvents;
import com.fangsu.items.ModItems;
import com.fangsu.modular.TrafficLightProgramBlocks;
import com.fangsu.network.ModNetwork;
import com.fangsu.ui.ModMenus;
import com.fangsu.utils.RegisterUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    public static final String MOD_ID = "fangsu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final JsonParser JSON_PARSER = new JsonParser();
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static Boolean isClient = null;
    public static boolean debug = false;

    public static void init() {
        // Write common init code here.
        isClient = Platform.getEnv().name().equals("CLIENT");

        // 读取 config/fangsu.properties（缺失时自动写出默认值）
        FangSuConfig.init();

        //#if MC_VERSION >= 12000
        // 游戏版本>=1.20.0
        //$$ LOGGER.info("FangSu is running on Minecraft >= 1.20!");
        //#elseif MC_VERSION >= 11900
        //$$ LOGGER.info("FangSu is running on Minecraft between 1.19 and 1.20!");
        //#else
        //$$ LOGGER.info("FangSu is running on Minecraft under 1.19!");
        //#endif

        ModBlocks.init();
        ModItems.init();
        TrafficLightProgramBlocks.init();
        ModCreativeTabs.init();
        RegisterUtil.register();
        ModMenus.init();
        ModNetwork.init();
        ModEvents.init();
    }

    /**
     * 当前是否运行在物理客户端（专用服务端返回 false）。
     * <p>
     * 专用服务端的 classpath 里根本没有客户端渲染类（{@code com.mojang.blaze3d.*}、
     * {@code net.minecraft.client.*}，Fabric 运行时为 {@code net.minecraft.class_xxxx}）。
     * 任何从服务端也可能走到的路径（最典型的是方块实体销毁时的渲染资源释放）一旦触碰它们，
     * 就会抛 {@link NoClassDefFoundError} 并打挂服务端 tick 循环，因此必须先做本判定。
     * <p>
     * {@link #isClient} 在 {@link #init()} 中赋值；赋值前（正常流程下不会发生）按客户端处理，
     * 保持与旧行为一致。
     */
    public static boolean isPhysicalClient() {
        return !Boolean.FALSE.equals(isClient);
    }

    public static void debug(String msg) {
        if (debug)
            LOGGER.info("[DEBUG] " + msg);

    }

    public static void debug(String msg, Object... args) {
        if (debug)
            LOGGER.info("[DEBUG] " + msg, args);

    }
}
