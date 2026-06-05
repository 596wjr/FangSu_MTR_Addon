package com.fangsu.train;

import com.fangsu.Main;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class LcdManager {
    private static final ResourceLocation LCD_LOCATION = new ResourceLocation("fangsu:lcds.json");

    private static final LcdManager INSTANCE = new LcdManager();

    private final Map<String, Supplier<LcdBase>> loadedLcds;

    private LcdManager() {
        loadedLcds = new HashMap<>();
    }

    public static LcdManager getInstance() {
        return INSTANCE;
    }

    public void load() {

    }

    public void injectLcd(String key, Supplier<LcdBase> supplier) {
        Main.LOGGER.info("Loading LCD: {}", key);
        loadedLcds.put(key, supplier);
    }

    public LcdBase getLcd(String key) {
        Supplier<LcdBase> supplier = loadedLcds.get(key);
        return supplier != null ? supplier.get() : null;
    }
}
