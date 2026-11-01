package com.fangsu.shape;

import com.fangsu.config.FangSuConfig;
import net.minecraft.world.level.Level;

/** 一次形状查询的设置快照；后台任务不读取方块实体、世界或线程局部变量。 */
public final class CollisionShapeContext {
    public record Settings(Level level, long pos, boolean quality, double step, String revision, int delayMillis) { }
    private static final ThreadLocal<Settings> CURRENT = new ThreadLocal<>();

    private CollisionShapeContext() { }

    public static Settings current(Level level, long pos) {
        Settings settings = CURRENT.get();
        return settings != null ? settings : new Settings(level, pos,
                FangSuConfig.highQualityShape(), FangSuConfig.shapeStep(), "", 300);
    }

    public static <T> T with(Settings settings, java.util.function.Supplier<T> action) {
        Settings previous = CURRENT.get();
        CURRENT.set(settings);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
