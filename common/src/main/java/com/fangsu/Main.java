package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    public static final String MOD_ID = "fangsu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Boolean isClient = null;

    public static void init() {
        // Write common init code here.
        isClient = Platform.getEnv().name().equals("CLIENT");

        //#if FABRIC
        //$$ LOGGER.info("Fangsu is running on FABRIC");
        //#elseif FORGE
        LOGGER.info("Fangsu is running on FORGE");
        //#endif

        ModBlocks.init();
        RegisterUtil.register();
    }

    public static void initClient() {
        ModBlocks.initClient();
    }
}
