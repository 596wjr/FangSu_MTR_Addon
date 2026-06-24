package com.fangsu;

import com.fangsu.mappings.ComponentHelper;
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
import com.fangsu.drawing.pids.PidsDrawManager;
import com.fangsu.drawing.ris.RisDrawManager;
import com.fangsu.drawing.sis.SisDrawManager;
import com.fangsu.train.FunctionalCustomTrains;
import com.fangsu.train.LcdManager;
import com.fangsu.train.lcds.MtrLcd;
import com.fangsu.ui.ModMenus;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
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

        if (is_nte_loaded) Main.LOGGER.info("[FangSu] running with NTE, using compatible rendering");
    }

    public static void initResources(ResourceManager resourceManager) {
        Main.LOGGER.info("[FangSu] initResources called, resourceManager={}", resourceManager);

        if (resourceManager == null) {
            Main.LOGGER.error("[FangSu] initResources: resourceManager is null! Skipping initialization.");
            return;
        }

        try {

            ResourceUtil.init(resourceManager);
            Main.LOGGER.info("[FangSu] ResourceUtil initialized, starting CustomItems.init...");

            try {
                CustomItems.getInstance().init();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] CustomItems.init failed", e);
            }
            Main.LOGGER.info("[FangSu] CustomItems.init completed, starting SignItemFactory.init...");

            try {
                SignItemFactory.init();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] SignItemFactory.init failed", e);
            }
            Main.LOGGER.info("[FangSu] SignItemFactory.init completed");
            try {
                DiaobanDrawManager.preload();
                PidsDrawManager.preload();
                RisDrawManager.preload();
                SisDrawManager.preload();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] DrawManager preload failed", e);
            }
            try {
                MainClient.drawScheduler.reloadShaders(resourceManager);
            } catch (Exception e) {
                Main.LOGGER.error("Failed to reload FangSu shaders", e);
            }

            try {
                CustomMtrLifts customMtrLifts = CustomMtrLifts.getInstance();
                customMtrLifts.load();
                {
                    JsonObject defaultLift = new JsonObject();
                    defaultLift.addProperty("id", "default");
                    defaultLift.addProperty("texture", "mtr:textures/entity/lift_1.png");
                    defaultLift.addProperty("name", ComponentHelper.translatable("mtr.fangsu.lift.vanilla").getString());
                    defaultLift.addProperty("description", ComponentHelper.translatable("mtr.fangsu.lift.vanilla.description").getString());
                    JsonObject nonTransparent = new JsonObject();
                    nonTransparent.addProperty("id", "non_transparent");
                    nonTransparent.addProperty("texture", "fangsu:textures/entity/non_transparent.png");
                    nonTransparent.addProperty("name", ComponentHelper.translatable("fangsu:textures/entity/non_transparent.png").getString());
                    nonTransparent.addProperty("description", ComponentHelper.translatable("mtr.fangsu.lift.non_transparent.description").getString());
                    customMtrLifts.injectBuiltInTexturedLifts(defaultLift);
                    customMtrLifts.injectBuiltInTexturedLifts(nonTransparent);
                }
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] CustomMtrLifts init failed", e);
            }
            try {
                LcdManager.getInstance().injectLcd("mtr", MtrLcd::new);
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] LcdManager init failed", e);
            }

            try {
                FunctionalCustomTrains.init(resourceManager);
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] FunctionalCustomTrains.init failed", e);
            }

            for (Runnable runnable : resourceInitRunnables) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    Main.LOGGER.error("failed to run resource runnable", e);
                }
            }

            Main.LOGGER.info("[FangSu] initResources completed successfully");
        } catch (Exception e) {
            Main.LOGGER.error("[FangSu] initResources failed with unexpected exception", e);
        }
    }

    public static void addResourceRunnable(Runnable runnable) {
        resourceInitRunnables.add(runnable);
    }
}
