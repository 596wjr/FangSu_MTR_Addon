package com.fangsu;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.CustomMtrLifts;
import com.fangsu.customItem.NteRailManager;
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

import java.util.List;

@Environment(EnvType.CLIENT)
public class MainClient {
    public static DrawScheduler drawScheduler = new DrawScheduler();
    public static ModelManager modelManager = new ModelManager();
    public static AtlasManager atlasManager = new AtlasManager();

    public static boolean is_nte_loaded = false;

    public static DrawContext drawContext = new DrawContext();

    // 真正的登记表放在两侧都能安全加载的 ClientBootstrap 里：扩展模组在 init() 阶段（服务端也会执行）
    // 直接引用本类会被 Fabric 拒绝（本类带 @Environment(EnvType.CLIENT)），从而打挂服务端启动。
    public static List<Runnable> resourceInitRunnables = com.fangsu.client.ClientBootstrap.resourceInitRunnables();

    public static void initClient() {
        ModBlocks.initClient();
        ModMenus.initClient();
        ShadersModHandler.init();
        ScriptManager.getInstance().init();
        com.fangsu.network.HybridCreatorClient.init();
        // 道岔 S2C（绑定结果）接收器
        com.fangsu.network.ModNetwork.initClient();
        com.fangsu.train.DoorOpenCacheClient.initClient();
        // 平交路口的网格构建队列（客户端 tick 驱动，与渲染回调解耦）
        com.fangsu.blockEntities.BlockEntityLevelCrossing.initBuildQueue();
        com.fangsu.train.DoorSideObserverMtr3.init();

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

        // P5-4（路径 B）：把「NTE 在不在」交给外轨超高的 NTE 侧实现，让它的一次性诊断能把
        // 「没装 NTE（走路径 A）」与「装了 NTE 但路径 B 的钩子没挂上（轨面平）」区分开。
        // NteRailTiltHelper 是普通 common 类，不引用任何 NTE 类型（NTE 不是构建依赖）。
        com.fangsu.mtr.rail.NteRailTiltHelper.reportNtePresence(is_nte_loaded);
    }

    public static void initResources(ResourceManager resourceManager) {
        Main.LOGGER.info("[FangSu] initResources called, resourceManager={}", resourceManager);

        if (resourceManager == null) {
            Main.LOGGER.error("[FangSu] initResources: resourceManager is null! Skipping initialization.");
            return;
        }

        try {

            ResourceUtil.init(resourceManager);
            // 必须先更换 ResourceUtil 缓存，再让已有 holder 重新读取 JS 和脚本内引用的资源。
            ScriptManager.getInstance().reloadAllScripts();
            com.fangsu.scripting.GifHelper.getInstance().disposeAll();
            Main.LOGGER.info("[FangSu] ResourceUtil initialized, starting CustomItems.init...");

            // 资源重载时释放旧的模型 / 图集缓存（对齐 ANTE 的 CustomResources 行为）。
            // 注意顺序：必须晚于 ResourceUtil.init，早于各内容注册重新加载。
            try {
                modelManager.clear();
                atlasManager.clear();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] modelManager/atlasManager clear failed", e);
            }

            try {
                CustomItems.getInstance().init();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] CustomItems.init failed", e);
            }
            try {
                NteRailManager.getInstance().load();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] NteRailManager.init failed", e);
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
                com.fangsu.render.lift.CustomLiftModel.clearCache();
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
                com.fangsu.train.connector.ConnectorRegistry.reload();
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

            try {
                SignItemFactory.loadMtrItems();
            } catch (Exception e) {
                Main.LOGGER.error("failed to load mtr sign items", e);
            }

            // 丢弃旧方块纹理，避免相同绘制 ID 重新绑定到上一轮的静态图或失败条目。
            com.fangsu.utils.GraphicsTextureHelper.getInstance().removeDrawGraphicsByPrefix("block_");
            // 注册全部完成再发布代际，已放置方块下次渲染时会重新加载模型、配置及绘制状态。
            ResourceUtil.completeReload();
            Main.LOGGER.info("[FangSu] initResources completed successfully");
        } catch (Exception e) {
            Main.LOGGER.error("[FangSu] initResources failed with unexpected exception", e);
        }
    }

    public static void addResourceRunnable(Runnable runnable) {
        com.fangsu.client.ClientBootstrap.addResourceRunnable(runnable);
    }
}
