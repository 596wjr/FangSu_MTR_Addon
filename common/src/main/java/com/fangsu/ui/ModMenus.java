package com.fangsu.ui;

import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {
//    public static final RegistrySupplier<MenuType<TransformScreen.TransformMenu>> TRANSFORM_MENU = RegisterUtil.addMenu("transform_ui", () -> new MenuType<>(TransformScreen.TransformMenu::new, FeatureFlagSet.of()));

    public static void init() {
    }

    public static void initClient() {
//        MenuRegistry.registerScreenFactory(TRANSFORM_MENU.get(), TransformScreen::new);
    }
}
