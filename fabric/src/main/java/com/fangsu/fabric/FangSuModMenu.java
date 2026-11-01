package com.fangsu.fabric;

import com.fangsu.ui.FangSuConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Fabric 模组菜单的可选配置入口。 */
@Environment(EnvType.CLIENT)
public final class FangSuModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FangSuConfigScreen::new;
    }
}
