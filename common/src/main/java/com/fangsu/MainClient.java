package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.customItem.CustomItems;
import com.fangsu.render.ShadersModHandler;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcerext.reuse.AtlasManager;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.fangsu.signItems.SignItemFactory;
import com.fangsu.ui.ModMenus;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.server.packs.resources.ResourceManager;

public class MainClient {
    public static DrawScheduler drawScheduler = new DrawScheduler();
    public static ModelManager modelManager = new ModelManager();
    public static AtlasManager atlasManager = new AtlasManager();

    public static boolean is_nte_loaded = true;

    public static DrawContext drawContext = new DrawContext();

    public static void initClient() {
        ModBlocks.initClient();
        ModMenus.initClient();
        ShadersModHandler.init();

        try {
            Class.forName("cn.zbx1425.mtrsteamloco.MainClient");
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("forge.cn.zbx1425.mtrsteamloco.MainClient");
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("fabric.cn.zbx1425.mtrsteamloco.MainClient");
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static void initResources(ResourceManager resourceManager) {
        ResourceUtil.init(resourceManager);
        CustomItems.init();
        SignItemFactory.init();
        try {
            MainClient.drawScheduler.reloadShaders(resourceManager);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to reload FangSu shaders", e);
        }
    }
}
