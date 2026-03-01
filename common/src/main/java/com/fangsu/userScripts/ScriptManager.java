package com.fangsu.userScripts;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ScriptManager {
    private static final ScriptManager instance = new ScriptManager();
    private Map<ResourceLocation, ScriptHolderBase> holders;

    private ScriptManager() {
        holders = new HashMap<>();
    }

    public static ScriptManager getInstance() {
        return instance;
    }

    public ScriptHolderBase getHolder(ResourceLocation id) {
        return holders.getOrDefault(id, null);
    }

    public void initHolder(ResourceLocation pos, Supplier<? extends ScriptHolderBase> holderSupplier) throws Exception {
        if (holders.containsKey(pos)) return;
        ScriptHolderBase holder = holderSupplier.get();
        holder.loadScript(pos);
        holders.put(pos, holder);
    }
}
