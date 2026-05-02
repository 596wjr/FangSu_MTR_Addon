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

    public static void init() {
        // Write common init code here.
        isClient = Platform.getEnv().name().equals("CLIENT");

        //#if FABRIC
        //$$ LOGGER.info("Fangsu is running on FABRIC");
        //#elseif FORGE
        //$$ LOGGER.info("Fangsu is running on FORGE");
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

        LOGGER.info("[DEBUG] " + msg);

    }

    public static void debug(String msg, Object... args) {

        LOGGER.info("[DEBUG] " + msg, args);

    }
}
