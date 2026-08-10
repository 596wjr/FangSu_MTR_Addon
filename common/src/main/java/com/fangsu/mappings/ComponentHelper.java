package com.fangsu.mappings;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * LocalComponent 鍏煎宸ュ叿绫伙紝缁熶竴 1.18.2 锟?1.20.1 锟?LocalComponent.translatable API锟?
 * <p>
 * 1.19+ 寮€锟?LocalComponent 鎺ュ彛鏈夐潤鎬佹柟锟?translatable()锟?
 * 1.18.2 闇€锟?new TranslatableComponent()锟?
 */
public class ComponentHelper {

    public static MutableComponent translatable(String key) {
        //#if MC_VERSION >= 11900
        return Component.translatable(key);
        //#else
        //$$ return new net.minecraft.network.chat.TranslatableComponent(key);
        //#endif
    }

    public static MutableComponent translatable(String key, Object... args) {
        //#if MC_VERSION >= 11900
        return Component.translatable(key, args);
        //#else
        //$$ return new net.minecraft.network.chat.TranslatableComponent(key, args);
        //#endif
    }

    public static String translatableString(String key) {
        return translatable(key).getString();
    }

    /**
     * 等效于 LocalComponent.empty() — 1.18.2 不存在该静态方法
     */
    public static MutableComponent empty() {
        //#if MC_VERSION >= 11900
        return Component.empty();
        //#else
        //$$ return new net.minecraft.network.chat.TextComponent("");
        //#endif
    }

    /**
     * 等效于 LocalComponent.literal(str) — 1.18.2 不存在该静态方法
     */
    public static MutableComponent literal(String str) {
        //#if MC_VERSION >= 11900
        return Component.literal(str);
        //#else
        //$$ return new net.minecraft.network.chat.TextComponent(str);
        //#endif
    }

    /**
     * 跨版本创建按钮：1.19.3+ 用 Button.builder（新 API），
     * 1.18.2/1.19.2 无 builder 方法，用构造器（照 BasicConfigScreen.addButton 分界）。
     * 版本差异收敛于此，UI 各处调用无需再写 //#if 双分支。
     */
    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        //#if MC_VERSION >= 11903
        return Button.builder(label, onPress).bounds(x, y, width, height).build();
        //#else
        //$$ return new Button(x, y, width, height, label, onPress);
        //#endif
    }
}
