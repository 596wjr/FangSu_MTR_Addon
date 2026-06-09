package com.fangsu.mappings;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Component 鍏煎宸ュ叿绫伙紝缁熶竴 1.18.2 锟?1.20.1 锟?Component.translatable API锟?
 * <p>
 * 1.19+ 寮€锟?Component 鎺ュ彛鏈夐潤鎬佹柟锟?translatable()锟?
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

    /** 等效于 Component.empty() — 1.18.2 不存在该静态方法 */
    public static MutableComponent empty() {
        //#if MC_VERSION >= 11900
        return Component.empty();
        //#else
        //$$ return new net.minecraft.network.chat.TextComponent("");
        //#endif
    }

    /** 等效于 Component.literal(str) — 1.18.2 不存在该静态方法 */
    public static MutableComponent literal(String str) {
        //#if MC_VERSION >= 11900
        return Component.literal(str);
        //#else
        //$$ return new net.minecraft.network.chat.TextComponent(str);
        //#endif
    }
}
