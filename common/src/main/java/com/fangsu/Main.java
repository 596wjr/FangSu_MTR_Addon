package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.customItem.CustomItems;
import com.fangsu.items.ModItems;
import com.fangsu.network.ModNetwork;
import com.fangsu.signItems.SignItemFactory;
import com.fangsu.ui.ModMenus;
import com.fangsu.utils.RegisterUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    public static final String MOD_ID = "fangsu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final JsonParser JSON_PARSER = new JsonParser();
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
        RegisterUtil.register();
        ModMenus.init();
        ModNetwork.init();
    }

    public static void initClient() {
        ModBlocks.initClient();
        ModMenus.initClient();
    }

    public static void initResources(ResourceManager resourceManager) {
        ResourceUtil.init(resourceManager);
        CustomItems.init();
        SignItemFactory.init();
    }
}
