package com.fangsu;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.render.ShadersModHandler;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcerext.reuse.AtlasManager;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.fangsu.drawing.sign.SignItemFactory;
import com.fangsu.drawing.diaoban.DiaobanDrawManager;
import com.fangsu.train.FunctionalCustomTrains;
import com.fangsu.ui.ModMenus;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

public class MainClient {
    public static DrawScheduler drawScheduler = new DrawScheduler();
    public static ModelManager modelManager = new ModelManager();
    public static AtlasManager atlasManager = new AtlasManager();

    public static boolean is_nte_loaded = false;

    public static DrawContext drawContext = new DrawContext();

    public static List<Runnable> resourceInitRunnables = new ArrayList<>();

    public static void initClient() {
        ModBlocks.initClient();
        ModMenus.initClient();
        ShadersModHandler.init();
        ScriptManager.getInstance().init();

        try {
            Class.forName("cn.zbx1425.mtrsteamloco.MainClient", false, MainClient.class.getClassLoader());
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("forge.cn.zbx1425.mtrsteamloco.MainClient", false, MainClient.class.getClassLoader());
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("fabric.cn.zbx1425.mtrsteamloco.MainClient", false, MainClient.class.getClassLoader());
            is_nte_loaded = true;
        } catch (ClassNotFoundException ignored) {
        }

        if (is_nte_loaded) Main.LOGGER.info("[FangSu] 正在渲染兼容模式下运行!");
    }

    public static void initResources(ResourceManager resourceManager) {
        ResourceUtil.init(resourceManager);
        CustomItems.getInstance().init();
        SignItemFactory.init();
        DiaobanDrawManager.preload();
        try {
            MainClient.drawScheduler.reloadShaders(resourceManager);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to reload FangSu shaders", e);
        }

        CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
        customMtrLifts.load();
        {
            JsonObject defaultLift = new JsonObject();
            defaultLift.addProperty("id", "default");
            defaultLift.addProperty("texture", "mtr:textures/entity/lift_1.png");
            defaultLift.addProperty("name", Component.translatable("mtr.fangsu.lift.vanilla").getString());
            defaultLift.addProperty("description", Component.translatable("mtr.fangsu.lift.vanilla.description").getString());
            JsonObject nonTransparent = new JsonObject();
            nonTransparent.addProperty("id", "non_transparent");
            nonTransparent.addProperty("texture", "fangsu:textures/entity/non_transparent.png");
            nonTransparent.addProperty("name", Component.translatable("fangsu:textures/entity/non_transparent.png").getString());
            nonTransparent.addProperty("description", Component.translatable("mtr.fangsu.lift.non_transparent.description").getString());
            customMtrLifts.injectBuiltInTexturedLifts(defaultLift);
            customMtrLifts.injectBuiltInTexturedLifts(nonTransparent);
        }
        FunctionalCustomTrains.init(resourceManager);

        for (Runnable runnable : resourceInitRunnables) {
            try {
                runnable.run();
            } catch (Exception e) {
                Main.LOGGER.error("failed to run resource runnable", e);
            }
        }
    }

    public static void addResourceRunnable(Runnable runnable) {
        resourceInitRunnables.add(runnable);
    }
}
