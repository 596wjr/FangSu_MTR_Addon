package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackDependencyScreen extends Screen {
    private static final ResourceLocation PACK_META_DATA_LOCATION = new ResourceLocation("fangsu:fangsu_pack.json");

    private final List<dependencyInfo> dependencies;
    private final Map<String, Long> loadedPackVersions;

    protected PackDependencyScreen(Component component) {
        super(component);
        this.dependencies = new ArrayList<>();
        this.loadedPackVersions = new HashMap<>();
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        //#else
        //$$@Override
        //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$    renderBackground(poseStack);
        //#endif

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        List<Resource> resources;
        try {
            //#if MC_VERSION >= 11900
            resources = manager.getResourceStack(PACK_META_DATA_LOCATION);
            //#else
            //$$ resources = java.util.Collections.singletonList(manager.getResource(PACK_META_DATA_LOCATION));
            //#endif
        } catch (Exception e) {
            Main.LOGGER.error("Failed to load pack meta data", e);
            return;
        }

        List<JsonElement> jsons = new ArrayList<>();
        for (Resource resource : resources) {
            //#if MC_VERSION >= 11900
            try (InputStream stream = resource.open()) {
                //#else
                //$$ try (InputStream stream = resource.getInputStream()) {
                //#endif
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                jsons.add(Main.GSON.fromJson(reader, JsonElement.class));
            } catch (IOException e) {
                Main.LOGGER.error("Failed to read FangSu pack meta-data json:", e);
            }
        }

    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        super.init();


    }

    private record dependencyInfo() {
    }
}
