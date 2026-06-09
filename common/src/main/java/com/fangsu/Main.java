package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.creativeTabs.ModCreativeTabs;
import com.fangsu.events.ModEvents;
import com.fangsu.items.ModItems;
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
        ModCreativeTabs.init();
        RegisterUtil.register();
        ModMenus.init();
        ModNetwork.init();
        ModEvents.init();
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
